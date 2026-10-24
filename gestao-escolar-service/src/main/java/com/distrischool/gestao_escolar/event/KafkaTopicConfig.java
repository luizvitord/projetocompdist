package com.distrischool.gestao_escolar.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic studentCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.STUDENT_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic enrollmentCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.ENROLLMENT_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic enrollmentUpdatedTopic() {
        return TopicBuilder.name(KafkaTopics.ENROLLMENT_UPDATED).partitions(3).replicas(1).build();
    }

    /**
     * Mesmo número de partições do schedule.updated: o DeadLetterPublishingRecoverer publica na
     * mesma partição de origem.
     */
    @Bean
    NewTopic scheduleUpdatedDltTopic() {
        return TopicBuilder.name(KafkaTopics.SCHEDULE_UPDATED_DLT).partitions(3).replicas(1).build();
    }
}
