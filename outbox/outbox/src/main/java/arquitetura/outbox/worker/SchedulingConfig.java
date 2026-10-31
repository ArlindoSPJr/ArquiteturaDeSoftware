package arquitetura.outbox.worker;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o polling agendado do OutboxWorker. Ligado por padrão; testes que precisam controlar quando o
 * worker roda (ex.: OutboxWorkerTests) desligam com outbox.worker.scheduling-enabled=false.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "outbox.worker.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

}
