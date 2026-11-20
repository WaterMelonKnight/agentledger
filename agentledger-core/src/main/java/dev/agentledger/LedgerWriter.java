package dev.agentledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Appends entries to a JSON Lines file and links each entry to the previous one by
 * hash, so removing, reordering or editing an entry breaks verification.
 */
public final class LedgerWriter {

  public static final String GENESIS = "GENESIS";

  private final Path path;
  private final String runId;
  private final ObjectMapper mapper =
      new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final Path headPath;
  private final String signingKey;

  private long seq;
  private String lastHash = GENESIS;

  public LedgerWriter(Path path) {
    this(path, "run-" + UUID.randomUUID());
  }

  public LedgerWriter(Path path, String runId) {
    this(path, runId, null, null);
  }

  public LedgerWriter(Path path, Path headPath, String signingKey) {
    this(path, "run-" + UUID.randomUUID(), headPath, signingKey);
  }

  public LedgerWriter(Path path, String runId, Path headPath, String signingKey) {
    this.path = path;
    this.runId = runId;
    this.headPath = headPath;
    this.signingKey = signingKey;
    resume();
    if (headPath != null && seq > 0) {
      HeadAnchor.of(seq, lastHash, signingKey).write(headPath);
    }
  }

  private void resume() {
    if (!Files.exists(path)) return;
    try {
      List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      for (String line : lines) {
        if (line.isBlank()) continue;
        LedgerEntry entry = mapper.readValue(line, LedgerEntry.class);
        seq = entry.seq();
        lastHash = entry.hash();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read existing ledger " + path, e);
    }
  }

  public synchronized LedgerEntry append(
      LedgerEntry.Kind kind,
      String name,
      LedgerEntry.Status status,
      long durationMs,
      String argsHash,
      String resultHash,
      Map<String, String> meta) {
    LedgerEntry unsigned =
        new LedgerEntry(
            seq + 1, runId, Instant.now(), kind, name, status, durationMs, argsHash, resultHash,
            new LinkedHashMap<>(meta == null ? Map.of() : meta), lastHash, null);
    LedgerEntry signed = unsigned.withHash(hashOf(unsigned));
    try {
      String line = mapper.writeValueAsString(signed) + System.lineSeparator();
      Files.writeString(
          path, line, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot append to ledger " + path, e);
    }
    seq = signed.seq();
    lastHash = signed.hash();
    if (headPath != null) {
      HeadAnchor.of(seq, lastHash, signingKey).write(headPath);
    }
    return signed;
  }

  public synchronized LedgerEntry record(
      LedgerEntry.Kind kind, String name, LedgerEntry.Status status) {
    return append(kind, name, status, 0, null, null, Map.of());
  }

  String hashOf(LedgerEntry entry) {
    return LedgerHash.of(entry);
  }

  public static String hashPayload(String payload) {
    return LedgerHash.ofPayload(payload);
  }

  public Path path() {
    return path;
  }

  public String runId() {
    return runId;
  }

  public long lastSeq() {
    return seq;
  }
}
