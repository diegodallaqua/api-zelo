package com.zelo.ocorrencia.application;

import com.zelo.categoria.application.CategoriaService;
import com.zelo.compartilhado.erro.LimiteExcedidoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.compartilhado.seguranca.ProvedorDeAtor;
import com.zelo.localidade.application.CidadeService;
import com.zelo.ocorrencia.application.RegistroDeOcorrenciaService.NovaOcorrencia;
import com.zelo.ocorrencia.domain.ArmazenamentoDeImagens;
import com.zelo.ocorrencia.domain.CalculadoraDePrioridade;
import com.zelo.ocorrencia.domain.DetectorDeDuplicidade;
import com.zelo.ocorrencia.domain.DetectorDeDuplicidade.Semelhante;
import com.zelo.ocorrencia.domain.OcorrenciasSemelhantesException;
import com.zelo.ocorrencia.domain.PrioridadePorCurtidasEDiasEmAberto;
import com.zelo.ocorrencia.domain.StatusOcorrencia;
import com.zelo.ocorrencia.infra.OcorrenciaRepository;
import com.zelo.ocorrencia.infra.ProcessadorDeFotos;
import com.zelo.ocorrencia.infra.ProcessadorDeFotos.FotoProcessada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroDeOcorrenciaServiceTest {

    private static final Integer CIDADE = 3550308;
    // 02:30 UTC = 23:30 do dia anterior em São Paulo: o "dia civil" começa em 2026-11-22T03:00Z.
    private static final Instant AGORA = Instant.parse("2026-11-23T02:30:00Z");

    @Mock OcorrenciaRepository repository;
    @Mock DetectorDeDuplicidade detector;
    @Mock ArmazenamentoDeImagens armazenamento;
    @Mock ProcessadorDeFotos processador;
    @Mock ProvedorDeAtor provedorDeAtor;
    @Mock CidadeService cidadeService;
    @Mock CategoriaService categoriaService;
    @Mock MontadorDeVisoes visoes;
    @Mock PublicadorDeEventos eventos;
    @Mock TransactionTemplate transacao;

    private RegistroDeOcorrenciaService service;
    private final UUID usuario = UUID.randomUUID();
    private final UUID categoria = UUID.randomUUID();

    @BeforeEach
    void configurar() {
        CalculadoraDePrioridade calculadora = new PrioridadePorCurtidasEDiasEmAberto();
        OcorrenciaProperties properties = new OcorrenciaProperties(ZoneId.of("America/Sao_Paulo"), 5, 25,
                Duration.ofDays(15), 90, 30, 5, new OcorrenciaProperties.Mapa(500, 1000),
                new OcorrenciaProperties.Fotos(1, 5, DataSize.ofMegabytes(10), 1920, 0.8f, 50));
        service = new RegistroDeOcorrenciaService(repository, detector, calculadora, armazenamento, processador,
                provedorDeAtor, cidadeService, categoriaService, visoes, eventos, properties, transacao,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        lenient().when(provedorDeAtor.obterParticipante(usuario)).thenReturn(Ator.cidadao(usuario));
        lenient().when(cidadeService.validarLocalDaOcorrencia(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(CIDADE);
        lenient().doAnswer(inv -> {
            inv.<Consumer<Object>>getArgument(0).accept(new SimpleTransactionStatus());
            return null;
        }).when(transacao).executeWithoutResult(any());
        lenient().when(processador.processar(any())).thenReturn(new FotoProcessada(new byte[]{1, 2, 3}, "image/jpeg", 10, 10));
    }

    @Test
    void registraComFotos() {
        UUID id = service.registrar(dados(false), List.of(foto()), usuario);

        assertThat(id).isNotNull();
        ArgumentCaptor<String> chave = ArgumentCaptor.forClass(String.class);
        verify(armazenamento).salvar(chave.capture(), any(), eq("image/jpeg"));
        assertThat(chave.getValue()).startsWith("ocorrencias/" + id + "/").endsWith(".jpg");
        verify(repository).save(any());
    }

    @Test
    void limiteDiarioContaODiaCivilDeSaoPaulo() {
        when(repository.countByAutorIdAndCriadaEmGreaterThanEqual(usuario, Instant.parse("2026-11-22T03:00:00Z")))
                .thenReturn(5L);

        assertThatThrownBy(() -> service.registrar(dados(false), List.of(foto()), usuario))
                .isInstanceOf(LimiteExcedidoException.class)
                .extracting("codigo").isEqualTo("LIMITE_DIARIO_DE_OCORRENCIAS");
        verify(armazenamento, never()).salvar(anyString(), any(), anyString());
    }

    @Test
    void equipeNaoTemLimiteDiario() {
        when(provedorDeAtor.obterParticipante(usuario)).thenReturn(new Ator(usuario, Perfil.GESTOR, CIDADE));
        service.registrar(dados(false), List.of(foto()), usuario);
        verify(repository, never()).countByAutorIdAndCriadaEmGreaterThanEqual(any(), any());
    }

    @Test
    void exigeAoMenosUmaFoto() {
        assertThatThrownBy(() -> service.registrar(dados(false), List.of(), usuario))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting("codigo").isEqualTo("FOTO_OBRIGATORIA");
    }

    @Test
    void limitaQuantidadeDeFotos() {
        List<MultipartFile> seis = List.of(foto(), foto(), foto(), foto(), foto(), foto());
        assertThatThrownBy(() -> service.registrar(dados(false), seis, usuario))
                .isInstanceOf(RegraDeNegocioException.class)
                .extracting("codigo").isEqualTo("LIMITE_DE_FOTOS");
    }

    @Test
    void sugereSemelhantesAntesDeCriar() {
        when(detector.buscar(anyDouble(), anyDouble(), eq(categoria))).thenReturn(List.of(new Semelhante(UUID.randomUUID(),
                -23.5, -46.6, 12.3, StatusOcorrencia.ABERTA, 4, "Buraco", AGORA)));

        assertThatThrownBy(() -> service.registrar(dados(false), List.of(foto()), usuario))
                .isInstanceOf(OcorrenciasSemelhantesException.class)
                .satisfies(e -> assertThat(((OcorrenciasSemelhantesException) e).getPropriedades()).containsKey("semelhantes"));
        verify(armazenamento, never()).salvar(anyString(), any(), anyString());
    }

    @Test
    void ignorarSemelhantesPulaADeteccao() {
        service.registrar(dados(true), List.of(foto()), usuario);
        verify(detector, never()).buscar(anyDouble(), anyDouble(), any());
    }

    @Test
    void removeFotosEnviadasSeOBancoFalhar() {
        doThrow(new IllegalStateException("banco fora")).when(transacao).executeWithoutResult(any());

        assertThatThrownBy(() -> service.registrar(dados(true), List.of(foto(), foto()), usuario))
                .isInstanceOf(IllegalStateException.class);
        verify(armazenamento, times(2)).remover(anyString());
    }

    private NovaOcorrencia dados(boolean ignorarSemelhantes) {
        return new NovaOcorrencia(categoria, "Buraco grande na esquina", -23.5, -46.6, -23.5, -46.6, false,
                ignorarSemelhantes);
    }

    private static MockMultipartFile foto() {
        return new MockMultipartFile("fotos", "f.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }
}
