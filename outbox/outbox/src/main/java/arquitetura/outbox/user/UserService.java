package arquitetura.outbox.user;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import arquitetura.outbox.outbox.OutboxUser;
import arquitetura.outbox.outbox.OutboxUserRepository;
import arquitetura.outbox.outbox.TransitionType;
import tools.jackson.databind.json.JsonMapper;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final OutboxUserRepository outboxUserRepository;
	private final JsonMapper jsonMapper;

	public UserService(UserRepository userRepository, OutboxUserRepository outboxUserRepository,
			JsonMapper jsonMapper) {
		this.userRepository = userRepository;
		this.outboxUserRepository = outboxUserRepository;
		this.jsonMapper = jsonMapper;
	}

	@Transactional
	public UserResponse create(UserRequest request) {
		User user = userRepository.save(new User(request.name(), request.phone()));
		registerEvent(user, TransitionType.USER_CREATED);
		return UserResponse.from(user);
	}

	@Transactional(readOnly = true)
	public List<UserResponse> findAll() {
		return userRepository.findAllByDeletedAtIsNull().stream().map(UserResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public UserResponse findById(Long id) {
		return UserResponse.from(findActive(id));
	}

	@Transactional
	public UserResponse update(Long id, UserRequest request) {
		User user = findActive(id);
		user.update(request.name(), request.phone());
		registerEvent(user, TransitionType.USER_UPDATED);
		return UserResponse.from(user);
	}

	@Transactional
	public void delete(Long id) {
		User user = findActive(id);
		user.delete();
		registerEvent(user, TransitionType.USER_DELETED);
	}

	private User findActive(Long id) {
		return userRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new UserNotFoundException(id));
	}

	// Grava o evento na mesma transação da alteração em users: ou os dois são persistidos, ou nenhum.
	private void registerEvent(User user, TransitionType transitionType) {
		String payload = jsonMapper.writeValueAsString(UserResponse.from(user));
		outboxUserRepository.save(new OutboxUser(user, transitionType, payload));
	}

}
