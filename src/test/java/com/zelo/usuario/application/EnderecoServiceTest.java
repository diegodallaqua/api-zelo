package com.zelo.usuario.application;

import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.erro.RequisicaoInvalidaException;
import com.zelo.localidade.application.CidadeService;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.EnderecoRepository;
import com.zelo.usuario.infra.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnderecoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");
    private static final int SAO_PAULO = 3550308;
    private static final int RIO = 3304557;

    @Mock EnderecoRepository enderecos;
    @Mock UsuarioRepository usuarios;
    @Mock CidadeService cidadeService;

    private EnderecoService service;

    @BeforeEach
    void configurar() {
        service = new EnderecoService(enderecos, usuarios, cidadeService, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void novoEnderecoValidoComCepNormalizado() {
        Endereco e = service.novo(dados("01001-000", SAO_PAULO));

        assertThat(e.getCep()).isEqualTo("01001000");
        assertThat(e.getCidadeId()).isEqualTo(SAO_PAULO);
        assertThat(e.getCriadoEm()).isEqualTo(AGORA);
        verify(cidadeService).exigirExistente(SAO_PAULO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "0100100", "010010000", "abcdefgh"})
    void cepPrecisaTerOitoDigitos(String cep) {
        assertThatThrownBy(() -> service.novo(dados(cep, SAO_PAULO)))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasFieldOrPropertyWithValue("codigo", "CEP_INVALIDO");
    }

    @Test
    void ruaBairroENumeroSaoObrigatorios() {
        Endereco.Dados semNumero = new Endereco.Dados("01001000", "Praça da Sé", "Sé", "  ", null, SAO_PAULO);

        assertThatThrownBy(() -> service.novo(semNumero))
                .hasFieldOrPropertyWithValue("codigo", "ENDERECO_INCOMPLETO");
    }

    @Test
    void cidadePrecisaExistir() {
        doThrow(new RegraDeNegocioException("CIDADE_INVALIDA", "Cidade não encontrada")).when(cidadeService)
                .exigirExistente(9999999);

        assertThatThrownBy(() -> service.novo(dados("01001000", 9999999)))
                .hasFieldOrPropertyWithValue("codigo", "CIDADE_INVALIDA");
    }

    @Test
    void enderecoExclusivoEhAtualizadoNoMesmoRegistro() {
        Usuario ana = cidadaoComEndereco();
        Endereco original = ana.getEndereco();
        when(usuarios.countByEnderecoId(original.getId())).thenReturn(1L);

        service.mudar(ana, dados("20010000", RIO));

        assertThat(ana.getEndereco()).isSameAs(original);
        assertThat(original.getCidadeId()).isEqualTo(RIO);
    }

    @Test
    void enderecoCompartilhadoGeraUmNovoRegistro() {
        Usuario ana = cidadaoComEndereco();
        Endereco compartilhado = ana.getEndereco();
        when(usuarios.countByEnderecoId(compartilhado.getId())).thenReturn(2L);

        service.mudar(ana, dados("20010000", RIO));

        assertThat(ana.getEndereco()).isNotSameAs(compartilhado);
        assertThat(ana.cidadeId()).isEqualTo(RIO);
        assertThat(compartilhado.getCidadeId()).as("o outro morador continua no endereço antigo").isEqualTo(SAO_PAULO);
    }

    @Test
    void enderecoInvalidoNaoAlteraNada() {
        Usuario ana = cidadaoComEndereco();

        assertThatThrownBy(() -> service.mudar(ana, dados("1", RIO))).hasFieldOrPropertyWithValue("codigo", "CEP_INVALIDO");
        assertThat(ana.cidadeId()).isEqualTo(SAO_PAULO);
    }

    @Test
    void removeEnderecoSoQuandoNinguemMaisUsa() {
        UUID orfao = UUID.randomUUID();
        UUID emUso = UUID.randomUUID();
        when(usuarios.countByEnderecoId(orfao)).thenReturn(0L);
        when(usuarios.countByEnderecoId(emUso)).thenReturn(1L);

        service.removerSeOrfao(orfao);
        service.removerSeOrfao(emUso);
        service.removerSeOrfao(null);

        verify(enderecos).deleteById(orfao);
        verify(enderecos, never()).deleteById(emUso);
        verify(usuarios, never()).countByEnderecoId(null);
    }

    @Test
    void visaoDeEnderecoNuloEhNula() {
        assertThat(service.visao(null)).isNull();
        verify(cidadeService, never()).resumo(any());
    }

    private static Endereco.Dados dados(String cep, int cidade) {
        return new Endereco.Dados(cep, "Rua", "Bairro", "10", null, cidade);
    }

    private static Usuario cidadaoComEndereco() {
        Endereco endereco = new Endereco(dados("01001000", SAO_PAULO), AGORA);
        ReflectionTestUtils.setField(endereco, "id", UUID.randomUUID());
        return Usuario.novoCidadao("Ana", "ana@gmail.com", "hash", endereco, AGORA);
    }
}
