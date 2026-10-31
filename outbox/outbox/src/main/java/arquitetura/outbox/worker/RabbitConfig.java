package arquitetura.outbox.worker;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import arquitetura.outbox.outbox.TransitionType;

@Configuration
public class RabbitConfig {

	public static final String USER_EVENTS_EXCHANGE = "user.events";

	public static final String SEND_NOTIFICATION_QUEUE = "sendNotification";

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
			.with(TransitionType.USER_CREATED.getRoutingKey());
	}

}
