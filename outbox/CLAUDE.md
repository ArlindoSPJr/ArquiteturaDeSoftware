# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Propósito do repositório

Repositório de estudo do padrão **Transactional Outbox**. Todo o progresso do estudo (código, anotações, diagramas) é salvo aqui. O usuário escreve em português — responda e documente em português.

## Estado atual

- `docs/diagrama.png` — arquitetura-alvo (descrita abaixo).
- Dois projetos Maven independentes (**Spring Boot 4.1.2 / Java 21**), cada um com seu `mvnw`:
  - `outbox/` — **User service** + Outbox worker (pacote `arquitetura.outbox`, porta 8080, banco `outbox`).
  - `notification/` — **Notification service** (pacote `arquitetura.notification`, porta 8081, banco `notification`).
- Os serviços não compartilham código: o contrato é só o RabbitMQ (exchange `user.events`, routing keys, payload JSON, `messageId`).

### User service (`outbox/`)

- Pacote `user`: CRUD em `/users` (`UserController` → `UserService` → `UserRepository`). DTOs `UserRequest`/`UserResponse` (records); `UserNotFoundException` vira 404. `User` tem `name`, `email` (`@NotBlank @Email` na API; coluna nullable no banco porque o `ddl-auto: update` não cria NOT NULL em tabela com linhas), `phone`.
- Pacote `outbox`: entidade `OutboxUser` (tabela `outbox_user`: `id` UUID, `user_id` FK → `users.id`, `transition_type`, `payload` jsonb, `status`) e enums `TransitionType` (`USER_CREATED`/`USER_UPDATED`/`USER_DELETED`) e `OutboxStatus` (`PENDING`/`PROCESSED`).
- Cada escrita no `UserService` (create/update/delete) grava o evento em `outbox_user` **no mesmo `@Transactional`**, com status `PENDING` e o `UserResponse` serializado como payload.
- **Soft delete** (decisão do usuário): `DELETE` só preenche `users.deleted_at`, porque a FK de `outbox_user` impediria apagar a linha. Consultas de usuário filtram `deleted_at is null` (`findAllByDeletedAtIsNull`, `findByIdAndDeletedAtIsNull`).
- Pacote `worker`: `OutboxWorker` faz polling com `@Scheduled(fixedDelay = 1000)` + `@Transactional` (`@EnableScheduling` em `worker/SchedulingConfig`, desligável com `outbox.worker.scheduling-enabled=false`). Busca até 50 eventos via `OutboxUserRepository.findLastUserPending` (query nativa: `status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED` — mais antigo primeiro, como uma fila). Para **cada** evento: publica na exchange, espera o *publisher confirm* (`publisher-confirm-type: correlated`, `CorrelationData`) e só então chama `markProcessed()` (`status = PROCESSED`, `processed_at`). Falha em qualquer evento → exceção → rollback do lote inteiro → tudo é tentado de novo no próximo ciclo (at-least-once).
- `worker/RabbitConfig`: declara **só** a topic exchange (nome em `outbox.rabbit.exchange`, padrão `user.events`) (quem publica é dono da exchange; filas e bindings são de quem consome). Routing keys vêm de `TransitionType.getRoutingKey()` (`user.created`/`user.updated`/`user.deleted`). Mensagem sem binding é descartada pelo broker, e o worker ainda recebe o confirm e marca `PROCESSED` — então, se o Notification service nunca tiver subido (fila inexistente), eventos `user.created` se perdem (ainda sem `mandatory`/returns). Mensagem: corpo = payload JSON, `messageId` = id do evento na outbox (chave de idempotência do consumidor), entrega persistente.
- O worker fica **dentro do User service** (decisão discutida com o usuário): a tabela outbox é do banco do User service, e um serviço separado lendo esse banco quebraria o "um banco por serviço". A fila `sendNotification` e seu binding (`user.created`) são declarados pelo **Notification service** (`notification/.../config/RabbitConfig`), que também declara a exchange com os mesmos parâmetros (declaração idempotente).

### Notification service (`notification/`)

- `config/RabbitConfig`: declara a exchange `user.events`, a fila durável `sendNotification` (nome mantido genérico de propósito: pode receber outras ações no futuro) e o binding `user.created`.
- `messaging/UserEventsListener`: `@RabbitListener` na fila; escolhe a ação pela routing key recebida (só `user.created` é tratada; outras são logadas e confirmadas). Lê o payload como `UserCreatedEvent(id, name, email)`. Sem `messageId` ou sem e-mail → `AmqpRejectAndDontRequeueException` (descarta sem retry).
- **Idempotent Consumer / Inbox**: tabela `notification` (`event_id` UUID PK = `messageId` = id do evento na outbox, `user_id`, `type` = `WELCOME`, `recipient`, `sent_at`). `NotificationService.sendWelcome`, num `@Transactional`: se o `event_id` já existe → ignora; senão `saveAndFlush` (a PK barra duplicata concorrente antes do envio) → envia o e-mail (`JavaMailSender`) → commit. Falha no envio → rollback + exceção → retry. SMTP não é transacional: e-mail enviado + commit falhando = duplicata rara (at-least-once).
- Retry do listener (`application.yaml`): 3 tentativas com backoff (1 s, ×2) — `max-retries: 2` (1ª + 2 novas; no Boot 4 `max-attempts` foi removida e é ignorada); esgotadas, a mensagem é rejeitada sem requeue e **descartada — ainda não há DLQ**.
- E-mail local vai para o **Mailpit** (SMTP `localhost:1025`, interface em http://localhost:8025). Remetente em `notification.mail.from`.

### Comum aos dois
- Esquema gerado pelo Hibernate (`ddl-auto: update`) — não há migrations (Flyway/Liquibase).
- Spring Boot 4 usa **Jackson 3**: o mapper é `tools.jackson.databind.json.JsonMapper`, não `com.fasterxml...ObjectMapper`. `@AutoConfigureMockMvc` fica em `org.springframework.boot.webmvc.test.autoconfigure`.

Infraestrutura local em `docker-compose.yml` (raiz do repositório): PostgreSQL 17, RabbitMQ 4 com painel de administração e Mailpit. Postgres: bancos `outbox` e `notification` (este criado por `docker/postgres/init/01-create-notification-db.sql`, que **só roda em volume novo** — num volume existente, criar à mão com `docker compose exec postgres psql -U root -d outbox -c "CREATE DATABASE notification"`), usuário/senha `root`/`root`, exposto na porta **5433** do host (a 5432 é de um PostgreSQL instalado no Windows; dentro da rede do compose continua 5432). RabbitMQ: usuário/senha `outbox`/`outbox`. O serviço `app` (imagem Java, `outbox/Dockerfile`) fica no perfil opcional `app`. No dia a dia a aplicação roda fora do Docker (mvnw/IDE), e o `application.yaml` aponta para `localhost`. No container, host e URL são sobrescritos por `SPRING_DATASOURCE_URL` e `SPRING_RABBITMQ_HOST`. O Notification service ainda não tem Dockerfile/serviço no compose.

**Banco de dados: PostgreSQL** (decisão do usuário). O `docs/diagrama.png` ainda mostra SQL Server — ignore isso; vale o PostgreSQL.

## Comandos

Infraestrutura (na raiz do repositório):

```bash
docker compose up -d                 # Postgres (5433) + RabbitMQ (5672, painel em http://localhost:15672) + Mailpit (1025, http://localhost:8025)
docker compose --profile app up -d --build   # também builda e sobe a aplicação Java (8080)
docker compose down                  # para tudo (acrescente -v para apagar os volumes)
```

Aplicações: executar a partir de `outbox/` ou `notification/` (no Windows/PowerShell use `.\mvnw.cmd`):

```bash
./mvnw clean package                                   # build + testes
./mvnw test                                            # todos os testes
./mvnw test -Dtest=UserControllerTests                 # uma classe de teste
./mvnw test -Dtest=UserControllerTests#deleteHidesUserAndRegistersEvent # um único método
./mvnw spring-boot:run                                 # outbox/: http://localhost:8080/users | notification/: porta 8081
```

Os testes são `@SpringBootTest` contra o Postgres real do compose (sem banco em memória/Testcontainers), então exigem `docker compose up -d` rodando antes. Os testes são `@Transactional`, então cada teste faz rollback e não deixa dados no banco. O rollback **não desfaz mensagens no RabbitMQ**, por isso `OutboxWorkerTests` publica numa exchange só de teste (`outbox.rabbit.exchange=user.events.test`) — publicando na `user.events` real, a fila `sendNotification` receberia eventos de usuários desfeitos e o Notification service mandaria e-mail para eles. O teste cria a própria fila temporária (nome gerado pelo broker, exclusiva, auto-delete — o RabbitMQ 4 recusa filas não duráveis e não exclusivas) ligada a `user.created` nessa exchange, e desliga o agendamento (`outbox.worker.scheduling-enabled=false`), senão o worker agendado publicaria eventos reais já commitados na exchange de teste e os marcaria `PROCESSED`. Nos outros testes do User service o worker agendado continua rodando e publica (na exchange real) eventos `PENDING` já commitados no banco de dev.

Nos testes do Notification service o listener fica desligado (`spring.rabbitmq.listener.simple.auto-startup=false`, para não consumir a fila real) e o `JavaMailSender` é `@MockitoBean`.

## Arquitetura-alvo (`docs/diagrama.png`)

Cenário: registro de usuário com envio de e-mail de boas-vindas.

1. **Cliente** chama o **User service** para registrar um usuário.
2. O User service grava, **na mesma transação do PostgreSQL**, a linha em `users` e um evento pendente na outbox (no código, tabela `outbox_user`). Esse é o ponto central do padrão: nunca publicar no broker diretamente dentro do fluxo de registro — o evento só existe se o usuário existir (sem dual-write).
3. O **Outbox worker** (processo separado) lê os eventos pendentes da outbox, publica no **RabbitMQ** e marca o evento como processado.
4. O **Notification service** consome a mensagem do RabbitMQ e envia o e-mail.

Implicações de projeto a respeitar ao implementar:
- A entrega é *at-least-once*: o worker pode publicar o mesmo evento mais de uma vez (falha entre publicar e marcar como processado), então o Notification service deve ser idempotente (ex.: deduplicar pelo id do evento).
- O User service não depende do RabbitMQ; só o worker e o Notification service falam com o broker.
