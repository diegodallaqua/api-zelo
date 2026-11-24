package com.zelo.usuario.domain;

import com.zelo.compartilhado.erro.RegraDeNegocioException;
import com.zelo.compartilhado.seguranca.Perfil;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "usuario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {

    public static final String NOME_ANONIMIZADO = "Usuário removido";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nome;
    private String email;
    private String senhaHash;

    @Convert(converter = PerfilConverter.class)
    @Column(name = "perfil_id")
    private Perfil perfil;

    /** Obrigatório para contas ativas; nulo só depois da exclusão da conta. */
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    private Instant suspensoAte;
    private boolean suspensaoPermanente;
    private String motivoSuspensao;
    private Instant excluidoEm;
    private Instant criadoEm;
    private Instant atualizadoEm;

    @Version
    private Long versao;

    private Usuario(String nome, String email, String senhaHash, Perfil perfil, Endereco endereco, Instant agora) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.endereco = Objects.requireNonNull(endereco, "O endereço é obrigatório.");
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public static Usuario novoCidadao(String nome, String email, String senhaHash, Endereco endereco, Instant agora) {
        return new Usuario(nome, email, senhaHash, Perfil.CIDADAO, endereco, agora);
    }

    /** Contas de moderador, gestor e administrador, criadas pelo administrador. */
    public static Usuario novoMembroDaEquipe(String nome, String email, String senhaHash, Perfil perfil,
                                             Endereco endereco, Instant agora) {
        if (perfil == Perfil.CIDADAO) {
            throw new RegraDeNegocioException("PERFIL_INVALIDO",
                    "Contas de cidadão são criadas pelo próprio usuário no cadastro.");
        }
        return new Usuario(nome, email, senhaHash, perfil, endereco, agora);
    }

    public void definirPerfil(Perfil novoPerfil, Instant agora) {
        this.perfil = Objects.requireNonNull(novoPerfil);
        this.atualizadoEm = agora;
    }

    /**
     * Troca o endereço. Se o endereço atual é compartilhado com outros usuários (1:N), cria um novo
     * em vez de alterar o de todos.
     */
    public void mudarEndereco(Endereco.Dados dados, boolean compartilhado, Instant agora) {
        if (endereco == null || compartilhado) {
            this.endereco = new Endereco(dados, agora);
        } else {
            endereco.atualizar(dados, agora);
        }
        this.atualizadoEm = agora;
    }

    /** Cidade do endereço: para moderadores e gestores, é a cidade de atuação. */
    public Integer cidadeId() {
        return endereco == null ? null : endereco.getCidadeId();
    }

    public boolean estaSuspenso(Instant agora) {
        return suspensaoPermanente || (suspensoAte != null && suspensoAte.isAfter(agora));
    }

    public boolean estaExcluido() {
        return excluidoEm != null;
    }

    /** @param ate fim da suspensão temporária; ignorado se {@code permanente} */
    public void suspender(Instant ate, boolean permanente, String motivo, Instant agora) {
        if (!permanente && (ate == null || !ate.isAfter(agora))) {
            throw new RegraDeNegocioException("SUSPENSAO_INVALIDA",
                    "Informe uma data futura para a suspensão temporária ou marque como permanente.");
        }
        this.suspensaoPermanente = permanente;
        this.suspensoAte = permanente ? null : ate;
        this.motivoSuspensao = motivo;
        this.atualizadoEm = agora;
    }

    public void reativar(Instant agora) {
        if (!estaSuspenso(agora)) {
            throw new RegraDeNegocioException("USUARIO_NAO_SUSPENSO", "O usuário não está suspenso.");
        }
        this.suspensaoPermanente = false;
        this.suspensoAte = null;
        this.motivoSuspensao = null;
        this.atualizadoEm = agora;
    }

    public void alterarNome(String novoNome, Instant agora) {
        this.nome = novoNome;
        this.atualizadoEm = agora;
    }

    public void alterarSenha(String novoHash, Instant agora) {
        this.senhaHash = novoHash;
        this.atualizadoEm = agora;
    }

    /**
     * Exclusão de conta (LGPD): remove dados pessoais, inclusive o vínculo com o endereço, e mantém a
     * linha só para preservar as referências de histórico, que deixam de identificar a pessoa.
     */
    public void anonimizar(Instant agora) {
        this.nome = NOME_ANONIMIZADO;
        this.email = "removido-" + id + "@zelo.invalid";
        this.senhaHash = null;
        this.perfil = Perfil.CIDADAO;
        this.endereco = null;
        this.suspensaoPermanente = false;
        this.suspensoAte = null;
        this.motivoSuspensao = null;
        this.excluidoEm = agora;
        this.atualizadoEm = agora;
    }
}
