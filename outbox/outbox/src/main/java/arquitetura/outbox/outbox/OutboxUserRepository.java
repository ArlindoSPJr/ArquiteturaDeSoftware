package arquitetura.outbox.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxUserRepository extends JpaRepository<OutboxUser, UUID> {

	List<OutboxUser> findAllByUserId(Long userId);

}
