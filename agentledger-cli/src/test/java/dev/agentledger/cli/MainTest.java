package dev.agentledger.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentledger.LedgerEntry;
import dev.agentledger.LedgerWriter;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

  @TempDir Path dir;

  private record Run(int code, String out, String err) {}

  private Run cli(String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = new Main().run(args,
        new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8));
    return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  @Test
  void verifyReturnsZeroForAnIntactLedger() {
    Path ledger = dir.resolve("ok.jsonl");
    new LedgerWriter(ledger, "run-1").record(
        LedgerEntry.Kind.TOOL_CALL, "quote_price", LedgerEntry.Status.OK);

    Run run = cli("verify", ledger.toString());
    assertThat(run.code()).isZero();
    assertThat(run.out()).contains("hash chain intact");
  }

  @Test
  void verifyReturnsOneWhenAnEntryWasEdited() throws Exception {
    Path ledger = dir.resolve("edited.jsonl");
    LedgerWriter writer = new LedgerWriter(ledger, "run-2");
    writer.record(LedgerEntry.Kind.TOOL_CALL, "read_file", LedgerEntry.Status.OK);
    writer.record(LedgerEntry.Kind.TOOL_CALL, "write_file", LedgerEntry.Status.OK);

    List<String> lines = Files.readAllLines(ledger);
    Files.write(ledger, List.of(lines.get(0), lines.get(1).replace("write_file", "delete_all")));

    Run run = cli("verify", ledger.toString());
    assertThat(run.code()).isEqualTo(Main.FAILED);
    assertThat(run.out()).contains("hash mismatch");
  }

  @Test
  void verifyDetectsTruncationWithAHeadAnchor() throws Exception {
    Path ledger = dir.resolve("cut.jsonl");
    Path head = dir.resolve("cut.head");
    LedgerWriter writer = new LedgerWriter(ledger, "run-3", head, "k");
    writer.record(LedgerEntry.Kind.DECISION, "step-1", LedgerEntry.Status.OK);
    writer.record(LedgerEntry.Kind.DECISION, "step-2", LedgerEntry.Status.OK);

    List<String> lines = Files.readAllLines(ledger);
    Files.write(ledger, List.of(lines.get(0)));

    Run run = cli("verify", ledger.toString(), "--head", head.toString());
    assertThat(run.code()).isEqualTo(Main.FAILED);
    assertThat(run.out()).contains("head anchor");

    Run withKey = cli("verify", ledger.toString(), "--head", head.toString(), "--key-env", "NO_SUCH_ENV");
    assertThat(withKey.code()).isEqualTo(Main.FAILED);
  }

  @Test
  void replayPrintsTheTimeline() {
    Path ledger = dir.resolve("replay.jsonl");
    LedgerWriter writer = new LedgerWriter(ledger, "run-4");
    writer.record(LedgerEntry.Kind.APPROVAL, "refund_1200", LedgerEntry.Status.OK);

    Run run = cli("replay", ledger.toString());
    assertThat(run.code()).isZero();
    assertThat(run.out()).contains("APPROVAL").contains("refund_1200");
  }

  @Test
  void missingArgumentsAreAUsageError() {
    Run run = cli("verify");
    assertThat(run.code()).isEqualTo(Main.USAGE);
    assertThat(run.err()).contains("missing ledger path");
  }
}
