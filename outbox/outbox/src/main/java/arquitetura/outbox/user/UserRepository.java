package arquitetura.outbox.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	List<User> findAllByDeletedAtIsNull();

	Optional<User> findByIdAndDeletedAtIsNull(Long id);

}
