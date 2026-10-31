package arquitetura.outbox.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import arquitetura.outbox.outbox.OutboxStatus;
import arquitetura.outbox.outbox.OutboxUser;
import arquitetura.outbox.outbox.OutboxUserRepository;
import arquitetura.outbox.outbox.TransitionType;
import tools.jackson.databind.json.JsonMapper;

// Roda contra o Postgres do docker-compose; @Transactional desfaz as alterações ao final de cada teste.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OutboxUserRepository outboxUserRepository;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void createRegistersUserAndPendingEvent() throws Exception {
		Long id = createUser("Maria", "maria@example.com", "11999990000");

		List<OutboxUser> events = outboxUserRepository.findAllByUserId(id);
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.getTransitionType()).isEqualTo(TransitionType.USER_CREATED);
			assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
			assertThat(event.getPayload()).contains("\"name\":\"Maria\"").contains("\"email\":\"maria@example.com\"");
		});
	}

	@Test
	void findByIdReturnsUser() throws Exception {
		Long id = createUser("João", "joao@example.com", "11888880000");

		mockMvc.perform(get("/users/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("João"))
			.andExpect(jsonPath("$.email").value("joao@example.com"))
			.andExpect(jsonPath("$.phone").value("11888880000"))
			.andExpect(jsonPath("$.createdAt").exists());
	}

	@Test
	void updateChangesUserAndRegistersEvent() throws Exception {
		Long id = createUser("Ana", "ana@example.com", "11777770000");

		mockMvc.perform(put("/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Ana Paula\",\"email\":\"ana@example.com\",\"phone\":\"11777771111\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Ana Paula"));

		assertThat(outboxUserRepository.findAllByUserId(id)).extracting(OutboxUser::getTransitionType)
			.containsExactlyInAnyOrder(TransitionType.USER_CREATED, TransitionType.USER_UPDATED);
	}

	@Test
	void deleteHidesUserAndRegistersEvent() throws Exception {
		Long id = createUser("Pedro", "pedro@example.com", "11666660000");

		mockMvc.perform(delete("/users/{id}", id)).andExpect(status().isNoContent());

		mockMvc.perform(get("/users/{id}", id)).andExpect(status().isNotFound());
		assertThat(outboxUserRepository.findAllByUserId(id)).extracting(OutboxUser::getTransitionType)
			.containsExactlyInAnyOrder(TransitionType.USER_CREATED, TransitionType.USER_DELETED);
	}

	@Test
	void createWithoutNameReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@example.com\",\"phone\":\"11555550000\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createWithInvalidEmailReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rui\",\"email\":\"invalido\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void findByIdOfMissingUserReturnsNotFound() throws Exception {
		mockMvc.perform(get("/users/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
	}

	private Long createUser(String name, String email, String phone) throws Exception {
		String body = mockMvc
			.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
				.content(jsonMapper.writeValueAsString(new UserRequest(name, email, phone))))
			.andExpect(status().isCreated())
			.andExpect(header().exists("Location"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		return jsonMapper.readValue(body, UserResponse.class).id();
	}

}
