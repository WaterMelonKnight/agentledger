package dev.agentledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LedgerTest {

  @TempDir Path dir;

  private Path ledger() {
    return dir.resolve("ledger.jsonl");
  }

  @Test
  void appendsAreChainedAndVerify() {
    LedgerWriter writer = new LedgerWriter(ledger(), "run-1");
    writer.append(LedgerEntry.Kind.TOOL_CALL, "get_weather", LedgerEntry.Status.OK, 12,
        LedgerWriter.hashPayload("{\"city\":\"sh\"}"), LedgerWriter.hashPayload("sunny"), Map.of());
    writer.append(LedgerEntry.Kind.DECISION, "approve_refund", LedgerEntry.Status.OK, 0,
        null, null, Map.of("confidence", "0.82"));

    LedgerVerifier.Result result = new LedgerVerifier().verify(ledger());
    assertThat(result.intact()).isTrue();
    assertThat(result.entries()).isEqualTo(2);
    assertThat(result.lastSeq()).isEqualTo(2);
  }

  @Test
  void editingAnEntryBreaksVerification() throws Exception {
    LedgerWriter writer = new LedgerWriter(ledger(), "run-2");
    writer.record(LedgerEntry.Kind.TOOL_CALL, "read_file", LedgerEntry.Status.OK);
    writer.record(LedgerEntry.Kind.TOOL_CALL, "write_file", LedgerEntry.Status.FAILED);

    List<String> lines = Files.readAllLines(ledger());
    Files.write(ledger(), List.of(lines.get(0), lines.get(1).replace("write_file", "delete_all")));

    LedgerVerifier.Result result = new LedgerVerifier().verify(ledger());
    assertThat(result.intact()).isFalse();
    assertThat(result.problems()).anyMatch(p -> p.contains("hash mismatch"));
  }

  @Test
  void truncationIsInvisibleWithoutAHeadAnchor() throws Exception {
    LedgerWriter writer = new LedgerWriter(ledger(), "run-3");
    writer.record(LedgerEntry.Kind.DECISION, "step-1", LedgerEntry.Status.OK);
    writer.record(LedgerEntry.Kind.DECISION, "step-2", LedgerEntry.Status.OK);

    List<String> lines = Files.readAllLines(ledger());
    Files.write(ledger(), List.of(lines.get(0)));

    LedgerVerifier.Result result = new LedgerVerifier().verify(ledger());
    assertThat(result.intact()).isTrue();
    assertThat(result.entries()).isEqualTo(1);
  }

  @Test
  void signedHeadDetectsTruncation() throws Exception {
    Path head = dir.resolve("ledger.head");
    LedgerWriter writer = new LedgerWriter(ledger(), "run-5", head, "demo-key");
    writer.record(LedgerEntry.Kind.DECISION, "step-1", LedgerEntry.Status.OK);
    writer.record(LedgerEntry.Kind.DECISION, "step-2", LedgerEntry.Status.OK);

    List<String> lines = Files.readAllLines(ledger());
    Files.write(ledger(), List.of(lines.get(0)));

    LedgerVerifier.Result result = new LedgerVerifier().verify(ledger(), head, "demo-key");
    assertThat(result.intact()).isFalse();
    assertThat(result.problems()).anyMatch(p -> p.contains("head anchor"));
  }

  @Test
  void headSignedWithAnotherKeyIsRejected() {
    Path head = dir.resolve("ledger2.head");
    LedgerWriter writer = new LedgerWriter(ledger(), "run-6", head, "right-key");
    writer.record(LedgerEntry.Kind.DECISION, "step-1", LedgerEntry.Status.OK);

    LedgerVerifier.Result result = new LedgerVerifier().verify(ledger(), head, "wrong-key");
    assertThat(result.intact()).isFalse();
    assertThat(result.problems()).anyMatch(p -> p.contains("head anchor"));
  }

  @Test
  void resumeKeepsSequenceAndChain() throws Exception {
    LedgerWriter first = new LedgerWriter(ledger(), "run-4");
    first.record(LedgerEntry.Kind.TOOL_CALL, "a", LedgerEntry.Status.OK);

    LedgerWriter second = new LedgerWriter(ledger(), "run-4");
    LedgerEntry next = second.record(LedgerEntry.Kind.TOOL_CALL, "b", LedgerEntry.Status.OK);

    assertThat(next.seq()).isEqualTo(2);
    assertThat(new LedgerVerifier().verify(ledger()).intact()).isTrue();
    assertThat(new String(Files.readAllBytes(ledger()), StandardCharsets.UTF_8))
        .contains("\"name\":\"b\"");
  }

  @Test
  void payloadHashesAreStable() {
    assertThat(LedgerWriter.hashPayload("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    assertThat(LedgerWriter.hashPayload(null)).isNull();
  }
}
