package arquitetura.outbox.user;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
		UserResponse user = userService.create(request);
		return ResponseEntity.created(URI.create("/users/" + user.id())).body(user);
	}

	@GetMapping
	public List<UserResponse> findAll() {
		return userService.findAll();
	}

	@GetMapping("/{id}")
	public UserResponse findById(@PathVariable Long id) {
		return userService.findById(id);
	}

	@PutMapping("/{id}")
	public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
		return userService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		userService.delete(id);
		return ResponseEntity.noContent().build();
	}

}
