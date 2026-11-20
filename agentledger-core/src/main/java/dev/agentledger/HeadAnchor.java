package dev.agentledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * External anchor for the tip of a ledger.
 *
 * A hash chain alone cannot detect truncation: removing the last entry leaves a
 * perfectly valid chain. The anchor stores the expected head (seq + hash) next to
 * the ledger, optionally signed with an HMAC key that an attacker with file access
 * does not have. Verify compares the file's last entry against the anchor.
 */
public record HeadAnchor(long seq, String hash, String mac, boolean signed) {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static HeadAnchor of(long seq, String hash, String key) {
    String mac = key == null || key.isBlank() ? null : sign(seq, hash, key);
    return new HeadAnchor(seq, hash, mac, mac != null);
  }

  public boolean matches(LedgerEntry last, String key) {
    if (last == null) return false;
    if (last.seq() != seq) return false;
    if (!last.hash().equals(hash)) return false;
    if (mac == null) return true;
    if (key == null || key.isBlank()) return false;
    return mac.equals(sign(seq, hash, key));
  }

  public void write(Path path) {
    try {
      Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
      Files.writeString(tmp, MAPPER.writeValueAsString(this) + System.lineSeparator(),
          StandardCharsets.UTF_8);
      Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot write ledger head " + path, e);
    }
  }

  public static HeadAnchor read(Path path) {
    if (!Files.exists(path)) return null;
    try {
      String text = Files.readString(path, StandardCharsets.UTF_8).trim();
      if (text.isEmpty()) return null;
      return MAPPER.readValue(text, HeadAnchor.class);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read ledger head " + path, e);
    }
  }

  private static String sign(long seq, String hash, String key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal((seq + "|" + hash).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("cannot sign ledger head", e);
    }
  }

  static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}
