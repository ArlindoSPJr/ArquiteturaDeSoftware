-- Banco do Notification service ("um banco por serviço"; o banco outbox é do User service).
-- Scripts em /docker-entrypoint-initdb.d só rodam quando o volume do Postgres é criado vazio.
CREATE DATABASE notification;
