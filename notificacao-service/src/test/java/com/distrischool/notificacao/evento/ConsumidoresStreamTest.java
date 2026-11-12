package com.distrischool.notificacao.evento;

import com.distrischool.notificacao.common.MensagemInvalidaException;
import com.distrischool.notificacao.notificacao.Perfil;
import com.distrischool.notificacao.temporeal.EntregadorTempoReal;
import com.distrischool.notificacao.temporeal.NotificacaoCriadaMensagem;
import com.distrischool.notificacao.temporeal.TempoRealStreamConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.function.cloudevent.CloudEventsFunctionExtensionConfiguration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaExtendedBindingProperties;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.cloud.stream.config.BindingProperties;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.FunctionConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Consumidores ligados pelo Spring Cloud Stream, com o test binder (sem Kafka) e as propriedades
 * reais de application.properties: bindings, grupo, novas tentativas e DLT.
 * <p>
 * O test binder repete as tentativas e entrega as falhas ao canal de erros, como o binder Kafka; a
 * publicação na DLT em si é do binder Kafka ({@code enable-dlq}/{@code dlq-name}), que assina esse
 * mesmo canal de erros do binding. Aqui se confere que a falha chega lá na hora certa e que o
 * nome da DLT de cada binding é {@code <tópico>.DLT}.
 */
class ConsumidoresStreamTest {

    private static final String NOTIFICACAO_CRIADA = "notificacao.criada";
    private static final String BINDING_TEMPO_REAL = "entregarNotificacao-in-0";
    private static final byte[] PAYLOAD = "{\"turmaId\":12}".getBytes(StandardCharsets.UTF_8);

    private ConfigurableApplicationContext context;
    private ProcessadorEventos processador;
    private EntregadorTempoReal entregador;
    private InputDestination entrada;
    private final List<ErrorMessage> erros = new CopyOnWriteArrayList<>();

    /**
     * Só as autoconfigurações do Stream que ligam as funções aos bindings: o contexto não tem
     * banco, JPA nem web. Quem recebe as mensagens são simulações.
     */
    @Configuration
    @ImportAutoConfiguration({ContextFunctionCatalogAutoConfiguration.class,
            CloudEventsFunctionExtensionConfiguration.class, FunctionConfiguration.class})
    static class Simulacoes {

        @Bean
        ProcessadorEventos processadorEventos() {
            return mock(ProcessadorEventos.class);
        }

        @Bean
        EntregadorTempoReal entregadorTempoReal() {
            return mock(EntregadorTempoReal.class);
        }
    }

    private static Class<?>[] configuracao() {
        return TestChannelBinderConfiguration.getCompleteConfiguration(
                ConsumidoresConfig.class, TempoRealStreamConfig.class, LeitorEventos.class, Simulacoes.class);
    }

    @BeforeEach
    void subir() {
        context = new SpringApplicationBuilder(configuracao())
                .web(WebApplicationType.NONE)
                .run(esperasCurtas());
        processador = context.getBean(ProcessadorEventos.class);
        entregador = context.getBean(EntregadorTempoReal.class);
        entrada = context.getBean(InputDestination.class);
        context.getBean("errorChannel", SubscribableChannel.class)
                .subscribe(mensagem -> erros.add((ErrorMessage) mensagem));
    }

    @AfterEach
    void descer() {
        context.close();
    }

    /** Mesmas tentativas de application.properties, com milissegundos no lugar de segundos. */
    private static String[] esperasCurtas() {
        List<String> argumentos = new ArrayList<>(List.of("--spring.jmx.enabled=false"));
        for (String funcao : List.of("gradePosted", "attendanceRecorded", "enrollmentCreated", "enrollmentUpdated",
                "studentCreated", "teacherAssigned", "staffCreated", "scheduleUpdated")) {
            String consumidor = "--spring.cloud.stream.bindings." + funcao + "-in-0.consumer.";
            argumentos.add(consumidor + "back-off-initial-interval=10");
            argumentos.add(consumidor + "back-off-max-interval=20");
        }
        return argumentos.toArray(String[]::new);
    }

    private static Message<byte[]> registro() {
        return MessageBuilder.withPayload(PAYLOAD)
                .setHeader(KafkaHeaders.RECEIVED_KEY, "12".getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private static boolean causadoPor(Throwable erro, Class<? extends Throwable> tipo) {
        for (Throwable t = erro; t != null; t = t.getCause() != t ? t.getCause() : null) {
            if (tipo.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    /** Bindings de entrada dos eventos de domínio, por tópico (o do tempo real fica de fora). */
    private static Map<String, String> bindingsDeDominio(Map<String, BindingProperties> bindings) {
        return bindings.entrySet().stream()
                .filter(b -> b.getKey().endsWith("-in-0") && !b.getKey().equals(BINDING_TEMPO_REAL))
                .collect(Collectors.toMap(b -> b.getValue().getDestination(), Map.Entry::getKey));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cadaTopicoChegaAoProcessadorComOPayloadBruto() {
        for (String topico : KafkaTopics.TODOS) {
            entrada.send(registro(), topico);

            ArgumentCaptor<Message<byte[]>> recebida = ArgumentCaptor.forClass(Message.class);
            verify(processador).processar(eq(topico), recebida.capture());
            assertThat(recebida.getValue().getPayload()).isEqualTo(PAYLOAD);
            assertThat((byte[]) recebida.getValue().getHeaders().get(KafkaHeaders.RECEIVED_KEY))
                    .isEqualTo("12".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(erros).isEmpty();
    }

    @Test
    void payloadInvalidoNaoERepetidoESegueDiretoParaOTratamentoDeErro() {
        doThrow(new MensagemInvalidaException("grade.posted: JSON inválido"))
                .when(processador).processar(eq(KafkaTopics.GRADE_POSTED), any());

        entrada.send(registro(), KafkaTopics.GRADE_POSTED);

        // Uma única chamada: repetir um payload fora do contrato não resolve
        verify(processador, times(1)).processar(eq(KafkaTopics.GRADE_POSTED), any());
        assertThat(erros).hasSize(1);
        assertThat(causadoPor(erros.get(0).getPayload(), MensagemInvalidaException.class)).isTrue();
    }

    @Test
    void falhaPersistenteERepetidaTresVezesAntesDoTratamentoDeErro() {
        doThrow(new IllegalStateException("banco fora do ar"))
                .when(processador).processar(eq(KafkaTopics.ATTENDANCE_RECORDED), any());

        entrada.send(registro(), KafkaTopics.ATTENDANCE_RECORDED);

        verify(processador, times(3)).processar(eq(KafkaTopics.ATTENDANCE_RECORDED), any());
        assertThat(erros).hasSize(1);
        assertThat(causadoPor(erros.get(0).getPayload(), IllegalStateException.class)).isTrue();
    }

    @Test
    void falhaTemporariaERecuperadaNasNovasTentativas() {
        doThrow(new IllegalStateException("banco fora do ar"))
                .doThrow(new IllegalStateException("banco fora do ar"))
                .doNothing()
                .when(processador).processar(eq(KafkaTopics.ENROLLMENT_CREATED), any());

        entrada.send(registro(), KafkaTopics.ENROLLMENT_CREATED);

        verify(processador, times(3)).processar(eq(KafkaTopics.ENROLLMENT_CREATED), any());
        assertThat(erros).isEmpty();
    }

    @Test
    void umaFalhaNaoImpedeOProximoRegistro() {
        doThrow(new MensagemInvalidaException("student.created: JSON inválido"))
                .doNothing()
                .when(processador).processar(eq(KafkaTopics.STUDENT_CREATED), any());

        entrada.send(registro(), KafkaTopics.STUDENT_CREATED);
        entrada.send(registro(), KafkaTopics.STUDENT_CREATED);

        verify(processador, times(2)).processar(eq(KafkaTopics.STUDENT_CREATED), any());
        assertThat(erros).hasSize(1);
    }

    @Test
    void cadaTopicoTemGrupoCompartilhadoTresTentativasEDltComSufixoPontoDlt() {
        Map<String, BindingProperties> bindings = context.getBean(BindingServiceProperties.class).getBindings();
        KafkaExtendedBindingProperties kafka = Binder.get(context.getEnvironment())
                .bind("spring.cloud.stream.kafka", KafkaExtendedBindingProperties.class).get();

        Map<String, String> bindingPorTopico = bindingsDeDominio(bindings);
        assertThat(bindingPorTopico.keySet()).containsExactlyInAnyOrderElementsOf(KafkaTopics.TODOS);

        bindingPorTopico.forEach((topico, binding) -> {
            BindingProperties propriedades = bindings.get(binding);
            assertThat(propriedades.getGroup()).as(binding).isEqualTo("notificacao-service");
            assertThat(propriedades.getConsumer().getMaxAttempts()).as(binding).isEqualTo(3);
            assertThat(propriedades.getConsumer().getBackOffMultiplier()).as(binding).isEqualTo(2.0);
            assertThat(propriedades.getConsumer().getRetryableExceptions()).as(binding)
                    .containsEntry(MensagemInvalidaException.class, false);

            KafkaConsumerProperties consumidorKafka = kafka.getExtendedConsumerProperties(binding);
            assertThat(consumidorKafka.isEnableDlq()).as(binding).isTrue();
            assertThat(consumidorKafka.getDlqName()).as(binding).isEqualTo(topico + KafkaTopics.SUFIXO_DLT);
        });
    }

    @Test
    void esperasDeProducaoSaoDeUmEDoisSegundos() {
        // As propriedades reais, sem as esperas curtas deste teste
        try (ConfigurableApplicationContext producao = new SpringApplicationBuilder(configuracao())
                .web(WebApplicationType.NONE)
                .run("--spring.jmx.enabled=false")) {
            Map<String, BindingProperties> bindings = producao.getBean(BindingServiceProperties.class).getBindings();
            bindingsDeDominio(bindings).values().forEach(binding -> {
                assertThat(bindings.get(binding).getConsumer().getBackOffInitialInterval()).as(binding).isEqualTo(1000);
                assertThat(bindings.get(binding).getConsumer().getBackOffMaxInterval()).as(binding).isEqualTo(2000);
            });
        }
    }

    // --- tempo real: tópico interno notificacao.criada ---

    @Test
    void avisosDeNotificacaoCriadaSaoLidosEmGrupoAnonimoSemDlt() {
        Map<String, BindingProperties> bindings = context.getBean(BindingServiceProperties.class).getBindings();

        BindingProperties entradaTempoReal = bindings.get(BINDING_TEMPO_REAL);
        assertThat(entradaTempoReal.getDestination()).isEqualTo(NOTIFICACAO_CRIADA);
        // Sem grupo = grupo anônimo: cada réplica recebe todos os avisos (fan-out)
        assertThat(entradaTempoReal.getGroup()).isNull();
        assertThat(entradaTempoReal.getConsumer().getMaxAttempts()).isEqualTo(1);
        // Nenhuma propriedade do binder Kafka (DLT) neste binding: ele nem aceita DLT em grupo anônimo
        assertThat(context.getEnvironment().getProperty(
                "spring.cloud.stream.kafka.bindings." + BINDING_TEMPO_REAL + ".consumer.enable-dlq")).isNull();

        assertThat(bindings.get("notificacaoCriada-out-0").getDestination()).isEqualTo(NOTIFICACAO_CRIADA);
    }

    @Test
    void avisoDeNotificacaoCriadaChegaAoEntregador() {
        entrada.send(MessageBuilder.withPayload(
                "{\"id\":7,\"perfil\":\"RESPONSAVEL\",\"referenciaId\":12,\"campoNovo\":true}"
                        .getBytes(StandardCharsets.UTF_8)).build(), NOTIFICACAO_CRIADA);

        verify(entregador).entregar(new NotificacaoCriadaMensagem(7L, Perfil.RESPONSAVEL, 12L));
        assertThat(erros).isEmpty();
    }

    @Test
    void avisoInvalidoNaoERepetido() {
        entrada.send(MessageBuilder.withPayload("{ quebrado".getBytes(StandardCharsets.UTF_8)).build(),
                NOTIFICACAO_CRIADA);

        verify(entregador, times(0)).entregar(any());
        assertThat(erros).hasSize(1);
    }
}
