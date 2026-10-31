package arquitetura.outbox.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.rabbitmq.client.GetResponse;

import arquitetura.outbox.outbox.OutboxStatus;
import arquitetura.outbox.outbox.OutboxUser;
import arquitetura.outbox.outbox.OutboxUserRepository;
import arquitetura.outbox.outbox.TransitionType;
import arquitetura.outbox.user.UserRequest;
import arquitetura.outbox.user.UserService;
import tools.jackson.databind.json.JsonMapper;

// Roda contra o Postgres e o RabbitMQ do docker-compose; @Transactional desfaz as alterações no banco
// (a mensagem no RabbitMQ não é desfeita, por isso o teste a consome da fila).
@SpringBootTest
@Transactional
class OutboxWorkerTests {

	@Autowired
	private UserService userService;

	@Autowired
	private OutboxWorker outboxWorker;

	@Autowired
	private OutboxUserRepository outboxUserRepository;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void publishPendingPublishesEventsAndMarksThemProcessed() throws Exception {
		Long id = userService.create(new UserRequest("Lucia", "11222220000")).id();
		userService.update(id, new UserRequest("Lucia Alves", "11222220000"));

		outboxWorker.publishPending();

		List<OutboxUser> events = outboxUserRepository.findAllByUserId(id);
		assertThat(events).hasSize(2).allSatisfy(event -> {
			assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
			assertThat(event.getProcessedAt()).isNotNull();
		});

		OutboxUser created = events.stream()
			.filter(event -> event.getTransitionType() == TransitionType.USER_CREATED)
			.findFirst()
			.orElseThrow();
		GetResponse message = takeFromSendNotification(created.getId().toString());
		assertThat(message).isNotNull();
		assertThat(message.getProps().getContentType()).isEqualTo("application/json");
		assertThat(jsonMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8)).get("name").asString())
			.isEqualTo("Lucia");
	}

	// Procura na fila a mensagem com o messageId informado e a remove (ack); as demais voltam para a fila (nack com requeue).
	private GetResponse takeFromSendNotification(String messageId) {
		return rabbitTemplate.execute(channel -> {
			List<Long> others = new ArrayList<>();
			GetResponse found = null;
			long deadline = System.currentTimeMillis() + 5000;
			while (found == null && System.currentTimeMillis() < deadline) {
				GetResponse response = channel.basicGet(RabbitConfig.SEND_NOTIFICATION_QUEUE, false);
				if (response == null) {
					Thread.sleep(100);
				}
				else if (messageId.equals(response.getProps().getMessageId())) {
					channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
					found = response;
				}
				else {
					others.add(response.getEnvelope().getDeliveryTag());
				}
			}
			for (Long tag : others) {
				channel.basicNack(tag, false, true);
			}
			return found;
		});
	}

}
