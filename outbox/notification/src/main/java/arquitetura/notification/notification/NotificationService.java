package arquitetura.notification.notification;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationRepository notificationRepository;

	private final JavaMailSender mailSender;

	private final String from;

	public NotificationService(NotificationRepository notificationRepository, JavaMailSender mailSender,
			@Value("${notification.mail.from}") String from) {
		this.notificationRepository = notificationRepository;
		this.mailSender = mailSender;
		this.from = from;
	}

	/**
	 * Envia o e-mail de boas-vindas uma única vez por evento.
	 *
	 * Ordem dentro da transação: registra a notificação (flush, para a PK barrar duplicata concorrente
	 * antes do envio) → envia o e-mail → commit. Se o envio falhar, o rollback desfaz o registro e a
	 * exceção faz a mensagem ser tentada de novo. SMTP não é transacional: se o e-mail sair e o commit
	 * falhar logo depois, a nova tentativa envia de novo — at-least-once, com duplicata rara.
	 *
	 * @return true se enviou; false se o evento já tinha sido processado
	 */
	@Transactional
	public boolean sendWelcome(UUID eventId, Long userId, String name, String email) {
		if (notificationRepository.existsById(eventId)) {
			log.info("Evento {} já processado, ignorando mensagem duplicada", eventId);
			return false;
		}
		notificationRepository.saveAndFlush(new Notification(eventId, userId, NotificationType.WELCOME, email));

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("Bem-vindo(a)!");
		message.setText("Olá, " + name + "! Seu cadastro foi realizado com sucesso.");
		mailSender.send(message);

		log.info("E-mail de boas-vindas enviado para {} (evento {})", email, eventId);
		return true;
	}

}
