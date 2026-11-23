package com.zelo.usuario.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.AcessoNegadoException;
import com.zelo.compartilhado.erro.NaoAutenticadoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.domain.Consentimento;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.EventosDeUsuario.ContaExcluida;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.ConsentimentoRepository;
import com.zelo.usuario.infra.RefreshTokenRepository;
import com.zelo.usuario.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Operações do titular sobre a própria conta, inclusive endereço e exclusão (LGPD). */
@Service
@RequiredArgsConstructor
public class ContaService {

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final ConsentimentoRepository consentimentos;
    private final AutenticacaoService autenticacao;
    private final EnderecoService enderecoService;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioProperties properties;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Usuario obter(UUID id) {
        return usuarios.findById(id).filter(u -> !u.estaExcluido())
                .orElseThrow(() -> new NaoAutenticadoException("CONTA_INDISPONIVEL",
                        "Sua conta não existe mais ou foi excluída."));
    }

    @Transactional
    public Usuario alterarNome(UUID id, String nome) {
        Usuario usuario = obter(id);
        usuario.alterarNome(nome.trim(), clock.instant());
        return usuario;
    }

    /**
     * O titular altera o próprio endereço. Para moderadores e gestores, a cidade do endereço define a área
     * de atuação, então só o administrador pode alterá-lo.
     */
    @Transactional
    public Usuario alterarEndereco(UUID id, Endereco.Dados dados) {
        Usuario usuario = obter(id);
        if (usuario.getPerfil().atuaPorCidade()) {
            throw new AcessoNegadoException("ENDERECO_DEFINE_ATUACAO",
                    "O endereço de moderadores e gestores define a área de atuação e só pode ser alterado pelo administrador.");
        }
        enderecoService.mudar(usuario, dados);
        return usuario;
    }

    /** Troca a senha e encerra todas as sessões abertas. */
    @Transactional
    public void alterarSenha(UUID id, String senhaAtual, String novaSenha) {
        Usuario usuario = obter(id);
        exigirSenhaCorreta(usuario, senhaAtual);
        Instant agora = clock.instant();
        usuario.alterarSenha(autenticacao.hashDeSenha(novaSenha), agora);
        refreshTokens.revogarTodos(id, agora);
    }

    /**
     * Exclui a conta: os módulos anonimizam ou apagam os dados ligados ao usuário (evento síncrono),
     * a linha do usuário perde todos os dados pessoais e o endereço é apagado se ninguém mais o usa.
     */
    @Transactional
    public void excluirConta(UUID id, String senha) {
        Usuario usuario = obter(id);
        exigirSenhaCorreta(usuario, senha);
        if (usuario.getPerfil() == Perfil.ADMIN && usuarios.countByPerfilAndExcluidoEmIsNull(Perfil.ADMIN) <= 1) {
            throw new RegraDeNegocioException("ULTIMO_ADMINISTRADOR",
                    "Não é possível excluir a conta do único administrador. Crie outro administrador antes.");
        }
        UUID enderecoId = usuario.getEndereco() == null ? null : usuario.getEndereco().getId();
        eventos.publishEvent(new ContaExcluida(id));
        refreshTokens.excluirTodos(id);
        usuario.anonimizar(clock.instant());
        usuarios.flush();
        enderecoService.removerSeOrfao(enderecoId);
        auditoria.registrarPeloSistema("CONTA_EXCLUIDA_PELO_TITULAR", "USUARIO", id, Map.of());
    }

    @Transactional(readOnly = true)
    public List<Consentimento> listarConsentimentos(UUID id) {
        return consentimentos.findByUsuarioIdOrderByRegistradoEmDesc(id);
    }

    @Transactional
    public Consentimento registrarConsentimento(UUID id, Consentimento.Tipo tipo, boolean aceito) {
        obter(id);
        UsuarioProperties.Cadastro cadastro = properties.cadastro();
        String versao = switch (tipo) {
            case TERMOS_DE_USO -> cadastro.versaoTermosDeUso();
            case POLITICA_DE_PRIVACIDADE -> cadastro.versaoPoliticaDePrivacidade();
            case LOCALIZACAO -> cadastro.versaoConsentimentoLocalizacao();
        };
        return consentimentos.save(new Consentimento(id, tipo, versao, aceito, clock.instant()));
    }

    private void exigirSenhaCorreta(Usuario usuario, String senha) {
        if (usuario.getSenhaHash() == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new RegraDeNegocioException("SENHA_INCORRETA", "A senha informada está incorreta.");
        }
    }
}
