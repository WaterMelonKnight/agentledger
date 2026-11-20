package dev.agentledger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One immutable fact about what an agent did: a tool call, a decision, or a human
 * approval. Payloads are represented by hashes, never by the raw content, so a
 * ledger can be shared without leaking prompts, arguments or tool output.
 */
public record LedgerEntry(
    long seq,
    String runId,
    Instant at,
    Kind kind,
    String name,
    Status status,
    long durationMs,
    String argsHash,
    String resultHash,
    Map<String, String> meta,
    String prevHash,
    String hash) {

  public enum Kind {
    TOOL_CALL,
    DECISION,
    APPROVAL
  }

  public enum Status {
    OK,
    FAILED,
    PENDING
  }

  /** Field order here is the canonical form used for hashing. Do not reorder. */
  Map<String, Object> canonical() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("seq", seq);
    out.put("runId", runId);
    out.put("at", at.toString());
    out.put("kind", kind.name());
    out.put("name", name);
    out.put("status", status.name());
    out.put("durationMs", durationMs);
    out.put("argsHash", argsHash);
    out.put("resultHash", resultHash);
    out.put("meta", new LinkedHashMap<>(meta));
    out.put("prevHash", prevHash);
    return out;
  }

  public LedgerEntry withHash(String computed) {
    return new LedgerEntry(
        seq, runId, at, kind, name, status, durationMs, argsHash, resultHash, meta, prevHash,
        computed);
  }
}
