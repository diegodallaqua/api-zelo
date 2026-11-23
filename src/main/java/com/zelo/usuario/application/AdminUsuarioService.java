package com.zelo.usuario.application;

import com.zelo.compartilhado.auditoria.AuditoriaService;
import com.zelo.compartilhado.erro.ConflitoException;
import com.zelo.compartilhado.erro.RecursoNaoEncontradoException;
import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Ator;
import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.Usuario;
import com.zelo.usuario.infra.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Gestão de usuários pelo administrador (RF21). Contas da equipe não têm restrição de domínio. */
@Service
@RequiredArgsConstructor
public class AdminUsuarioService {

    private final UsuarioRepository usuarios;
    private final AutenticacaoService autenticacao;
    private final EnderecoService enderecoService;
    private final AuditoriaService auditoria;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<Usuario> pesquisar(Perfil perfil, Integer cidadeId, String termo, boolean incluirExcluidos,
                                   Pageable pageable) {
        String padrao = termo == null || termo.isBlank()
                ? null
                : "%" + termo.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        return usuarios.pesquisar(perfil, cidadeId, padrao, incluirExcluidos, pageable);
    }

    @Transactional(readOnly = true)
    public Usuario obter(UUID id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("USUARIO_NAO_ENCONTRADO",
                        "Usuário não encontrado: " + id + "."));
    }

    @Transactional
    public Usuario criarMembroDaEquipe(String nome, String email, String senha, Perfil perfil, Endereco.Dados endereco,
                                       Ator ator) {
        String emailNormalizado = AutenticacaoService.normalizarEmail(email);
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new ConflitoException("EMAIL_JA_CADASTRADO", "Já existe uma conta com este e-mail.");
        }
        Usuario usuario = usuarios.save(Usuario.novoMembroDaEquipe(nome.trim(), emailNormalizado,
                autenticacao.hashDeSenha(senha), perfil, enderecoService.novo(endereco), clock.instant()));
        auditoria.registrar(ator, "MEMBRO_DA_EQUIPE_CRIADO", "USUARIO", usuario.getId(),
                Map.of("perfil", perfil.name(), "cidadeId", usuario.cidadeId()));
        return usuario;
    }

    @Transactional
    public Usuario alterarPerfil(UUID id, Perfil perfil, Ator ator) {
        Usuario usuario = ativo(id);
        if (usuario.getPerfil() == Perfil.ADMIN && perfil != Perfil.ADMIN
                && usuarios.countByPerfilAndExcluidoEmIsNull(Perfil.ADMIN) <= 1) {
            throw new RegraDeNegocioException("ULTIMO_ADMINISTRADOR",
                    "Não é possível remover o perfil do único administrador.");
        }
        Perfil anterior = usuario.getPerfil();
        usuario.definirPerfil(perfil, clock.instant());
        auditoria.registrar(ator, "PERFIL_ALTERADO", "USUARIO", id, Map.of("de", anterior.name(), "para", perfil.name()));
        return usuario;
    }

    /** Altera o endereço; para moderadores e gestores, muda também a cidade de atuação. */
    @Transactional
    public Usuario alterarEndereco(UUID id, Endereco.Dados endereco, Ator ator) {
        Usuario usuario = ativo(id);
        Integer cidadeAnterior = usuario.cidadeId();
        enderecoService.mudar(usuario, endereco);
        auditoria.registrar(ator, "ENDERECO_ALTERADO", "USUARIO", id,
                Map.of("cidadeAnterior", String.valueOf(cidadeAnterior), "cidadeNova", endereco.cidadeId()));
        return usuario;
    }

    private Usuario ativo(UUID id) {
        Usuario usuario = obter(id);
        if (usuario.estaExcluido()) {
            throw new RegraDeNegocioException("USUARIO_EXCLUIDO", "Não é possível alterar uma conta excluída.");
        }
        return usuario;
    }
}
