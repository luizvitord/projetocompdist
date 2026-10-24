package com.distrischool.gestao_escolar.common;

/** Uma dependência externa (outro serviço) está fora do ar ou com o circuito aberto. */
public class ServicoIndisponivelException extends RuntimeException {

    public ServicoIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
