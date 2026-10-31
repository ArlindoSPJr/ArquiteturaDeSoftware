package arquitetura.notification.messaging;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import arquitetura.notification.config.RabbitConfig;
import arquitetura.notification.notification.NotificationService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consome a fila sendNotification. A fila pode receber outras routing keys no futuro, então a ação é
 * escolhida pela routing key de cada mensagem.
 *
 * Retorno normal = ack. Exceção = a mensagem é tentada de novo (retry do listener, ver application.yaml).
 */
@Component
public class UserEventsListener {

	private static final Logger log = LoggerFactory.getLogger(UserEventsListener.class);

	private final NotificationService notificationService;

	private final JsonMapper jsonMapper;

	public UserEventsListener(NotificationService notificationService, JsonMapper jsonMapper) {
		this.notificationService = notificationService;
		this.jsonMapper = jsonMapper;
	}

	@RabbitListener(queues = RabbitConfig.SEND_NOTIFICATION_QUEUE)
	public void onMessage(Message message) {
		String routingKey = message.getMessageProperties().getReceivedRoutingKey();
		if (!RabbitConfig.USER_CREATED_ROUTING_KEY.equals(routingKey)) {
			log.warn("Routing key {} sem tratamento na fila {}, ignorando", routingKey,
					RabbitConfig.SEND_NOTIFICATION_QUEUE);
			return;
		}

		UUID eventId = eventId(message);
		UserCreatedEvent event = jsonMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8),
				UserCreatedEvent.class);
		if (event.email() == null || event.email().isBlank()) {
			throw new AmqpRejectAndDontRequeueException("Evento " + eventId + " sem e-mail do usuário");
		}
		notificationService.sendWelcome(eventId, event.id(), event.name(), event.email());
	}

	// O messageId é o id do evento na outbox: é a chave de idempotência. Sem ele não dá para deduplicar,
	// e tentar de novo não resolve, então a mensagem é rejeitada direto (sem retry/requeue).
	private UUID eventId(Message message) {
		String messageId = message.getMessageProperties().getMessageId();
		if (messageId == null) {
			throw new AmqpRejectAndDontRequeueException("Mensagem sem messageId");
		}
		return UUID.fromString(messageId);
	}

}
