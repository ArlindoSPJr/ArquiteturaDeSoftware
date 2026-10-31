# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Propósito do repositório

Repositório de estudo do padrão **Transactional Outbox**. Todo o progresso do estudo (código, anotações, diagramas) é salvo aqui. O usuário escreve em português — responda e documente em português.

## Estado atual

- `docs/diagrama.png` — arquitetura-alvo (descrita abaixo).
- `outbox/` — aplicação **Spring Boot 4.1.2 / Java 21 / Maven** (pacote base `arquitetura.outbox`, classe `OutboxApplication`). Starters: `webmvc`, `data-jpa`, `validation`, `amqp` (RabbitMQ), `mail`; driver **PostgreSQL**. O `application.yaml` configura datasource e RabbitMQ apontando para `localhost`.
- Por enquanto é um único módulo/aplicação; os três componentes do diagrama (User service, Outbox worker, Notification service) ainda não foram separados.

Implementado até agora — só o **lado de escrita do User service** (ainda sem worker, sem RabbitMQ, sem e-mail):

- Pacote `user`: CRUD em `/users` (`UserController` → `UserService` → `UserRepository`). DTOs `UserRequest`/`UserResponse` (records); `UserNotFoundException` vira 404.
- Pacote `outbox`: entidade `OutboxUser` (tabela `outbox_user`: `id` UUID, `user_id` FK → `users.id`, `transition_type`, `payload` jsonb, `status`) e enums `TransitionType` (`USER_CREATED`/`USER_UPDATED`/`USER_DELETED`) e `OutboxStatus` (`PENDING`/`PROCESSED`).
- Cada escrita no `UserService` (create/update/delete) grava o evento em `outbox_user` **no mesmo `@Transactional`**, com status `PENDING` e o `UserResponse` serializado como payload.
- **Soft delete** (decisão do usuário): `DELETE` só preenche `users.deleted_at`, porque a FK de `outbox_user` impediria apagar a linha. Consultas de usuário filtram `deleted_at is null` (`findAllByDeletedAtIsNull`, `findByIdAndDeletedAtIsNull`).
- Pacote `worker`: `OutboxWorker` faz polling com `@Scheduled(fixedDelay = 1000)` + `@Transactional` (`@EnableScheduling` em `OutboxApplication`). Busca até 50 eventos via `OutboxUserRepository.findLastUserPending` (query nativa: `status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED` — mais antigo primeiro, como uma fila). Para **cada** evento: publica na exchange, espera o *publisher confirm* (`publisher-confirm-type: correlated`, `CorrelationData`) e só então chama `markProcessed()` (`status = PROCESSED`, `processed_at`). Falha em qualquer evento → exceção → rollback do lote inteiro → tudo é tentado de novo no próximo ciclo (at-least-once).
- `worker/RabbitConfig`: topic exchange `user.events`; fila durável `sendNotification` ligada só à routing key `user.created`. Routing keys vêm de `TransitionType.getRoutingKey()` (`user.created`/`user.updated`/`user.deleted`). `user.updated`/`user.deleted` são publicados mas não têm fila ligada (o broker descarta). Mensagem: corpo = payload JSON, `messageId` = id do evento na outbox (chave de idempotência do futuro consumidor), entrega persistente.
- O worker fica **dentro do User service** (decisão discutida com o usuário): a tabela outbox é do banco do User service, e um serviço separado lendo esse banco quebraria o "um banco por serviço". A fila `sendNotification` está declarada aqui só até o Notification service existir — depois a declaração deve ir para ele.
- Esquema gerado pelo Hibernate (`ddl-auto: update`) — não há migrations (Flyway/Liquibase).
- Spring Boot 4 usa **Jackson 3**: o mapper é `tools.jackson.databind.json.JsonMapper`, não `com.fasterxml...ObjectMapper`. `@AutoConfigureMockMvc` fica em `org.springframework.boot.webmvc.test.autoconfigure`.

Infraestrutura local em `docker-compose.yml` (raiz do repositório): PostgreSQL 17 e RabbitMQ 4 com painel de administração. Postgres: banco `outbox`, usuário/senha `root`/`root`, exposto na porta **5433** do host (a 5432 é de um PostgreSQL instalado no Windows; dentro da rede do compose continua 5432). RabbitMQ: usuário/senha `outbox`/`outbox`. O serviço `app` (imagem Java, `outbox/Dockerfile`) fica no perfil opcional `app`. No dia a dia a aplicação roda fora do Docker (mvnw/IDE), e o `application.yaml` aponta para `localhost`. No container, host e URL são sobrescritos por `SPRING_DATASOURCE_URL` e `SPRING_RABBITMQ_HOST`. SMTP ainda não está configurado.

**Banco de dados: PostgreSQL** (decisão do usuário). O `docs/diagrama.png` ainda mostra SQL Server — ignore isso; vale o PostgreSQL.

## Comandos

Infraestrutura (na raiz do repositório):

```bash
docker compose up -d                 # Postgres (5433) + RabbitMQ (5672, painel em http://localhost:15672)
docker compose --profile app up -d --build   # também builda e sobe a aplicação Java (8080)
docker compose down                  # para tudo (acrescente -v para apagar os volumes)
```

Aplicação: executar a partir de `outbox/` (no Windows/PowerShell use `.\mvnw.cmd`):

```bash
./mvnw clean package                                   # build + testes
./mvnw test                                            # todos os testes
./mvnw test -Dtest=UserControllerTests                 # uma classe de teste
./mvnw test -Dtest=UserControllerTests#deleteHidesUserAndRegistersEvent # um único método
./mvnw spring-boot:run                                 # sobe a aplicação (http://localhost:8080/users)
```

Os testes são `@SpringBootTest` contra o Postgres real do compose (sem banco em memória/Testcontainers), então exigem `docker compose up -d` rodando antes. Os testes são `@Transactional`, então cada teste faz rollback e não deixa dados no banco. O rollback **não desfaz mensagens no RabbitMQ**: `OutboxWorkerTests` consome da fila `sendNotification` a mensagem que publicou (e devolve as outras com nack + requeue) para não deixar mensagens de usuários inexistentes. O `OutboxWorker` agendado também roda durante os testes e publica eventos `PENDING` já commitados no banco de dev.

## Arquitetura-alvo (`docs/diagrama.png`)

Cenário: registro de usuário com envio de e-mail de boas-vindas.

1. **Cliente** chama o **User service** para registrar um usuário.
2. O User service grava, **na mesma transação do PostgreSQL**, a linha em `users` e um evento pendente na outbox (no código, tabela `outbox_user`). Esse é o ponto central do padrão: nunca publicar no broker diretamente dentro do fluxo de registro — o evento só existe se o usuário existir (sem dual-write).
3. O **Outbox worker** (processo separado) lê os eventos pendentes da outbox, publica no **RabbitMQ** e marca o evento como processado.
4. O **Notification service** consome a mensagem do RabbitMQ e envia o e-mail.

Implicações de projeto a respeitar ao implementar:
- A entrega é *at-least-once*: o worker pode publicar o mesmo evento mais de uma vez (falha entre publicar e marcar como processado), então o Notification service deve ser idempotente (ex.: deduplicar pelo id do evento).
- O User service não depende do RabbitMQ; só o worker e o Notification service falam com o broker.
