package com.zelo.ocorrencia.application;

import com.zelo.categoria.application.CategoriaService;
import com.zelo.compartilhado.erro.LimiteExcedidoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.localidade.application.CidadeService;
import com.zelo.ocorrencia.application.Visoes.FotoVisao;
import com.zelo.ocorrencia.domain.ArmazenamentoDeImagens;
import com.zelo.ocorrencia.domain.CalculadoraDePrioridade;
import com.zelo.ocorrencia.domain.DetectorDeDuplicidade;
import com.zelo.ocorrencia.domain.DetectorDeDuplicidade.Semelhante;
import com.zelo.ocorrencia.domain.FotoOcorrencia;
import com.zelo.ocorrencia.domain.Ocorrencia;
import com.zelo.ocorrencia.domain.OcorrenciasSemelhantesException;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import com.zelo.ocorrencia.infra.ProcessadorDeFotos;
import com.zelo.ocorrencia.infra.ProcessadorDeFotos.FotoProcessada;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ações do autor sobre a própria ocorrência: registrar (RF08-RF10), editar, excluir e gerenciar fotos (RF12).
 * O envio das fotos ao armazenamento acontece fora da transação; se a gravação no banco falhar, os
 * arquivos enviados são removidos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistroDeOcorrenciaService {

    private final OcorrenciaRepository repository;
    private final DetectorDeDuplicidade detectorDeDuplicidade;
    private final CalculadoraDePrioridade calculadoraDePrioridade;
    private final ArmazenamentoDeImagens armazenamento;
    private final ProcessadorDeFotos processadorDeFotos;
    private final ProvedorDeAtor provedorDeAtor;
    private final CidadeService cidadeService;
    private final CategoriaService categoriaService;
    private final MontadorDeVisoes visoes;
    private final PublicadorDeEventos eventos;
    private final OcorrenciaProperties properties;
    private final TransactionTemplate transacao;
    private final Clock clock;

    public record NovaOcorrencia(UUID categoriaId, String descricao, double latitude, double longitude,
                                 double latitudeDispositivo, double longitudeDispositivo, boolean anonima,
                                 boolean ignorarSemelhantes) {
    }

    public UUID registrar(NovaOcorrencia dados, List<MultipartFile> fotos, UUID usuarioId) {
        Ator ator = provedorDeAtor.obterParticipante(usuarioId);
        List<MultipartFile> arquivos = fotos == null ? List.of() : fotos.stream().filter(f -> f != null).toList();
        exigirQuantidadeDeFotos(arquivos.size());
        if (!ator.isEquipe()) {
            exigirLimiteDiario(usuarioId);
        }
        Integer cidadeId = cidadeService.validarLocalDaOcorrencia(dados.latitude(), dados.longitude(),
                dados.latitudeDispositivo(), dados.longitudeDispositivo());
        categoriaService.exigirDisponivel(dados.categoriaId());
        if (!dados.ignorarSemelhantes()) {
            List<Semelhante> semelhantes = detectorDeDuplicidade.buscar(dados.latitude(), dados.longitude(),
                    dados.categoriaId());
            if (!semelhantes.isEmpty()) {
                throw new OcorrenciasSemelhantesException(semelhantes);
            }
        }
        List<FotoProcessada> processadas = arquivos.stream().map(processadorDeFotos::processar).toList();

        Instant agora = clock.instant();
        Ocorrencia ocorrencia = Ocorrencia.registrar(usuarioId, dados.categoriaId(), cidadeId, dados.descricao(),
                dados.latitude(), dados.longitude(), dados.anonima(), agora);
        ocorrencia.definirPrioridadeInicial(calculadoraDePrioridade.calcular(0, agora, agora));
        List<String> enviadas = new ArrayList<>();
        try {
            for (FotoProcessada foto : processadas) {
                UUID fotoId = UUID.randomUUID();
                String chave = chaveDaFoto(ocorrencia.getId(), fotoId);
                armazenamento.salvar(chave, foto.conteudo(), foto.contentType());
                enviadas.add(chave);
                ocorrencia.adicionarFoto(fotoId, chave, foto.contentType(), foto.conteudo().length, foto.largura(),
                        foto.altura(), properties.fotos().maximo(), agora);
            }
            transacao.executeWithoutResult(s -> {
                repository.save(ocorrencia);
                eventos.publicar(ocorrencia);
            });
            return ocorrencia.getId();
        } catch (RuntimeException e) {
            enviadas.forEach(armazenamento::remover);
            throw e;
        }
    }

    public void editar(UUID ocorrenciaId, UUID usuarioId, String descricao, UUID categoriaId) {
        provedorDeAtor.obterParticipante(usuarioId);
        transacao.executeWithoutResult(s -> {
            Ocorrencia o = carregar(ocorrenciaId);
            if (categoriaId != null && !categoriaId.equals(o.getCategoriaId())) {
                categoriaService.exigirDisponivel(categoriaId);
            }
            o.editar(usuarioId, descricao, categoriaId, clock.instant());
            eventos.publicar(o);
        });
    }

    /** Exclusão lógica pelo autor; as fotos saem do armazenamento (minimização de dados). */
    public void excluir(UUID ocorrenciaId, UUID usuarioId) {
        provedorDeAtor.obter(usuarioId);
        List<String> chaves = transacao.execute(s -> {
            Ocorrencia o = carregar(ocorrenciaId);
            o.excluirPeloAutor(usuarioId, clock.instant());
            eventos.publicar(o);
            return o.getFotos().stream().map(FotoOcorrencia::getChave).toList();
        });
        if (chaves != null) {
            chaves.forEach(armazenamento::remover);
        }
    }

    public FotoVisao adicionarFoto(UUID ocorrenciaId, MultipartFile arquivo, UUID usuarioId) {
        provedorDeAtor.obterParticipante(usuarioId);
        int maximo = properties.fotos().maximo();
        transacao.executeWithoutResult(s -> carregar(ocorrenciaId).exigirPodeIncluirFoto(usuarioId, maximo));
        FotoProcessada foto = processadorDeFotos.processar(arquivo);
        UUID fotoId = UUID.randomUUID();
        String chave = chaveDaFoto(ocorrenciaId, fotoId);
        armazenamento.salvar(chave, foto.conteudo(), foto.contentType());
        try {
            return transacao.execute(s -> {
                Ocorrencia o = carregar(ocorrenciaId);
                o.exigirPodeIncluirFoto(usuarioId, maximo);
                FotoOcorrencia nova = o.adicionarFoto(fotoId, chave, foto.contentType(), foto.conteudo().length,
                        foto.largura(), foto.altura(), maximo, clock.instant());
                eventos.publicar(o);
                return visoes.foto(nova);
            });
        } catch (RuntimeException e) {
            armazenamento.remover(chave);
            throw e;
        }
    }

    public void removerFoto(UUID ocorrenciaId, UUID fotoId, UUID usuarioId) {
        provedorDeAtor.obter(usuarioId);
        String chave = transacao.execute(s -> {
            Ocorrencia o = carregar(ocorrenciaId);
            FotoOcorrencia removida = o.removerFoto(usuarioId, fotoId, properties.fotos().minimo(), clock.instant());
            eventos.publicar(o);
            return removida.getChave();
        });
        if (chave != null) {
            armazenamento.remover(chave);
        }
    }

    private void exigirQuantidadeDeFotos(int quantidade) {
        OcorrenciaProperties.Fotos fotos = properties.fotos();
        if (quantidade < fotos.minimo()) {
            throw new RegraDeNegocioException("FOTO_OBRIGATORIA", "Envie ao menos " + fotos.minimo() + " foto(s) do problema.");
        }
        if (quantidade > fotos.maximo()) {
            throw new RegraDeNegocioException("LIMITE_DE_FOTOS", "Envie no máximo " + fotos.maximo() + " foto(s).");
        }
    }

    /** Regra 3.2: limite de ocorrências por usuário no dia civil do fuso configurado. */
    private void exigirLimiteDiario(UUID usuarioId) {
        ZoneId fuso = properties.fusoHorario();
        Instant inicioDoDia = LocalDate.now(clock.withZone(fuso)).atStartOfDay(fuso).toInstant();
        long hoje = repository.countByAutorIdAndCriadaEmGreaterThanEqual(usuarioId, inicioDoDia);
        if (hoje >= properties.limiteDiarioPorUsuario()) {
            throw new LimiteExcedidoException("LIMITE_DIARIO_DE_OCORRENCIAS",
                    "Você atingiu o limite de " + properties.limiteDiarioPorUsuario()
                            + " ocorrências por dia. Tente novamente amanhã.");
        }
    }

    private Ocorrencia carregar(UUID id) {
        Ocorrencia o = repository.findById(id).orElseThrow(RegistroDeOcorrenciaService::naoEncontrada);
        o.exigirNaoRemovida();
        return o;
    }

    static RecursoNaoEncontradoException naoEncontrada() {
        return new RecursoNaoEncontradoException("OCORRENCIA_NAO_ENCONTRADA", "Ocorrência não encontrada.");
    }

    private static String chaveDaFoto(UUID ocorrenciaId, UUID fotoId) {
        return "ocorrencias/" + ocorrenciaId + "/" + fotoId + ".jpg";
    }
}
