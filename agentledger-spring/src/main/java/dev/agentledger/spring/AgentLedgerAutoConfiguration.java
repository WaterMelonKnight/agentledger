package dev.agentledger.spring;

import dev.agentledger.LedgerWriter;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AgentLedgerProperties.class)
@ConditionalOnProperty(prefix = "agentledger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentLedgerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LedgerWriter agentLedgerWriter(AgentLedgerProperties properties) {
    Path path = Path.of(properties.getPath());
    Path head = properties.getHeadPath() == null ? null : Path.of(properties.getHeadPath());
    String runId = properties.getRunId() == null
        ? "run-" + java.util.UUID.randomUUID()
        : properties.getRunId();
    return new LedgerWriter(path, runId, head, properties.getKey());
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentLedger agentLedger(LedgerWriter writer) {
    return new AgentLedger(writer);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentActionAspect agentActionAspect(AgentLedger ledger) {
    return new AgentActionAspect(ledger);
  }
}
