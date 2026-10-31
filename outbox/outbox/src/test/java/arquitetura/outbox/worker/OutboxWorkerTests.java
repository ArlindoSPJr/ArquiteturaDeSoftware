package arquitetura.outbox.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
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

// Roda contra o Postgres e o RabbitMQ do docker-compose; @Transactional desfaz as alterações no banco.
// A mensagem no RabbitMQ não é desfeita pelo rollback, então o worker publica numa exchange só de teste
// (senão a fila real sendNotification receberia eventos de usuários que não existem). O agendamento fica
// desligado: o worker agendado publicaria eventos reais, já commitados, nessa exchange de teste.
@SpringBootTest(properties = { "outbox.rabbit.exchange=" + OutboxWorkerTests.TEST_EXCHANGE,
		"outbox.worker.scheduling-enabled=false" })
@Transactional
class OutboxWorkerTests {

	static final String TEST_EXCHANGE = "user.events.test";

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
		// Fila temporária do próprio teste, ligada a user.created: o User service não declara mais filas
		// (elas são do consumidor), e o teste não pode depender do Notification service estar rodando.
		String queue = declareTestQueue(TransitionType.USER_CREATED.getRoutingKey());
		try {
			Long id = userService.create(new UserRequest("Lucia", "lucia@example.com", "11222220000")).id();
			userService.update(id, new UserRequest("Lucia Alves", "lucia@example.com", "11222220000"));

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
			GetResponse message = take(queue, created.getId().toString());
			assertThat(message).isNotNull();
			assertThat(message.getProps().getContentType()).isEqualTo("application/json");
			assertThat(jsonMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8)).get("email").asString())
				.isEqualTo("lucia@example.com");
		}
		finally {
			rabbitTemplate.execute(channel -> channel.queueDelete(queue));
		}
	}

	// Cria uma fila com nome gerado pelo broker, exclusiva (presa à conexão, que o CachingConnectionFactory
	// compartilha) e auto-delete, ligada à exchange com a routing key. O RabbitMQ 4 não aceita mais filas
	// não duráveis e não exclusivas (feature transient_nonexcl_queues).
	private String declareTestQueue(String routingKey) {
		return rabbitTemplate.execute(channel -> {
			String name = channel.queueDeclare("", false, true, true, null).getQueue();
			channel.queueBind(name, TEST_EXCHANGE, routingKey);
			return name;
		});
	}

	// Lê da fila (com ack automático) até achar a mensagem com o messageId informado. As outras mensagens
	// (ex.: publicadas pelo worker agendado) são descartadas junto com a fila, que é só do teste.
	private GetResponse take(String queue, String messageId) {
		return rabbitTemplate.execute(channel -> {
			long deadline = System.currentTimeMillis() + 5000;
			while (System.currentTimeMillis() < deadline) {
				GetResponse response = channel.basicGet(queue, true);
				if (response == null) {
					Thread.sleep(100);
				}
				else if (messageId.equals(response.getProps().getMessageId())) {
					return response;
				}
			}
			return null;
		});
	}

}
