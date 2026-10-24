package com.distrischool.gestao_administrativa.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Encaminha os eventos de domínio para o Kafka somente após o commit da transação,
 * evitando publicar eventos de operações que sofreram rollback.
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
    public void on(TeacherAssignedEvent event) {
        send(KafkaTopics.TEACHER_ASSIGNED, event.turmaId(), event);
    }

    @TransactionalEventListener
    public void on(ScheduleUpdatedEvent event) {
        send(KafkaTopics.SCHEDULE_UPDATED, event.turmaId(), event);
    }

    @TransactionalEventListener
    public void on(StaffCreatedEvent event) {
        send(KafkaTopics.STAFF_CREATED, event.tecnicoId(), event);
    }

    private void send(String topic, Long key, Object event) {
        kafkaTemplate.send(topic, String.valueOf(key), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar evento no tópico {}: {}", topic, event, ex);
                    } else {
                        log.debug("Evento publicado no tópico {}: {}", topic, event);
                    }
                });
    }
}
