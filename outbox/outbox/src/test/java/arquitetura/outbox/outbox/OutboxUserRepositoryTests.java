package arquitetura.outbox.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import arquitetura.outbox.user.UserRequest;
import arquitetura.outbox.user.UserService;

// Roda contra o Postgres do docker-compose; @Transactional desfaz as alterações ao final de cada teste.
@SpringBootTest
@Transactional
class OutboxUserRepositoryTests {

	@Autowired
	private UserService userService;

	@Autowired
	private OutboxUserRepository outboxUserRepository;

	@Test
	void findLastUserPendingReturnsPendingEventsOldestFirst() {
		Long id = userService.create(new UserRequest("Carla", "11444440000")).id();
		userService.update(id, new UserRequest("Carla Souza", "11444440000"));

		List<OutboxUser> events = outboxUserRepository.findLastUserPending(50);

		assertThat(events).extracting(OutboxUser::getTransitionType)
			.contains(TransitionType.USER_CREATED, TransitionType.USER_UPDATED);
		assertThat(events).allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING));
		assertThat(events).isSortedAccordingTo(Comparator.comparing(OutboxUser::getCreatedAt));
	}

	@Test
	void findLastUserPendingRespectsLimit() {
		Long id = userService.create(new UserRequest("Bruno", "11333330000")).id();
		userService.update(id, new UserRequest("Bruno Lima", "11333330000"));

		assertThat(outboxUserRepository.findLastUserPending(1)).hasSize(1);
	}

}
