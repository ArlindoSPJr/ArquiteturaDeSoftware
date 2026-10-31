package arquitetura.notification.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload do evento user.created publicado pelo User service (o UserResponse dele serializado).
 * Só os campos que este serviço usa; os demais são ignorados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserCreatedEvent(Long id, String name, String email) {

}
