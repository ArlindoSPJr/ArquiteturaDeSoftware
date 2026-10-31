package arquitetura.outbox.worker;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do RabbitMQ do lado publicador: só a exchange.
 *
 * Filas e bindings são de quem consome (ex.: o Notification service declara a fila
 * sendNotification ligada a user.created). O worker só publica na exchange com uma routing key
 * e não sabe quais filas existem.
 *
 * O nome vem de outbox.rabbit.exchange (padrão user.events). Os testes do worker usam outra exchange,
 * para que as mensagens de usuários desfeitos pelo rollback não cheguem às filas reais.
 */
@Configuration
public class RabbitConfig {

	public static final String EXCHANGE_PROPERTY = "${outbox.rabbit.exchange:user.events}";

	@Bean
	TopicExchange userEventsExchange(@Value(EXCHANGE_PROPERTY) String exchange) {
		return new TopicExchange(exchange);
	}

}
