package com.distrischool.notificacao.evento;

import com.distrischool.notificacao.common.MensagemInvalidaException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê o JSON bruto de um evento. Os produtores não enviam cabeçalhos de tipo Java, então o tipo de
 * destino é sempre escolhido aqui, pelo tópico. Campos desconhecidos são ignorados: o produtor
 * pode acrescentar campos sem quebrar este serviço.
 */
@Component
public class LeitorEventos {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * @throws MensagemInvalidaException se o payload não for um objeto JSON no formato esperado. A
     *         mensagem do Jackson não é repassada: ela pode citar trechos do payload
     */
    public <T> T ler(String topico, byte[] payload, Class<T> tipo) {
        T mensagem;
        try {
            mensagem = mapper.readValue(payload, tipo);
        } catch (JacksonException ex) {
            throw new MensagemInvalidaException(topico + ": JSON inválido (" + ex.getClass().getSimpleName() + ")");
        }
        if (mensagem == null) {
            throw new MensagemInvalidaException(topico + ": payload vazio");
        }
        return mensagem;
    }
}
