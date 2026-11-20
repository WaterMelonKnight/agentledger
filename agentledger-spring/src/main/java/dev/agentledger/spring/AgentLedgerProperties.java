package dev.agentledger.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentledger")
public class AgentLedgerProperties {

  /** Append-only JSON Lines file. Created if missing. */
  private String path = "./agent-ledger.jsonl";

  /** Optional fixed run id; a random one is used when absent. */
  private String runId;

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(String runId) {
    this.runId = runId;
  }
}
