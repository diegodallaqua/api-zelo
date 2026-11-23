package com.zelo.compartilhado.erro;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tradução centralizada de exceções para respostas HTTP (RFC 9457) com mensagens em português.
 * Também recebe as exceções de autenticação/autorização e de rate limit, encaminhadas pelos
 * handlers de segurança e pelo filtro de rate limit.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // ---------------------------------------------------------------- exceções de negócio

    @ExceptionHandler(ErroDeNegocioException.class)
    public ResponseEntity<ProblemDetail> tratarErroDeNegocio(ErroDeNegocioException ex) {
        HttpStatus status = statusDe(ex);
        ProblemDetail problema = Problemas.criar(status, ex.getCodigo(), ex.getMessage());
        ex.getPropriedades().forEach(problema::setProperty);
        HttpHeaders headers = new HttpHeaders();
        if (status == HttpStatus.UNAUTHORIZED) {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return ResponseEntity.status(status).headers(headers).body(problema);
    }

    private HttpStatus statusDe(ErroDeNegocioException ex) {
        if (ex instanceof RequisicaoInvalidaException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof NaoAutenticadoException) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ex instanceof AcessoNegadoException) {
            return HttpStatus.FORBIDDEN;
        }
        if (ex instanceof RecursoNaoEncontradoException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof ConflitoException) {
            return HttpStatus.CONFLICT;
        }
        if (ex instanceof RegraDeNegocioException) {
            return HttpStatus.UNPROCESSABLE_CONTENT;
        }
        if (ex instanceof LimiteExcedidoException) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (ex instanceof ServicoIndisponivelException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (ex instanceof ArquivoInvalidoException arquivo) {
            return switch (arquivo.getMotivo()) {
                case FORMATO_NAO_SUPORTADO -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                case TAMANHO_EXCEDIDO -> HttpStatus.CONTENT_TOO_LARGE;
                case CONTEUDO_INVALIDO -> HttpStatus.UNPROCESSABLE_CONTENT;
            };
        }
        log.error("Exceção de negócio sem status mapeado: {}", ex.getClass().getName());
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    // ---------------------------------------------------------------- segurança

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> tratarAutenticacao(AuthenticationException ex) {
        String detalhe = ex instanceof InvalidBearerTokenException
                ? "Token de acesso inválido ou expirado. Faça login novamente ou renove o token."
                : "Autenticação necessária para acessar este recurso.";
        String codigo = ex instanceof InvalidBearerTokenException ? "TOKEN_INVALIDO" : "NAO_AUTENTICADO";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(Problemas.criar(HttpStatus.UNAUTHORIZED, codigo, detalhe));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> tratarAcessoNegado(AccessDeniedException ex) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || autenticacao instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .body(Problemas.criar(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO",
                            "Autenticação necessária para acessar este recurso."));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Problemas.criar(HttpStatus.FORBIDDEN, "ACESSO_NEGADO",
                        "Seu perfil não tem permissão para executar esta operação."));
    }

    // ---------------------------------------------------------------- persistência

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> tratarConcorrencia(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Problemas.criar(HttpStatus.CONFLICT, "CONFLITO_DE_CONCORRENCIA",
                        "O recurso foi alterado por outra operação ao mesmo tempo. Recarregue e tente novamente."));
    }

    /**
     * Só violação de restrição (SQLSTATE 23xxx: único, FK, check) é conflito. Outros erros de dados
     * (ex.: 22xxx, conversão) indicam defeito e viram 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> tratarIntegridade(DataIntegrityViolationException ex) {
        String sqlState = ex.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : null;
        if (sqlState != null && !sqlState.startsWith("23")) {
            return tratarInesperado(ex);
        }
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Problemas.criar(HttpStatus.CONFLICT, "CONFLITO_DE_DADOS",
                        "A operação conflita com dados já existentes ou referenciados."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> tratarConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, Object>> erros = ex.getConstraintViolations().stream()
                .map(v -> erro(ultimoNo(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        ProblemDetail problema = Problemas.criar(HttpStatus.BAD_REQUEST, "DADOS_INVALIDOS",
                "Um ou mais campos estão inválidos.");
        problema.setProperty("erros", erros);
        return ResponseEntity.badRequest().body(problema);
    }

    /** {@link MaxUploadSizeExceededException} é tratada pela classe base (413). */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ProblemDetail> tratarMultipart(MultipartException ex) {
        return ResponseEntity.badRequest()
                .body(Problemas.criar(HttpStatus.BAD_REQUEST, "MULTIPART_INVALIDO",
                        "Não foi possível ler o envio multipart. Verifique o formato da requisição."));
    }

    // ---------------------------------------------------------------- fallback

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> tratarInesperado(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.internalServerError()
                .body(Problemas.criar(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO",
                        "Ocorreu um erro inesperado. Tente novamente mais tarde."));
    }

    // ---------------------------------------------------------------- exceções do Spring MVC

    /**
     * Todas as exceções padrão do Spring MVC tratadas por {@link ResponseEntityExceptionHandler}
     * passam por aqui; trocamos o corpo por um ProblemDetail em português.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problema = traduzir(ex, statusCode);
        if (statusCode.is5xxServerError()) {
            log.error("Erro do framework com status {}", statusCode.value(), ex);
        }
        return super.handleExceptionInternal(ex, problema, headers, statusCode, request);
    }

    private ProblemDetail traduzir(Exception ex, HttpStatusCode status) {
        if (ex instanceof MethodArgumentNotValidException e) {
            List<Map<String, Object>> erros = new ArrayList<>();
            e.getBindingResult().getFieldErrors().forEach(fe -> erros.add(erro(fe.getField(), mensagemDoCampo(fe))));
            e.getBindingResult().getGlobalErrors().forEach(ge -> erros.add(erro(ge.getObjectName(), ge.getDefaultMessage())));
            return comErros(Problemas.criar(status, "DADOS_INVALIDOS", "Um ou mais campos estão inválidos."), erros);
        }
        if (ex instanceof HandlerMethodValidationException e) {
            List<Map<String, Object>> erros = new ArrayList<>();
            e.getParameterValidationResults().forEach(resultado -> {
                String parametro = resultado.getMethodParameter().getParameterName();
                for (MessageSourceResolvable erro : resultado.getResolvableErrors()) {
                    erros.add(erro(parametro, erro.getDefaultMessage()));
                }
            });
            return comErros(Problemas.criar(status, "DADOS_INVALIDOS", "Um ou mais parâmetros estão inválidos."), erros);
        }
        if (ex instanceof HttpMessageNotReadableException e) {
            return Problemas.criar(status, "CORPO_INVALIDO", mensagemCorpoInvalido(e));
        }
        if (ex instanceof TypeMismatchException e) {
            String nome = e.getPropertyName() != null ? e.getPropertyName() : "?";
            return Problemas.criar(status, "PARAMETRO_INVALIDO",
                    "Valor inválido para o parâmetro '" + nome + "'." + valoresAceitos(e.getRequiredType()));
        }
        if (ex instanceof MissingServletRequestParameterException e) {
            return Problemas.criar(status, "PARAMETRO_OBRIGATORIO",
                    "O parâmetro obrigatório '" + e.getParameterName() + "' não foi informado.");
        }
        if (ex instanceof MissingServletRequestPartException e) {
            return Problemas.criar(status, "PARTE_OBRIGATORIA",
                    "A parte obrigatória '" + e.getRequestPartName() + "' não foi enviada no multipart.");
        }
        if (ex instanceof ServletRequestBindingException) {
            return Problemas.criar(status, "REQUISICAO_INVALIDA", "A requisição está incompleta ou mal formada.");
        }
        if (ex instanceof HttpRequestMethodNotSupportedException e) {
            return Problemas.criar(status, "METODO_NAO_SUPORTADO",
                    "O método " + e.getMethod() + " não é suportado nesta rota.");
        }
        if (ex instanceof HttpMediaTypeNotSupportedException e) {
            String aceitos = e.getSupportedMediaTypes().stream().map(Object::toString).collect(Collectors.joining(", "));
            return Problemas.criar(status, "TIPO_DE_CONTEUDO_NAO_SUPORTADO",
                    "Tipo de conteúdo não suportado: " + e.getContentType() + ". Use: " + aceitos + ".");
        }
        if (ex instanceof HttpMediaTypeNotAcceptableException) {
            return Problemas.criar(status, "FORMATO_NAO_ACEITO",
                    "Não é possível responder no formato solicitado pelo cabeçalho Accept.");
        }
        if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
            return Problemas.criar(status, "ROTA_NAO_ENCONTRADA", "A rota solicitada não existe.");
        }
        if (ex instanceof MaxUploadSizeExceededException) {
            return Problemas.criar(status, "ARQUIVO_TAMANHO_EXCEDIDO",
                    "O envio excede o tamanho máximo permitido por arquivo ou por requisição.");
        }
        if (ex instanceof AsyncRequestTimeoutException) {
            return Problemas.criar(status, "TEMPO_ESGOTADO", "O processamento excedeu o tempo limite.");
        }
        if (status.is5xxServerError()) {
            return Problemas.criar(status, "ERRO_INTERNO", "Ocorreu um erro inesperado. Tente novamente mais tarde.");
        }
        return Problemas.criar(status, "REQUISICAO_INVALIDA", "A requisição não pôde ser processada.");
    }

    private static String mensagemDoCampo(FieldError fe) {
        if (fe.isBindingFailure()) {
            Class<?> tipo = null;
            if (fe.contains(TypeMismatchException.class)) {
                tipo = fe.unwrap(TypeMismatchException.class).getRequiredType();
            }
            return "Valor inválido." + valoresAceitos(tipo);
        }
        return fe.getDefaultMessage();
    }

    private static String mensagemCorpoInvalido(HttpMessageNotReadableException e) {
        Throwable causa = e.getCause();
        if (causa instanceof MismatchedInputException mismatch) {
            String campo = caminho(mismatch);
            String aceitos = valoresAceitos(mismatch.getTargetType());
            if (!campo.isBlank()) {
                return "Valor inválido para o campo '" + campo + "'." + aceitos;
            }
        }
        if (causa instanceof JacksonException) {
            return "O corpo da requisição não é um JSON válido.";
        }
        return "O corpo da requisição está ausente ou mal formatado.";
    }

    private static String caminho(JacksonException ex) {
        return ex.getPath().stream()
                .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }

    private static String valoresAceitos(Class<?> tipo) {
        if (tipo != null && tipo.isEnum()) {
            return " Valores aceitos: " + Arrays.stream(tipo.getEnumConstants())
                    .map(Object::toString).collect(Collectors.joining(", ")) + ".";
        }
        return "";
    }

    private static String ultimoNo(String caminho) {
        int ponto = caminho.lastIndexOf('.');
        return ponto >= 0 ? caminho.substring(ponto + 1) : caminho;
    }

    private static Map<String, Object> erro(String campo, String mensagem) {
        Map<String, Object> erro = new LinkedHashMap<>();
        erro.put("campo", campo);
        erro.put("mensagem", mensagem);
        return erro;
    }

    private static ProblemDetail comErros(ProblemDetail problema, List<Map<String, Object>> erros) {
        problema.setProperty("erros", erros);
        return problema;
    }
}
