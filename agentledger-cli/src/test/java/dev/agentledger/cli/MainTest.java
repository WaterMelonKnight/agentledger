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
  void rootAndProveAndCheckRoundTrip() throws Exception {
    Path ledger = dir.resolve("merkle.jsonl");
    LedgerWriter writer = new LedgerWriter(ledger, "run-5");
    for (int i = 0; i < 5; i++) {
      writer.record(LedgerEntry.Kind.TOOL_CALL, "tool-" + i, LedgerEntry.Status.OK);
    }
    Path anchor = dir.resolve("anchor.json");
    Path proof = dir.resolve("proof.json");

    assertThat(cli("root", ledger.toString(), "--out", anchor.toString()).code()).isZero();
    assertThat(Files.readString(anchor)).contains("\"entries\":5");

    Run proved = cli("prove", ledger.toString(), "--index", "2", "--out", proof.toString());
    assertThat(proved.code()).isZero();
    assertThat(Files.readString(proof)).contains("\"index\":2");

    assertThat(cli("check", ledger.toString(), "--proof", proof.toString()).code()).isZero();
    assertThat(cli("check", ledger.toString(), "--proof", proof.toString()).out()).contains("OK");
  }

  @Test
  void checkRejectsAProofFromAnotherLedger() throws Exception {
    Path first = dir.resolve("a.jsonl");
    Path second = dir.resolve("b.jsonl");
    LedgerWriter a = new LedgerWriter(first, "run-6");
    a.record(LedgerEntry.Kind.TOOL_CALL, "a", LedgerEntry.Status.OK);
    a.record(LedgerEntry.Kind.TOOL_CALL, "b", LedgerEntry.Status.OK);
    LedgerWriter b = new LedgerWriter(second, "run-7");
    b.record(LedgerEntry.Kind.TOOL_CALL, "x", LedgerEntry.Status.OK);
    b.record(LedgerEntry.Kind.TOOL_CALL, "y", LedgerEntry.Status.OK);

    Path proof = dir.resolve("p.json");
    assertThat(cli("prove", first.toString(), "--index", "0", "--out", proof.toString()).code())
        .isZero();

    Run run = cli("check", second.toString(), "--proof", proof.toString());
    assertThat(run.code()).isEqualTo(Main.FAILED);
    assertThat(run.out()).contains("FAIL");
  }

  @Test
  void missingArgumentsAreAUsageError() {
    Run run = cli("verify");
    assertThat(run.code()).isEqualTo(Main.USAGE);
    assertThat(run.err()).contains("missing ledger path");
  }
}
