package dev.agentledger.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agentledger")
public class AgentLedgerProperties {

  /** Append-only JSON Lines file. Created if missing. */
  private String path = "./agent-ledger.jsonl";

  /** Optional fixed run id; a random one is used when absent. */
  private String runId;

  /** Optional head anchor file; enables truncation detection. */
  private String headPath;

  /** Optional HMAC key for the head anchor. Prefer an environment variable. */
  private String key;

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

  public String getHeadPath() {
    return headPath;
  }

  public void setHeadPath(String headPath) {
    this.headPath = headPath;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }
}
