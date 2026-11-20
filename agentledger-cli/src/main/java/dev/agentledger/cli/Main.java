package dev.agentledger.cli;

import dev.agentledger.LedgerEntry;
import dev.agentledger.LedgerVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Small CLI: verify a ledger, or replay it as a timeline. */
public final class Main {

  static final int OK = 0;
  static final int FAILED = 1;
  static final int USAGE = 2;

  public static void main(String[] args) {
    int code = new Main().run(args, System.out, System.err);
    System.exit(code);
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
    return switch (cmd.command) {
      case "verify" -> verify(cmd, out, err);
      case "replay" -> replay(cmd, out, err);
      default -> {
        err.println("error: unknown command " + cmd.command);
        usage(err);
        yield USAGE;
      }
    };
  }

  private int verify(Cmd cmd, PrintStream out, PrintStream err) {
    if (!Files.exists(cmd.ledger)) {
      err.println("error: no ledger at " + cmd.ledger);
      return USAGE;
    }
    LedgerVerifier.Result result =
        new LedgerVerifier().verify(cmd.ledger, cmd.head, cmd.key());
    out.println(result.summary());
    result.problems().forEach(problem -> out.println("  - " + problem));
    return result.intact() ? OK : FAILED;
  }

  private int replay(Cmd cmd, PrintStream out, PrintStream err) {
    if (!Files.exists(cmd.ledger)) {
      err.println("error: no ledger at " + cmd.ledger);
      return USAGE;
    }
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    try {
      for (String line : Files.readAllLines(cmd.ledger, StandardCharsets.UTF_8)) {
        if (line.isBlank()) continue;
        LedgerEntry e = mapper.readValue(line, LedgerEntry.class);
        out.printf("#%-3d %s %-9s %-22s %-6s %5dms%n",
            e.seq(), e.at(), e.kind(), e.name(), e.status(), e.durationMs());
      }
    } catch (IOException e) {
      err.println("error: cannot read ledger: " + e.getMessage());
      return FAILED;
    }
    return OK;
  }

  private void usage(PrintStream out) {
    out.println("""
        agentledger <command>

          verify <ledger.jsonl> [--head <file>] [--key-env <VAR>]
              Re-derive every hash, check the chain, and compare the tip with the
              head anchor when one is given. Exit 0 intact, 1 tampered, 2 usage.

          replay <ledger.jsonl>
              Print the recorded actions as a timeline.""");
  }

  record Cmd(String command, Path ledger, Path head, String keyEnv) {
    static Cmd parse(String[] args) {
      if (args.length < 2) throw new IllegalArgumentException("missing ledger path");
      String command = args[0];
      Path ledger = Path.of(args[1]);
      Path head = null;
      String keyEnv = null;
      List<String> rest = new ArrayList<>();
      for (int i = 2; i < args.length; i++) {
        switch (args[i]) {
          case "--head" -> {
            if (++i >= args.length) throw new IllegalArgumentException("--head needs a path");
            head = Path.of(args[i]);
          }
          case "--key-env" -> {
            if (++i >= args.length) throw new IllegalArgumentException("--key-env needs a name");
            keyEnv = args[i];
          }
          default -> rest.add(args[i]);
        }
      }
      if (!rest.isEmpty()) throw new IllegalArgumentException("unexpected argument " + rest.get(0));
      return new Cmd(command, ledger, head, keyEnv);
    }

    String key() {
      return keyEnv == null ? null : System.getenv(keyEnv);
    }
  }
}
