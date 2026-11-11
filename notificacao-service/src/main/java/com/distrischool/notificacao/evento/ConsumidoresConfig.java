package com.distrischool.notificacao.evento;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * Consumidores do Spring Cloud Stream, no estilo funcional: cada bean {@code Consumer} é ligado ao
 * seu tópico pelo binding {@code <nome do bean>-in-0} (ver {@code spring.cloud.stream.bindings.*}
 * e {@code spring.cloud.function.definition} em application.properties).
 * <p>
 * Um consumidor por tópico porque a DLT ({@code <tópico>.DLT}) é configurada por binding. O
 * payload chega como bytes, sem conversão: o hash de idempotência é calculado sobre o JSON bruto.
 * <p>
 * Todos usam o grupo {@code notificacao-service}: as réplicas dividem as partições (cada evento é
 * processado por uma só), enquanto o gestao-escolar-service lê o mesmo {@code schedule.updated} no
 * grupo dele (publish/subscribe).
 */
@Configuration
public class ConsumidoresConfig {

    @Bean
    Consumer<Message<byte[]>> gradePosted(ProcessadorEventos processador) {
        return mensagem -> processador.processar(KafkaTopics.GRADE_POSTED, mensagem);
    }

    @Bean
    Consumer<Message<byte[]>> attendanceRecorded(ProcessadorEventos processador) {
        return mensagem -> processador.processar(KafkaTopics.ATTENDANCE_RECORDED, mensagem);
    }

    @Bean
    Consumer<Message<byte[]>> enrollmentCreated(ProcessadorEventos processador) {
        return mensagem -> processador.processar(KafkaTopics.ENROLLMENT_CREATED, mensagem);
    }

    @Bean
    Consumer<Message<byte[]>> enrollmentUpdated(ProcessadorEventos processador) {
        return mensagem -> processador.processar(KafkaTopics.ENROLLMENT_UPDATED, mensagem);
    }

    @Bean
    Consumer<Message<byte[]>> studentCreated(ProcessadorEventos processador) {
        return mensagem -> processador.processar(KafkaTopics.STUDENT_CREATED, mensagem);
    }

    @Bean
    Consumer<Message<byte[]>> teacherAssigned(ProcessadorEventos processador) {
        return mensagem -> processador.processar(KafkaTopics.TEACHER_ASSIGNED, mensagem);
    }

    @Bean
    Consumer<Message<byte[]>> staffCreated(ProcessadorEventos processador) {
        return mensagem -> processador.processar(KafkaTopics.STAFF_CREATED, mensagem);
    }

    @Bean
    Consumer<Message<byte[]>> scheduleUpdated(ProcessadorEventos processador) {
        return mensagem -> processador.processar(KafkaTopics.SCHEDULE_UPDATED, mensagem);
    }
}
