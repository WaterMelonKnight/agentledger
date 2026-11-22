package dev.agentledger.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.agentledger.LedgerEntry;
import dev.agentledger.LedgerVerifier;
import dev.agentledger.MerkleTree;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small CLI: check integrity, replay, and prove a single entry with a Merkle proof. */
public final class Main {

  static final int OK = 0;
  static final int FAILED = 1;
  static final int USAGE = 2;

  private static final ObjectMapper JSON = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public static void main(String[] args) {
    System.exit(new Main().run(args, System.out, System.err));
  }

  int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
      usage(out);
      return args.length == 0 ? USAGE : OK;
    }
    Cmd cmd;
    try {
      cmd = Cmd.parse(args);
    } catch (IllegalArgumentException e) {
      err.println("error: " + e.getMessage());
      usage(err);
      return USAGE;
    }
    if (!cmd.command.equals("help") && !Files.exists(cmd.ledger)) {
      err.println("error: no ledger at " + cmd.ledger);
      return USAGE;
    }
    return switch (cmd.command) {
      case "verify" -> verify(cmd, out);
      case "replay" -> replay(cmd, out, err);
      case "root" -> root(cmd, out, err);
      case "prove" -> prove(cmd, out, err);
      case "check" -> check(cmd, out, err);
      default -> {
        err.println("error: unknown command " + cmd.command);
        usage(err);
        yield USAGE;
      }
    };
  }

  private int verify(Cmd cmd, PrintStream out) {
    LedgerVerifier.Result result = new LedgerVerifier().verify(cmd.ledger, cmd.head, cmd.key());
    out.println(result.summary());
    result.problems().forEach(problem -> out.println("  - " + problem));
    return result.intact() ? OK : FAILED;
  }

  private int replay(Cmd cmd, PrintStream out, PrintStream err) {
    try {
      for (LedgerEntry e : LedgerVerifier.entries(cmd.ledger)) {
        out.printf("#%-3d %s %-9s %-22s %-6s %5dms%n",
            e.seq(), e.at(), e.kind(), e.name(), e.status(), e.durationMs());
      }
      return OK;
    } catch (RuntimeException e) {
      err.println("error: cannot read ledger: " + e.getMessage());
      return FAILED;
    }
  }

  private int root(Cmd cmd, PrintStream out, PrintStream err) {
    List<LedgerEntry> entries = LedgerVerifier.entries(cmd.ledger);
    if (entries.isEmpty()) {
      out.println("ledger is empty");
      return FAILED;
    }
    String root = MerkleTree.root(entries.stream().map(LedgerEntry::hash).toList());
    out.println("root    " + root);
    out.println("entries " + entries.size());
    if (cmd.out != null) {
      try {
        Files.writeString(cmd.out, JSON.writeValueAsString(
            new Anchor(root, entries.size(), java.time.Instant.now())) + System.lineSeparator(),
            StandardCharsets.UTF_8);
        out.println("wrote   " + cmd.out);
      } catch (IOException e) {
        err.println("error: cannot write anchor: " + e.getMessage());
        return FAILED;
      }
    }
    return OK;
  }

  private int prove(Cmd cmd, PrintStream out, PrintStream err) {
    if (cmd.index == null) {
      err.println("error: prove needs --index <n>");
      return USAGE;
    }
    List<LedgerEntry> entries = LedgerVerifier.entries(cmd.ledger);
    if (cmd.index < 0 || cmd.index >= entries.size()) {
      err.println("error: --index must be between 0 and " + (entries.size() - 1));
      return USAGE;
    }
    MerkleTree.InclusionProof proof = MerkleTree.proof(
        entries.stream().map(LedgerEntry::hash).toList(), cmd.index);
    String json;
    try {
      json = JSON.writeValueAsString(proof);
    } catch (IOException e) {
      err.println("error: cannot serialize proof: " + e.getMessage());
      return FAILED;
    }
    if (cmd.out != null) {
      try {
        Files.writeString(cmd.out, json + System.lineSeparator(), StandardCharsets.UTF_8);
      } catch (IOException e) {
        err.println("error: cannot write proof: " + e.getMessage());
        return FAILED;
      }
    }
    out.println(json);
    return OK;
  }

  private int check(Cmd cmd, PrintStream out, PrintStream err) {
    if (cmd.proofFile == null) {
      err.println("error: check needs --proof <file>");
      return USAGE;
    }
    try {
      MerkleTree.InclusionProof proof =
          JSON.readValue(Files.readString(cmd.proofFile, StandardCharsets.UTF_8),
              MerkleTree.InclusionProof.class);
      List<LedgerEntry> entries = LedgerVerifier.entries(cmd.ledger);
      if (proof.index() < 0 || proof.index() >= entries.size()) {
        out.println("FAIL entry " + proof.index() + " is not in this ledger");
        return FAILED;
      }
      String leaf = entries.get(proof.index()).hash();
      boolean ok = MerkleTree.verify(proof, leaf);
      out.println((ok ? "OK   " : "FAIL ") + "entry " + proof.index()
          + " is " + (ok ? "included in " : "not part of ") + "root " + proof.root());
      return ok ? OK : FAILED;
    } catch (IOException | RuntimeException e) {
      err.println("error: cannot read proof: " + e.getMessage());
      return FAILED;
    }
  }

  private void usage(PrintStream out) {
    out.println("""
        agentledger <command>

          verify <ledger.jsonl> [--head <file>] [--key-env <VAR>]
              Re-derive every hash, check the chain, compare the head anchor when given.
              Exit 0 intact, 1 tampered, 2 usage.

          replay <ledger.jsonl>
              Print the recorded actions as a timeline.

          root <ledger.jsonl> [--out <anchor.json>]
              Print the Merkle root over all entries; optionally write it as an anchor
              to store somewhere the ledger writer cannot rewrite.

          prove <ledger.jsonl> --index <n> [--out <proof.json>]
              Print an inclusion proof for one entry (log2 entries in size).

          check <ledger.jsonl> --proof <proof.json>
              Verify that proof against the ledger. Exit 0 included, 1 not.""");
  }

  record Anchor(String root, int entries, java.time.Instant anchoredAt) {}

  record Cmd(String command, Path ledger, Path head, String keyEnv, Integer index, Path out,
             Path proofFile) {

    static Cmd parse(String[] args) {
      if (args.length < 2) throw new IllegalArgumentException("missing ledger path");
      String command = args[0];
      Path ledger = Path.of(args[1]);
      Path head = null;
      Path out = null;
      Path proofFile = null;
      String keyEnv = null;
      Integer index = null;
      for (int i = 2; i < args.length; i++) {
        switch (args[i]) {
          case "--head" -> head = Path.of(requireValue(args, ++i, "--head"));
          case "--key-env" -> keyEnv = requireValue(args, i += 1, "--key-env");
          case "--index" -> index = Integer.parseInt(requireValue(args, ++i, "--index"));
          case "--out" -> out = Path.of(requireValue(args, ++i, "--out"));
          case "--proof" -> proofFile = Path.of(requireValue(args, ++i, "--proof"));
          default -> throw new IllegalArgumentException("unexpected argument " + args[i]);
        }
      }
      return new Cmd(command, ledger, head, keyEnv, index, out, proofFile);
    }

    private static String requireValue(String[] args, int i, String flag) {
      if (i >= args.length) throw new IllegalArgumentException(flag + " needs a value");
      return args[i];
    }

    String key() {
      return keyEnv == null ? null : System.getenv(keyEnv);
    }
  }
}
