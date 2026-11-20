package dev.agentledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Re-derives every hash in a ledger and checks the chain. Any edit, reorder,
 * deletion or truncation in the middle is reported instead of silently accepted.
 */
public final class LedgerVerifier {

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  public record Result(boolean intact, int entries, long lastSeq, List<String> problems) {
    public String summary() {
      if (intact) return entries + " entries, hash chain intact";
      return entries + " entries, " + problems.size() + " problem(s): " + String.join("; ", problems);
    }
  }

  public Result verify(Path path) {
    List<String> problems = new ArrayList<>();
    List<LedgerEntry> entries = read(path, problems);
    LedgerWriter writer = new LedgerWriter(Path.of(path + ".verifier-scratch"));
    String expectedPrev = LedgerWriter.GENESIS;
    long expectedSeq = 1;
    for (LedgerEntry entry : entries) {
      if (entry.seq() != expectedSeq) {
        problems.add("entry " + entry.seq() + ": expected seq " + expectedSeq);
      }
      if (!expectedPrev.equals(entry.prevHash())) {
        problems.add("entry " + entry.seq() + ": prevHash does not match the previous entry");
      }
      String recomputed = writer.hashOf(entry);
      if (!recomputed.equals(entry.hash())) {
        problems.add("entry " + entry.seq() + ": hash mismatch (content was modified)");
      }
      expectedPrev = entry.hash();
      expectedSeq = entry.seq() + 1;
    }
    return new Result(problems.isEmpty(), entries.size(),
        entries.isEmpty() ? 0 : entries.get(entries.size() - 1).seq(), List.copyOf(problems));
  }

  private List<LedgerEntry> read(Path path, List<String> problems) {
    List<LedgerEntry> entries = new ArrayList<>();
    if (!Files.exists(path)) return entries;
    try {
      int lineNo = 0;
      for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
        lineNo++;
        if (line.isBlank()) continue;
        try {
          entries.add(mapper.readValue(line, LedgerEntry.class));
        } catch (IOException e) {
          problems.add("line " + lineNo + ": not valid ledger JSON");
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read ledger " + path, e);
    }
    return entries;
  }
}
