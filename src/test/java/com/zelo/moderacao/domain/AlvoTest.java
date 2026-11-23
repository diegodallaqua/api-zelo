package com.zelo.moderacao.domain;

import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exatamente uma chave (ocorrência OU comentário) em denúncias e decisões. */
class AlvoTest {

    @Test
    void ocorrencia() {
        UUID id = UUID.randomUUID();
        Alvo alvo = Alvo.de(id, null);
        assertThat(alvo.tipo()).isEqualTo(TipoConteudo.OCORRENCIA);
        assertThat(alvo.id()).isEqualTo(id);
        assertThat(alvo.comentarioId()).isNull();
    }

    @Test
    void comentario() {
        UUID id = UUID.randomUUID();
        Alvo alvo = Alvo.de(null, id);
        assertThat(alvo.tipo()).isEqualTo(TipoConteudo.COMENTARIO);
        assertThat(alvo.ocorrenciaId()).isNull();
    }

    @Test
    void recusaOsDois() {
        assertThatThrownBy(() -> Alvo.de(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .extracting("codigo").isEqualTo("ALVO_INVALIDO");
    }

    @Test
    void recusaNenhum() {
        assertThatThrownBy(() -> Alvo.de(null, null))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .extracting("codigo").isEqualTo("ALVO_INVALIDO");
    }

    @Test
    void tipoMapeiaParaIdsDaTabela() {
        assertThat(TipoConteudo.OCORRENCIA.id()).isEqualTo(1);
        assertThat(TipoConteudo.deId(2)).isEqualTo(TipoConteudo.COMENTARIO);
    }

    @Test
    void decisaoDeSuspensaoNaoAceitaConteudo() {
        assertThatThrownBy(() -> DecisaoModeracao.builder().acao(DecisaoModeracao.Acao.SUSPENDER_USUARIO)
                .alvo(Alvo.ocorrencia(UUID.randomUUID())).motivo("x").decididaEm(Instant.now()).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecisaoModeracao.builder().acao(DecisaoModeracao.Acao.MANTER)
                .motivo("x").decididaEm(Instant.now()).build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
