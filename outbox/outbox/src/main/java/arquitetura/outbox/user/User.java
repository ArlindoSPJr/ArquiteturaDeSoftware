package arquitetura.outbox.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	private String phone;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	// Soft delete: a linha nunca é apagada, para não quebrar a FK de outbox_user.
	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected User() {
	}

	public User(String name, String phone) {
		this.name = name;
		this.phone = phone;
		this.createdAt = Instant.now();
	}

	public void update(String name, String phone) {
		this.name = name;
		this.phone = phone;
	}

	public void delete() {
		this.deletedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getPhone() {
		return phone;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

}
