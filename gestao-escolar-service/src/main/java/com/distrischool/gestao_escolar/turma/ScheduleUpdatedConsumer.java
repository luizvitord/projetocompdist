package com.distrischool.gestao_escolar.turma;

import com.distrischool.gestao_escolar.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consome o {@code schedule.updated} do gestao-administrativa-service para manter a réplica.
 * Falhas são tratadas pelo error handler de {@code KafkaConsumerConfig} (novas tentativas e DLT).
 */
@Component
@RequiredArgsConstructor
public class ScheduleUpdatedConsumer {

    private final TurmaReplicaService turmaReplicaService;

    // O produtor não envia cabeçalhos de tipo: o JSON é lido sempre como ScheduleUpdatedMessage
    @KafkaListener(topics = KafkaTopics.SCHEDULE_UPDATED, properties = {
            "spring.json.value.default.type=com.distrischool.gestao_escolar.turma.ScheduleUpdatedMessage"
    })
    public void consumir(ScheduleUpdatedMessage mensagem) {
        turmaReplicaService.aplicar(mensagem);
    }
}
