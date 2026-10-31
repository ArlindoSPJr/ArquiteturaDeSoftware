package arquitetura.outbox.worker;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import arquitetura.outbox.outbox.OutboxUser;
import arquitetura.outbox.outbox.OutboxUserRepository;

@Component
public class OutboxWorker {

	private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

	private static final int BATCH_SIZE = 50;

	private static final long CONFIRM_TIMEOUT_SECONDS = 5;

	private final OutboxUserRepository outboxUserRepository;
	private final RabbitTemplate rabbitTemplate;
	private final String exchange;

	public OutboxWorker(OutboxUserRepository outboxUserRepository, RabbitTemplate rabbitTemplate,
			@Value(RabbitConfig.EXCHANGE_PROPERTY) String exchange) {
		this.outboxUserRepository = outboxUserRepository;
		this.rabbitTemplate = rabbitTemplate;
		this.exchange = exchange;
	}

	// fixedDelay: o próximo ciclo começa 1 s depois que o anterior TERMINA, então nunca há dois ao mesmo tempo.
	// @Transactional mantém as linhas travadas pelo FOR UPDATE durante todo o ciclo.
	// Se qualquer publicação falhar, a exceção faz rollback: nada do lote é marcado e tudo volta a ser tentado
	// no próximo ciclo (inclusive o que já tinha sido publicado -> at-least-once).
	@Scheduled(fixedDelay = 1000)
	@Transactional
	public void publishPending() throws Exception {
		List<OutboxUser> events = outboxUserRepository.findLastUserPending(BATCH_SIZE);
		if (events.isEmpty()) {
			return;
		}

		for (OutboxUser event : events) {
			publish(event);
			event.markProcessed();
		}
		log.info("{} evento(s) publicado(s) e marcado(s) como PROCESSED", events.size());
	}

	private void publish(OutboxUser event) throws Exception {
		String routingKey = event.getTransitionType().getRoutingKey();
		Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
			.setContentType(MessageProperties.CONTENT_TYPE_JSON)
			.setMessageId(event.getId().toString())
			.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
			.build();

		CorrelationData correlation = new CorrelationData(event.getId().toString());
		rabbitTemplate.send(exchange, routingKey, message, correlation);

		// Publisher confirm: espera o broker dizer que recebeu (ack) antes de seguir.
		CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		if (!confirm.ack()) {
			throw new IllegalStateException(
					"Broker recusou o evento " + event.getId() + ": " + confirm.reason());
		}
		log.info("Evento {} publicado com routing key {}", event.getId(), routingKey);
	}

}
