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

```
$ java -jar demo/target/agentledger-demo-*.jar --agentledger.path=/tmp/ledger.jsonl
ledger: /tmp/ledger.jsonl
verify: 4 entries, hash chain intact
```

One entry:

```json
{"seq":2,"runId":"run-2043f42a","at":"2026-11-20T05:56:34.040Z","kind":"TOOL_CALL",
 "name":"quote_price","status":"FAILED","durationMs":0,
 "argsHash":"437564d7…","resultHash":"6757b394…",
 "meta":{"error":"IllegalArgumentException"},
 "prevHash":"08c82af5…","hash":"e459a462…"}
```

## Modules

| module | what it is |
| --- | --- |
| `agentledger-core` | entries, writer, verifier, hashing. No Spring, no I/O beyond append |
| `agentledger-spring` | `@AgentAction`, auto-configuration, `AgentLedger` facade |
| `demo` | a 20-line Spring Boot app that records a success, a failure, a decision, an approval |

## Status

`0.1.0-SNAPSHOT`, 5 tests, built and run on JDK 21. This is a first slice on purpose:

- **Not yet:** signed head (so tail truncation needs an external anchor), replay CLI,
  OpenTelemetry export, MCP-aware recording, storage backends other than local files.
- **Known gap:** the chain detects edits, reordering and middle deletion. Removing the
  *last* entry cannot be detected from the file alone; a signed head is the fix.

## Build

```sh
mvn -B package
java -jar demo/target/agentledger-demo-0.1.0-SNAPSHOT.jar
```
