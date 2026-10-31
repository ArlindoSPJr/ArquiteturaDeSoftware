package arquitetura.notification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// Roda contra o Postgres do docker-compose (banco notification); @Transactional desfaz as alterações.
// O listener não é iniciado, para o teste não consumir as mensagens reais da fila sendNotification.
// O JavaMailSender é mock: nenhum e-mail sai.
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Transactional
class NotificationServiceTests {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationRepository notificationRepository;

	@MockitoBean
	private JavaMailSender mailSender;

	@Test
	void sendWelcomeSendsEmailAndRegistersNotification() {
		UUID eventId = UUID.randomUUID();

		boolean sent = notificationService.sendWelcome(eventId, 1L, "Maria", "maria@example.com");

		assertThat(sent).isTrue();
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		assertThat(captor.getValue().getTo()).containsExactly("maria@example.com");
		assertThat(captor.getValue().getText()).contains("Maria");
		assertThat(notificationRepository.findById(eventId)).hasValueSatisfying(notification -> {
			assertThat(notification.getType()).isEqualTo(NotificationType.WELCOME);
			assertThat(notification.getRecipient()).isEqualTo("maria@example.com");
		});
	}

	@Test
	void duplicateEventSendsEmailOnlyOnce() {
		UUID eventId = UUID.randomUUID();

		notificationService.sendWelcome(eventId, 1L, "Maria", "maria@example.com");
		boolean sentAgain = notificationService.sendWelcome(eventId, 1L, "Maria", "maria@example.com");

		assertThat(sentAgain).isFalse();
		verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
	}

	@Test
	void mailFailurePropagatesSoMessageIsRetried() {
		doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(SimpleMailMessage.class));

		assertThatThrownBy(() -> notificationService.sendWelcome(UUID.randomUUID(), 1L, "Maria", "maria@example.com"))
			.isInstanceOf(MailSendException.class);
	}

}
