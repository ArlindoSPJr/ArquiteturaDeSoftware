package arquitetura.outbox.user;

import jakarta.validation.constraints.NotBlank;

public record UserRequest(@NotBlank String name, String phone) {
}
