package arquitetura.outbox.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import arquitetura.outbox.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_user")
public class OutboxUser {

	// UUID gerado na aplicação: servirá de chave de idempotência para o consumidor.
	@Id
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name = "transition_type", nullable = false)
	private TransitionType transitionType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OutboxStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	protected OutboxUser() {
	}

	public OutboxUser(User user, TransitionType transitionType, String payload) {
		this.id = UUID.randomUUID();
		this.user = user;
		this.transitionType = transitionType;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
		this.createdAt = Instant.now();
	}

	public void markProcessed() {
		this.status = OutboxStatus.PROCESSED;
		this.processedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public TransitionType getTransitionType() {
		return transitionType;
	}

	public String getPayload() {
		return payload;
	}

	public OutboxStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

}
