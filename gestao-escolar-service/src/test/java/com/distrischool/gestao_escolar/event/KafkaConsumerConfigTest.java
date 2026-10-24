package com.distrischool.gestao_escolar.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    @Test
    void registroComFalhaVaiParaODltComPontoNaMesmaParticao() {
        ConsumerRecord<String, String> registro = new ConsumerRecord<>(KafkaTopics.SCHEDULE_UPDATED, 2, 10L, "12", "{}");

        TopicPartition destino = KafkaConsumerConfig.destinoDlt(registro, new RuntimeException());

        assertThat(destino.topic()).isEqualTo(KafkaTopics.SCHEDULE_UPDATED_DLT).isEqualTo("schedule.updated.DLT");
        assertThat(destino.partition()).isEqualTo(2);
    }
}
