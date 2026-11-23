package com.zelo.usuario.domain;

import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Perfil;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioTest {

    private static final int SAO_PAULO = 3550308;
    private static final int RIO = 3304557;

    private final Instant agora = Instant.parse("2026-11-23T12:00:00Z");

    private Endereco endereco(int cidade) {
        return new Endereco(new Endereco.Dados("01002-020", " Viaduto do Chá ", "Centro", "15", " ", cidade), agora);
    }

    @Test
    void enderecoNormalizaCepEComplemento() {
        Endereco e = endereco(SAO_PAULO);
        assertThat(e.getCep()).isEqualTo("01002020");
        assertThat(e.getRua()).isEqualTo("Viaduto do Chá");
        assertThat(e.getComplemento()).isNull();
    }

    @Test
    void cidadeDeAtuacaoVemDoEndereco() {
        Usuario gestor = Usuario.novoMembroDaEquipe("Gestora", "g@prefeitura.gov.br", "h", Perfil.GESTOR,
                endereco(SAO_PAULO), agora);
        assertThat(gestor.cidadeId()).isEqualTo(SAO_PAULO);

        gestor.mudarEndereco(new Endereco.Dados("20010000", "Rua Primeiro de Março", "Centro", "1", null, RIO), false, agora);
        assertThat(gestor.cidadeId()).isEqualTo(RIO);
    }

    @Test
    void enderecoCompartilhadoNaoEAlteradoParaOsOutros() {
        Endereco compartilhado = endereco(SAO_PAULO);
        Usuario ana = Usuario.novoCidadao("Ana", "ana@gmail.com", "h", compartilhado, agora);
        Usuario bia = Usuario.novoCidadao("Bia", "bia@gmail.com", "h", compartilhado, agora);

        ana.mudarEndereco(new Endereco.Dados("20010000", "Rua X", "Centro", "1", null, RIO), true, agora);

        assertThat(ana.getEndereco()).isNotSameAs(compartilhado);
        assertThat(bia.cidadeId()).isEqualTo(SAO_PAULO);
    }

    @Test
    void enderecoEObrigatorio() {
        assertThatThrownBy(() -> Usuario.novoCidadao("Ana", "ana@gmail.com", "h", null, agora))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equipeNaoCriaCidadao() {
        assertThatThrownBy(() -> Usuario.novoMembroDaEquipe("C", "c@gmail.com", "h", Perfil.CIDADAO,
                endereco(SAO_PAULO), agora))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void suspensaoTemporariaExpira() {
        Usuario u = Usuario.novoCidadao("Ana", "ana@gmail.com", "h", endereco(SAO_PAULO), agora);
        u.suspender(agora.plus(Duration.ofDays(7)), false, "Spam", agora);

        assertThat(u.estaSuspenso(agora.plus(Duration.ofDays(6)))).isTrue();
        assertThat(u.estaSuspenso(agora.plus(Duration.ofDays(8)))).isFalse();
    }

    @Test
    void suspensaoTemporariaExigeDataFutura() {
        Usuario u = Usuario.novoCidadao("Ana", "ana@gmail.com", "h", endereco(SAO_PAULO), agora);
        assertThatThrownBy(() -> u.suspender(agora.minusSeconds(1), false, "x", agora))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void suspensaoPermanente() {
        Usuario u = Usuario.novoCidadao("Ana", "ana@gmail.com", "h", endereco(SAO_PAULO), agora);
        u.suspender(null, true, "Reincidente", agora);
        assertThat(u.estaSuspenso(agora.plus(Duration.ofDays(3650)))).isTrue();

        u.reativar(agora);
        assertThat(u.estaSuspenso(agora)).isFalse();
    }

    @Test
    void anonimizacaoRemoveDadosPessoaisEEndereco() {
        Usuario u = Usuario.novoMembroDaEquipe("Gestora", "g@prefeitura.gov.br", "h", Perfil.GESTOR,
                endereco(SAO_PAULO), agora);
        u.anonimizar(agora);

        assertThat(u.getNome()).isEqualTo(Usuario.NOME_ANONIMIZADO);
        assertThat(u.getEmail()).endsWith("@zelo.invalid").doesNotContain("prefeitura");
        assertThat(u.getSenhaHash()).isNull();
        assertThat(u.getPerfil()).isEqualTo(Perfil.CIDADAO);
        assertThat(u.getEndereco()).isNull();
        assertThat(u.estaExcluido()).isTrue();
    }
}
