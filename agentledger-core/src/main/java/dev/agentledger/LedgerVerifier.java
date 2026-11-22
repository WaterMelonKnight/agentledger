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

  public record Result(
      boolean intact, int entries, long lastSeq, boolean headChecked, List<String> problems) {
    public String summary() {
      String head = headChecked ? ", head matches" : "";
      if (intact) return entries + " entries, hash chain intact" + head;
      return entries + " entries, " + problems.size() + " problem(s): " + String.join("; ", problems);
    }
  }

  public Result verify(Path path) {
    return verify(path, null, null);
  }

  /**
   * @param headPath anchor file; when present the file's tip must match it
   * @param key HMAC key used to validate the anchor; null accepts an unsigned anchor
   */
  public Result verify(Path path, Path headPath, String key) {
    List<String> problems = new ArrayList<>();
    List<LedgerEntry> entries = read(path, problems);
    String expectedPrev = LedgerWriter.GENESIS;
    long expectedSeq = 1;
    for (LedgerEntry entry : entries) {
      if (entry.seq() != expectedSeq) {
        problems.add("entry " + entry.seq() + ": expected seq " + expectedSeq);
      }
      if (!expectedPrev.equals(entry.prevHash())) {
        problems.add("entry " + entry.seq() + ": prevHash does not match the previous entry");
      }
      String recomputed = LedgerHash.of(entry);
      if (!recomputed.equals(entry.hash())) {
        problems.add("entry " + entry.seq() + ": hash mismatch (content was modified)");
      }
      expectedPrev = entry.hash();
      expectedSeq = entry.seq() + 1;
    }
    boolean headChecked = false;
    if (headPath != null) {
      HeadAnchor anchor = HeadAnchor.read(headPath);
      if (anchor == null) {
        problems.add("head anchor missing at " + headPath);
      } else {
        headChecked = true;
        LedgerEntry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        if (!anchor.matches(last, key)) {
          problems.add("ledger tip does not match the head anchor (entries were removed or replaced)");
        }
      }
    }
    return new Result(problems.isEmpty(), entries.size(),
        entries.isEmpty() ? 0 : entries.get(entries.size() - 1).seq(), headChecked,
        List.copyOf(problems));
  }

  /** Reads the ledger without judging it; problems with malformed lines are dropped. */
  public static List<LedgerEntry> entries(Path path) {
    List<String> ignored = new ArrayList<>();
    return new LedgerVerifier().read(path, ignored);
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
