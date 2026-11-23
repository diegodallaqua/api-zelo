package com.zelo.usuario.api;

import com.zelo.compartilhado.seguranca.Perfil;
import com.zelo.usuario.application.EnderecoService.EnderecoVisao;
import com.zelo.usuario.domain.Consentimento;
import com.zelo.usuario.domain.Endereco;
import com.zelo.usuario.domain.Usuario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UsuarioDtos {

    static final String REGRA_SENHA = "^(?=.*\\p{L})(?=.*\\d).+$";
    static final String MENSAGEM_SENHA = "A senha deve conter ao menos uma letra e um número.";

    private UsuarioDtos() {
    }

    public record EnderecoRequest(
            @NotBlank(message = "O CEP é obrigatório.")
            @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "Informe o CEP com 8 dígitos (ex.: 01001-000).") String cep,
            @NotBlank(message = "A rua é obrigatória.") @Size(max = 150, message = "A rua deve ter no máximo 150 caracteres.") String rua,
            @NotBlank(message = "O bairro é obrigatório.") @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.") String bairro,
            @NotBlank(message = "O número é obrigatório.") @Size(max = 10, message = "O número deve ter no máximo 10 caracteres.") String numero,
            @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres.") String complemento,
            @NotNull(message = "A cidade é obrigatória.") Integer cidadeId) {

        public Endereco.Dados paraDados() {
            return new Endereco.Dados(cep, rua, bairro, numero, complemento, cidadeId);
        }
    }

    public record CadastroRequest(
            @NotBlank(message = "O nome é obrigatório.") @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres.") String nome,
            @NotBlank(message = "O e-mail é obrigatório.") @Email(message = "Informe um e-mail válido.") @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.") String email,
            @NotBlank(message = "A senha é obrigatória.") @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            @Pattern(regexp = REGRA_SENHA, message = MENSAGEM_SENHA) String senha,
            @NotNull(message = "O endereço é obrigatório.") @Valid EnderecoRequest endereco,
            @NotNull(message = "Informe o aceite dos Termos de Uso.") Boolean aceiteTermosDeUso,
            @NotNull(message = "Informe o aceite da Política de Privacidade.") Boolean aceitePoliticaDePrivacidade) {
    }

    public record LoginRequest(
            @NotBlank(message = "O e-mail é obrigatório.") String email,
            @NotBlank(message = "A senha é obrigatória.") String senha) {
    }

    public record RefreshTokenRequest(@NotBlank(message = "O refreshToken é obrigatório.") String refreshToken) {
    }

    public record AtualizarContaRequest(
            @NotBlank(message = "O nome é obrigatório.") @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres.") String nome) {
    }

    public record AlterarSenhaRequest(
            @NotBlank(message = "A senha atual é obrigatória.") String senhaAtual,
            @NotBlank(message = "A nova senha é obrigatória.") @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            @Pattern(regexp = REGRA_SENHA, message = MENSAGEM_SENHA) String novaSenha) {
    }

    public record ExcluirContaRequest(@NotBlank(message = "Confirme a exclusão informando sua senha.") String senha) {
    }

    public record ConsentimentoRequest(
            @NotNull(message = "O tipo de consentimento é obrigatório.") Consentimento.Tipo tipo,
            @NotNull(message = "Informe se o consentimento foi dado.") Boolean aceito) {
    }

    public record ConsentimentoResposta(UUID id, Consentimento.Tipo tipo, String versao, boolean aceito,
                                        Instant registradoEm) {

        public static ConsentimentoResposta de(Consentimento c) {
            return new ConsentimentoResposta(c.getId(), c.getTipo(), c.getVersao(), c.isAceito(), c.getRegistradoEm());
        }
    }

    public record ContaResposta(UUID id, String nome, String email, Perfil perfil, EnderecoVisao endereco,
                                boolean suspenso, Instant suspensoAte, boolean suspensaoPermanente, Instant criadoEm) {

        public static ContaResposta de(Usuario u, EnderecoVisao endereco, Instant agora) {
            return new ContaResposta(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), endereco,
                    u.estaSuspenso(agora), u.getSuspensoAte(), u.isSuspensaoPermanente(), u.getCriadoEm());
        }
    }

    public record NovoMembroDaEquipeRequest(
            @NotBlank(message = "O nome é obrigatório.") @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres.") String nome,
            @NotBlank(message = "O e-mail é obrigatório.") @Email(message = "Informe um e-mail válido.") @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.") String email,
            @NotBlank(message = "A senha é obrigatória.") @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
            @Pattern(regexp = REGRA_SENHA, message = MENSAGEM_SENHA) String senha,
            @NotNull(message = "O perfil é obrigatório.") Perfil perfil,
            @NotNull(message = "O endereço é obrigatório.") @Valid EnderecoRequest endereco) {
    }

    public record AlterarPerfilRequest(@NotNull(message = "O perfil é obrigatório.") Perfil perfil) {
    }

    public record UsuarioAdminResposta(UUID id, String nome, String email, Perfil perfil, EnderecoVisao endereco,
                                       boolean suspenso, Instant suspensoAte, boolean suspensaoPermanente,
                                       String motivoSuspensao, boolean excluido, Instant criadoEm) {

        public static UsuarioAdminResposta de(Usuario u, EnderecoVisao endereco, Instant agora) {
            return new UsuarioAdminResposta(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), endereco,
                    u.estaSuspenso(agora), u.getSuspensoAte(), u.isSuspensaoPermanente(), u.getMotivoSuspensao(),
                    u.estaExcluido(), u.getCriadoEm());
        }
    }
}
