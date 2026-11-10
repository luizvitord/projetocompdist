package com.distrischool.gestao_escolar.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * Grava os eventos de domínio na outbox, na mesma transação da operação de negócio: o evento
 * existe se, e somente se, a operação foi confirmada. O {@link OutboxRelay} publica no Kafka
 * depois do commit, inclusive se o Kafka estiver fora do ar neste momento.
 * <p>
 * O payload é serializado aqui pelo mesmo serializador do produtor, com a mesma configuração
 * (JacksonJsonSerializer, sem cabeçalhos de tipo); o relay envia esses bytes como estão. A
 * mensagem que chega ao Kafka é a mesma de quando o evento era publicado diretamente.
 * <p>
 * {@code MANDATORY}: um evento publicado fora de uma transação é erro de programação e falha na
 * hora, em vez de se perder.
 * <p>
 * Os logs trazem só o tópico e a chave: os payloads têm dados pessoais de alunos.
 */
@Slf4j
@Component
public class KafkaEventPublisher {

    private final OutboxRepository outbox;
    private final JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>();

    public KafkaEventPublisher(OutboxRepository outbox, ProducerFactory<?, ?> producerFactory) {
        this.outbox = outbox;
        serializer.configure(producerFactory.getConfigurationProperties(), false);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(StudentCreatedEvent event) {
        gravar(KafkaTopics.STUDENT_CREATED, event.alunoId(), event);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(EnrollmentCreatedEvent event) {
        gravar(KafkaTopics.ENROLLMENT_CREATED, event.alunoId(), event);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(EnrollmentUpdatedEvent event) {
        gravar(KafkaTopics.ENROLLMENT_UPDATED, event.alunoId(), event);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(AttendanceRecordedEvent event) {
        gravar(KafkaTopics.ATTENDANCE_RECORDED, event.turmaId(), event);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(GradePostedEvent event) {
        gravar(KafkaTopics.GRADE_POSTED, event.turmaId(), event);
    }

    private void gravar(String topic, Long key, Object event) {
        String payload = new String(serializer.serialize(topic, event), StandardCharsets.UTF_8);
        outbox.inserir(topic, String.valueOf(key), payload);
        log.debug("Evento gravado na outbox para o tópico {} (chave {})", topic, key);
    }
}
