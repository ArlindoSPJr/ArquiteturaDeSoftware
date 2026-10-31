package arquitetura.outbox.user;

import java.time.Instant;

public record UserResponse(Long id, String name, String phone, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getPhone(), user.getCreatedAt());
	}

}
