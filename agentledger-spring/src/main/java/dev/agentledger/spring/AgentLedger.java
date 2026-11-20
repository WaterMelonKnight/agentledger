package dev.agentledger.spring;

import dev.agentledger.LedgerEntry;
import dev.agentledger.LedgerWriter;
import java.util.Map;

/** App-facing facade: record a fact, or delegate to the underlying writer. */
public class AgentLedger {

  private final LedgerWriter writer;

  public AgentLedger(LedgerWriter writer) {
    this.writer = writer;
  }

  public LedgerEntry toolCall(String name, long durationMs, String args, String result) {
    return writer.append(
        LedgerEntry.Kind.TOOL_CALL, name, LedgerEntry.Status.OK, durationMs,
        LedgerWriter.hashPayload(args), LedgerWriter.hashPayload(result), Map.of());
  }

  public LedgerEntry decision(String name, Map<String, String> meta) {
    return writer.append(
        LedgerEntry.Kind.DECISION, name, LedgerEntry.Status.OK, 0, null, null, meta);
  }

  public LedgerEntry approval(String name, boolean approved, String approver) {
    return writer.append(
        LedgerEntry.Kind.APPROVAL, name, approved ? LedgerEntry.Status.OK : LedgerEntry.Status.FAILED,
        0, null, null, Map.of("approver", approver));
  }

  public LedgerWriter writer() {
    return writer;
  }
}
