package arquitetura.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Listener desligado para o teste não consumir as mensagens reais da fila sendNotification.
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class NotificationApplicationTests {

	@Test
	void contextLoads() {
	}

}
