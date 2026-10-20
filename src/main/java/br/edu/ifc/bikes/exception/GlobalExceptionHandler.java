package br.edu.ifc.bikes.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Tratamento centralizado de erros. Transforma exceções em respostas HTTP padronizadas
 * e registra o log no nível adequado (WARN para erro do cliente, ERROR para falhas do servidor).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404: o service já registrou o ERROR com o ID, aqui só devolvemos a resposta. */
    @ExceptionHandler(ItemNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> tratarNaoEncontrado(ItemNaoEncontradoException ex, HttpServletRequest req) {
        log.warn("Requisição {} {} resultou em 404: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return montar(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    /** 400: falha nas validações do corpo (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> tratarValidacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validação falhou em {} {}: {}", req.getMethod(), req.getRequestURI(), detalhes);
        return montar(HttpStatus.BAD_REQUEST, detalhes, req);
    }

    /** 400: JSON malformado, parâmetro com tipo errado ou erro proposital do ChaosController. */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErroResposta> tratarRequisicaoInvalida(Exception ex, HttpServletRequest req) {
        log.warn("Requisição inválida em {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return montar(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> tratarRecursoInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return montar(HttpStatus.NOT_FOUND, "Recurso não encontrado", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResposta> tratarMetodoNaoSuportado(HttpRequestMethodNotSupportedException ex,
                                                                 HttpServletRequest req) {
        return montar(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req);
    }

    /** 500: qualquer falha inesperada. Loga em ERROR com stack trace completo (vai para o Graylog). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> tratarErroInterno(Exception ex, HttpServletRequest req) {
        // Exceções do próprio Spring MVC que já carregam um status 4xx (ex.: 415, parâmetro ausente)
        // não podem ser contadas como 500, senão poluem a métrica de erros do Grafana.
        if (ex instanceof ErrorResponse resposta && resposta.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.resolve(resposta.getStatusCode().value());
            log.warn("Requisição rejeitada em {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
            return montar(status != null ? status : HttpStatus.BAD_REQUEST, ex.getMessage(), req);
        }
        log.error("Erro interno em {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        return montar(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor", req);
    }

    private ResponseEntity<ErroResposta> montar(HttpStatus status, String mensagem, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ErroResposta.of(status.value(), status.getReasonPhrase(), mensagem, req.getRequestURI()));
    }
}
