package com.zelo.usuario.application;

import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import com.zelo.localidade.application.CidadeService;
import com.zelo.localidade.application.CidadeService.CidadeResumo;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.EnderecoRepository;
import com.zelo.usuario.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Validação e manutenção dos endereços dos usuários. */
@Service
@RequiredArgsConstructor
public class EnderecoService {

    private final EnderecoRepository enderecos;
    private final UsuarioRepository usuarios;
    private final CidadeService cidadeService;
    private final Clock clock;

    public record EnderecoVisao(String cep, String rua, String bairro, String numero, String complemento,
                                CidadeResumo cidade) {
    }

    @Transactional(readOnly = true)
    public Endereco novo(Endereco.Dados dados) {
        validar(dados);
        return new Endereco(dados, clock.instant());
    }

    /** Troca o endereço do usuário, sem alterar o de outros usuários que compartilham o mesmo registro. */
    @Transactional
    public void mudar(Usuario usuario, Endereco.Dados dados) {
        validar(dados);
        Endereco atual = usuario.getEndereco();
        boolean compartilhado = atual != null && usuarios.countByEnderecoId(atual.getId()) > 1;
        usuario.mudarEndereco(dados, compartilhado, clock.instant());
    }

    /** Apaga o endereço se nenhum usuário aponta mais para ele (ex.: após exclusão de conta). */
    @Transactional
    public void removerSeOrfao(UUID enderecoId) {
        if (enderecoId != null && usuarios.countByEnderecoId(enderecoId) == 0) {
            enderecos.deleteById(enderecoId);
        }
    }

    @Transactional(readOnly = true)
    public EnderecoVisao visao(Endereco e) {
        if (e == null) {
            return null;
        }
        return new EnderecoVisao(e.getCep(), e.getRua(), e.getBairro(), e.getNumero(), e.getComplemento(),
                cidadeService.resumo(e.getCidadeId()));
    }

    void validar(Endereco.Dados d) {
        if (d.cep() == null || !d.cep().matches("\\d{8}")) {
            throw new RequisicaoInvalidaException("CEP_INVALIDO", "O CEP deve ter 8 dígitos.");
        }
        if (vazio(d.rua()) || vazio(d.bairro()) || vazio(d.numero())) {
            throw new RequisicaoInvalidaException("ENDERECO_INCOMPLETO", "Informe rua, bairro e número do endereço.");
        }
        cidadeService.exigirExistente(d.cidadeId());
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
