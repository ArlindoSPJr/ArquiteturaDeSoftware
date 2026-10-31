package arquitetura.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do RabbitMQ do lado consumidor.
 *
 * A exchange é do User service (quem publica), mas também é declarada aqui para o binding
 * funcionar independentemente de qual serviço sobe primeiro. A declaração é idempotente:
 * os parâmetros (topic, durável) precisam ser iguais aos do User service, senão o broker
 * recusa com PRECONDITION_FAILED.
 *
 * A fila e o binding são deste serviço: é ele quem quer receber os eventos user.created.
 */
@Configuration
public class RabbitConfig {

	public static final String USER_EVENTS_EXCHANGE = "user.events";

	public static final String SEND_NOTIFICATION_QUEUE = "sendNotification";

	public static final String USER_CREATED_ROUTING_KEY = "user.created";

	@Bean
	TopicExchange userEventsExchange() {
		return new TopicExchange(USER_EVENTS_EXCHANGE);
	}

	@Bean
	Queue sendNotificationQueue() {
		return QueueBuilder.durable(SEND_NOTIFICATION_QUEUE).build();
	}

	@Bean
	Binding sendNotificationBinding(Queue sendNotificationQueue, TopicExchange userEventsExchange) {
		return BindingBuilder.bind(sendNotificationQueue)
			.to(userEventsExchange)
			.with(USER_CREATED_ROUTING_KEY);
	}

}
