package com.zelo.usuario.application;

import com.zelo.compartilhado.erro.ErroDeNegocioException;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Cria o primeiro administrador a partir de ZELO_ADMIN_* se ainda não houver nenhum. Roda depois da
 * importação de cidades ({@code @Order(10)}), porque o endereço precisa de uma cidade existente.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
class CriacaoDoAdministradorInicial implements ApplicationRunner {

    private final UsuarioProperties properties;
    private final UsuarioRepository usuarios;
    private final AutenticacaoService autenticacao;
    private final EnderecoService enderecoService;
    private final TransactionTemplate transacao;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        UsuarioProperties.AdminInicial admin = properties.adminInicial();
        if (admin == null || vazio(admin.email()) || vazio(admin.senha())) {
            return;
        }
        UsuarioProperties.EnderecoInicial e = admin.endereco();
        if (e == null || e.cidadeId() == null) {
            log.warn("Administrador inicial não criado: defina o endereço (ZELO_ADMIN_CEP, _RUA, _BAIRRO, _NUMERO, _CIDADE_ID).");
            return;
        }
        try {
            transacao.executeWithoutResult(status -> {
                if (usuarios.countByPerfilAndExcluidoEmIsNull(Perfil.ADMIN) > 0) {
                    return;
                }
                String email = AutenticacaoService.normalizarEmail(admin.email());
                if (usuarios.existsByEmailIgnoreCase(email)) {
                    log.warn("Administrador inicial não criado: o e-mail {} já pertence a outra conta.", email);
                    return;
                }
                Endereco endereco = enderecoService.novo(new Endereco.Dados(e.cep(), e.rua(), e.bairro(), e.numero(),
                        e.complemento(), e.cidadeId()));
                usuarios.save(Usuario.novoMembroDaEquipe(admin.nome(), email, autenticacao.hashDeSenha(admin.senha()),
                        Perfil.ADMIN, endereco, clock.instant()));
                log.info("Administrador inicial criado: {}", email);
            });
        } catch (ErroDeNegocioException ex) {
            log.warn("Administrador inicial não criado: {}", ex.getMessage());
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
