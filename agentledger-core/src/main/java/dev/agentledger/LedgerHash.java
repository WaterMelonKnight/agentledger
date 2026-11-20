package dev.agentledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical serialization and hashing shared by the writer and the verifier. */
public final class LedgerHash {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private LedgerHash() {}

  public static String of(LedgerEntry entry) {
    try {
      byte[] canonical = MAPPER.writeValueAsBytes(entry.canonical());
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot canonicalize entry", e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public static String ofPayload(String payload) {
    if (payload == null) return null;
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
