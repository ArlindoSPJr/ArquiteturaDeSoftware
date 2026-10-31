package arquitetura.notification.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Notificação já enviada (Idempotent Consumer / Inbox).
 *
 * A PK é o id do evento na outbox do User service (vem no messageId da mensagem). Como a entrega do
 * RabbitMQ é at-least-once, a mesma mensagem pode chegar mais de uma vez: se já existe linha com esse
 * event_id, o e-mail já foi enviado e a mensagem é ignorada.
 */
@Entity
@Table(name = "notification")
public class Notification {

	@Id
	@Column(name = "event_id")
	private UUID eventId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(nullable = false)
	private String recipient;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	protected Notification() {
	}

	public Notification(UUID eventId, Long userId, NotificationType type, String recipient) {
		this.eventId = eventId;
		this.userId = userId;
		this.type = type;
		this.recipient = recipient;
		this.sentAt = Instant.now();
	}

	public UUID getEventId() {
		return eventId;
	}

	public Long getUserId() {
		return userId;
	}

	public NotificationType getType() {
		return type;
	}

	public String getRecipient() {
		return recipient;
	}

	public Instant getSentAt() {
		return sentAt;
	}

}
