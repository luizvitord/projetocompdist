package com.distrischool.gestao_escolar.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduz exceções em respostas RFC 9457 (ProblemDetail). Estende
 * {@link ResponseEntityExceptionHandler} para cobrir também os erros padrão do Spring MVC.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ProblemDetail handleRegraNegocio(RegraNegocioException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ValidacaoException.class)
    public ProblemDetail handleValidacao(ValidacaoException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        ex.getPropriedades().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    public ProblemDetail handleServicoIndisponivel(ServicoIndisponivelException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> erros = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> erros.putIfAbsent(e.getField(), e.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos");
        problem.setProperty("erros", erros);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Validação de parâmetros fora de um objeto de corpo (ex.: lista no corpo, com
     * {@code List<@Valid X>}), no mesmo formato: {@code erros} com o caminho de cada campo,
     * como {@code notas[0].valor}.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (ParameterValidationResult resultado : ex.getParameterValidationResults()) {
            String caminho = resultado.getMethodParameter().getParameterName();
            if (resultado.getContainerIndex() != null) {
                caminho += "[" + resultado.getContainerIndex() + "]";
            }
            if (resultado instanceof ParameterErrors errors) {
                for (FieldError e : errors.getFieldErrors()) {
                    erros.putIfAbsent(caminho + "." + e.getField(), e.getDefaultMessage());
                }
                String base = caminho;
                errors.getGlobalErrors().forEach(e -> erros.putIfAbsent(base, e.getDefaultMessage()));
            } else {
                String base = caminho;
                resultado.getResolvableErrors().forEach(e -> erros.putIfAbsent(base, e.getDefaultMessage()));
            }
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos");
        problem.setProperty("erros", erros);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }
}
