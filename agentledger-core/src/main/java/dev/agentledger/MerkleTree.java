package dev.agentledger;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Merkle tree over ledger entry hashes.
 *
 * Leaves are domain-separated ({@code SHA-256(0x00 || leaf)}) from internal nodes
 * ({@code SHA-256(0x01 || left || right)}) so a leaf can never be replayed as an
 * internal node. An odd node is promoted unchanged instead of being duplicated,
 * which avoids the classic "duplicate the last node" ambiguity.
 *
 * The root is what you anchor outside the ledger: 32 bytes prove a whole history.
 */
public final class MerkleTree {

  private MerkleTree() {}

  public record Step(String hash, boolean left) {}

  public record InclusionProof(int index, int size, String root, List<Step> path) {}

  public static String root(List<String> leafHashes) {
    if (leafHashes.isEmpty()) return null;
    List<String> level = leafHashes.stream().map(MerkleTree::leaf).toList();
    while (level.size() > 1) {
      List<String> next = new ArrayList<>();
      for (int i = 0; i < level.size(); i += 2) {
        if (i + 1 == level.size()) next.add(level.get(i));
        else next.add(node(level.get(i), level.get(i + 1)));
      }
      level = next;
    }
    return level.get(0);
  }

  public static InclusionProof proof(List<String> leafHashes, int index) {
    if (index < 0 || index >= leafHashes.size()) {
      throw new IndexOutOfBoundsException("index " + index + " outside 0.." + (leafHashes.size() - 1));
    }
    List<Step> path = new ArrayList<>();
    List<String> level = leafHashes.stream().map(MerkleTree::leaf).toList();
    int cursor = index;
    while (level.size() > 1) {
      int sibling = cursor % 2 == 0 ? cursor + 1 : cursor - 1;
      if (sibling < level.size()) {
        path.add(new Step(level.get(sibling), sibling < cursor));
      }
      List<String> next = new ArrayList<>();
      for (int i = 0; i < level.size(); i += 2) {
        if (i + 1 == level.size()) next.add(level.get(i));
        else next.add(node(level.get(i), level.get(i + 1)));
      }
      level = next;
      cursor /= 2;
    }
    return new InclusionProof(index, leafHashes.size(), level.get(0), List.copyOf(path));
  }

  public static boolean verify(InclusionProof proof, String leafHash) {
    if (proof.path() == null) return false;
    String current = leaf(leafHash);
    for (Step step : proof.path()) {
      current = step.left() ? node(step.hash(), current) : node(current, step.hash());
    }
    return current.equals(proof.root());
  }

  static String leaf(String entryHash) {
    return sha256((byte) 0, bytes(entryHash));
  }

  static String node(String leftHex, String rightHex) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(1);
    out.writeBytes(bytes(leftHex));
    out.writeBytes(bytes(rightHex));
    return sha256(out.toByteArray());
  }

  private static byte[] bytes(String hex) {
    return HexFormat.of().parseHex(hex);
  }

  private static String sha256(byte... input) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static String sha256(byte prefix, byte[] payload) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(prefix);
    out.writeBytes(payload);
    return sha256(out.toByteArray());
  }
}
