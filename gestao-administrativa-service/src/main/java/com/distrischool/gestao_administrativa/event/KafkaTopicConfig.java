package com.distrischool.gestao_administrativa.event;

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
    NewTopic teacherAssignedTopic() {
        return TopicBuilder.name(KafkaTopics.TEACHER_ASSIGNED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic scheduleUpdatedTopic() {
        return TopicBuilder.name(KafkaTopics.SCHEDULE_UPDATED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic staffCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.STAFF_CREATED).partitions(3).replicas(1).build();
    }
}
