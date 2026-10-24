package com.distrischool.gestao_escolar.event;

import com.distrischool.gestao_escolar.turma.MensagemInvalidaException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratamento de falhas dos consumidores: 3 tentativas no total (a original + 2 novas, com
 * backoff exponencial) e, depois, envio do registro para o tópico {@code <tópico>.DLT}.
 * <p>
 * O registro vai para a DLT como foi lido; os cabeçalhos {@code kafka_dlt-*} trazem a exceção e
 * o tópico, a partição e o offset de origem.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                          ProducerFactory<?, ?> producerFactory) {
        // Um registro que nem chegou a ser desserializado vai para a DLT com os bytes originais;
        // os demais, como JSON (o mesmo serializador dos eventos publicados)
        @SuppressWarnings("unchecked")
        ProducerFactory<String, byte[]> bytesProducerFactory = (ProducerFactory<String, byte[]>)
                producerFactory.copyWithConfigurationOverride(
                        Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, new KafkaTemplate<>(bytesProducerFactory));
        templates.put(Object.class, kafkaTemplate);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(2);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);

        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(templates, KafkaConsumerConfig::destinoDlt);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Evento fora do contrato não melhora com novas tentativas
        errorHandler.addNotRetryableExceptions(MensagemInvalidaException.class);
        return errorHandler;
    }

    /**
     * {@code <tópico>.DLT}, na mesma partição de origem. Explícito porque o padrão do Spring Kafka 4
     * é o sufixo {@code -dlt}.
     */
    static TopicPartition destinoDlt(ConsumerRecord<?, ?> registro, Exception ex) {
        return new TopicPartition(registro.topic() + ".DLT", registro.partition());
    }
}
