package com.distrischool.gestao_escolar.event;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

@Configuration
@EnableScheduling
public class OutboxConfig {

    @Bean
    OutboxRelay outboxRelay(OutboxRepository repository,
                            ProducerFactory<?, ?> producerFactory,
                            PlatformTransactionManager transactionManager,
                            @Value("${outbox.relay.tamanho-lote:50}") int tamanhoLote,
                            @Value("${outbox.relay.prazo-lote:20s}") Duration prazoLote,
                            @Value("${outbox.limpeza.retencao:7d}") Duration retencao) {
        // Mesmo produtor (acks, idempotência, timeouts), mas com o payload enviado como bytes: ele já
        // é o JSON serializado pelo KafkaEventPublisher. Não é um bean de propósito: um KafkaTemplate
        // declarado aqui desligaria o KafkaTemplate autoconfigurado pelo Spring Boot.
        @SuppressWarnings("unchecked")
        ProducerFactory<String, byte[]> bytesProducerFactory = (ProducerFactory<String, byte[]>)
                producerFactory.copyWithConfigurationOverride(
                        Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        return new OutboxRelay(repository, new KafkaTemplate<>(bytesProducerFactory),
                new TransactionTemplate(transactionManager),
                new OutboxRelay.Configuracao(tamanhoLote, prazoLote, retencao), Clock.systemUTC());
    }

    @Bean
    MeterBinder outboxMetricas(OutboxRelay relay) {
        return registry -> Gauge.builder("outbox.eventos.pendentes", relay, OutboxRelay::pendentes)
                .description("Eventos gravados na outbox e ainda não publicados no Kafka")
                .register(registry);
    }
}
