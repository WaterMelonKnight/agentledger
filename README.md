# AgentLedger

**An append-only, tamper-evident ledger of what an AI agent actually did.**

Agents call tools, make decisions, and wait for humans. Today that record lives in
logs nobody can verify and in prompts nobody can replay. AgentLedger writes it as a
hash-chained JSON Lines file: every entry points at the previous one, payloads are
hashed instead of stored, and a verifier re-derives the chain.

It is a **library**, not a runtime. It does not schedule agents, call models, or
wrap your framework — it records what already happened, inside the Spring service
you already run.

## Why

- **Audit, not vibes.** "The agent approved the refund" is a fact you can prove:
  entry N, hash, previous hash.
- **Shareable by default.** Arguments and results are stored as SHA-256 hashes, so a
  ledger can leave the building without leaking prompts, user data or tool output.
- **Failures count.** A tool call that threw is recorded with `status: FAILED` and the
  exception type — the interesting part of an agent's day.
- **Crash-safe sequence.** Reopening the file resumes the sequence and the chain.
- **Truncation counts too.** A hash chain alone cannot notice that the last entry was
  deleted; a signed head anchor can, because the expected tip lives outside the file.

## Quick start

```java
@Service
class QuoteService {
  @AgentAction("quote_price")          // recorded automatically
  public String quote(String city, String date) { ... }
}
```

```properties
agentledger.path=./agent-ledger.jsonl
```

```java
ledger.decision("approve_quote", Map.of("confidence", "0.87", "policy", "pricing-v3"));
ledger.approval("refund_1200", true, "human:pm");
```

```sh
$ java -jar demo/target/agentledger-demo-*.jar
ledger : ./demo-ledger.jsonl
verify : 4 entries, hash chain intact, head matches
edited : 4 entries, 1 problem(s): entry 2: hash mismatch (content was modified)
cut    : 3 entries, 1 problem(s): ledger tip does not match the head anchor (entries were removed or replaced)
```

One entry:

```json
{"seq":2,"runId":"run-2043f42a","at":"2026-11-20T05:56:34.040Z","kind":"TOOL_CALL",
 "name":"quote_price","status":"FAILED","durationMs":0,
 "argsHash":"437564d7…","resultHash":"6757b394…",
 "meta":{"error":"IllegalArgumentException"},
 "prevHash":"08c82af5…","hash":"e459a462…"}
```

## Command line

```sh
# re-derive every hash, check the chain and the head anchor
java -jar agentledger-cli/target/agentledger.jar verify demo-ledger.jsonl \
     --head demo-ledger.head --key-env AGENTLEDGER_KEY
agentledger exited 0   # 1 = tampered, 2 = usage error

# replay the recorded actions
java -jar agentledger-cli/target/agentledger.jar replay demo-ledger.jsonl
#1   2026-11-20T05:56:33Z TOOL_CALL quote_price   OK      0ms
#2   2026-11-20T05:56:34Z TOOL_CALL quote_price   FAILED  0ms
#3   2026-11-20T05:56:34Z DECISION  approve_quote OK      0ms
#4   2026-11-20T05:56:34Z APPROVAL  refund_1200   OK      0ms
```

## Modules

| module | what it is |
| --- | --- |
| `agentledger-core` | entries, writer, verifier, hashing. No Spring, no I/O beyond append |
| `agentledger-spring` | `@AgentAction`, auto-configuration, `AgentLedger` facade |
| `agentledger-cli` | `verify` and `replay` over a ledger file, no Spring |
| `demo` | a 20-line Spring Boot app that records a success, a failure, a decision, an approval, then shows an edited and a truncated copy failing verification |

## Status

`0.1.0-SNAPSHOT`, 12 tests, built and run on JDK 21.

What is covered: hash chaining (edit / reorder / middle deletion), payload hashing,
sequence resume after reopen, unsigned and HMAC-signed head anchors, CLI exit codes.

- **Not yet:** OpenTelemetry export, MCP-aware recording, storage backends other than
  local files, key rotation.
- **Honest limit:** an attacker who can rewrite both the ledger *and* the head file wins;
  the HMAC key must live somewhere the ledger writer cannot be tricked into using it
  (env var, KMS, separate process).

## Build

```sh
mvn -B package
java -jar demo/target/agentledger-demo-0.1.0-SNAPSHOT.jar
```
