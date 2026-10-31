package arquitetura.outbox.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxUserRepository extends JpaRepository<OutboxUser, UUID> {

	List<OutboxUser> findAllByUserId(Long userId);

	// FOR UPDATE trava as linhas lidas até o fim da transação; SKIP LOCKED faz outra instância
	// do worker pular as linhas já travadas em vez de esperar por elas. Exige transação ativa.
	@Query(value = """
			SELECT *
			FROM outbox_user
			WHERE status = 'PENDING'
			ORDER BY created_at ASC
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxUser> findLastUserPending(@Param("limit") int limit);

}
