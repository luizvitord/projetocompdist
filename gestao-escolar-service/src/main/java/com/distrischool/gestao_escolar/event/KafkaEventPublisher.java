package com.distrischool.gestao_escolar.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Encaminha os eventos de domínio para o Kafka somente após o commit da transação,
 * evitando publicar eventos de operações que sofreram rollback.
 * <p>
 * Os logs trazem só o tópico e a chave: os payloads têm dados pessoais de alunos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener
    public void on(StudentCreatedEvent event) {
        send(KafkaTopics.STUDENT_CREATED, event.alunoId(), event);
    }

    @TransactionalEventListener
    public void on(EnrollmentCreatedEvent event) {
        send(KafkaTopics.ENROLLMENT_CREATED, event.alunoId(), event);
    }

    @TransactionalEventListener
    public void on(EnrollmentUpdatedEvent event) {
        send(KafkaTopics.ENROLLMENT_UPDATED, event.alunoId(), event);
    }

    private void send(String topic, Long key, Object event) {
        kafkaTemplate.send(topic, String.valueOf(key), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar evento no tópico {} (chave {})", topic, key, ex);
                    } else {
                        log.debug("Evento publicado no tópico {} (chave {})", topic, key);
                    }
                });
    }
}
