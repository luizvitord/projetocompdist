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
}
