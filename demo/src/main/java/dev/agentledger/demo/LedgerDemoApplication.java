package dev.agentledger.demo;

import dev.agentledger.LedgerVerifier;
import dev.agentledger.spring.AgentLedger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LedgerDemoApplication {

  public static void main(String[] args) {
    SpringApplication.run(LedgerDemoApplication.class, args);
  }

  @Bean
  CommandLineRunner run(QuoteService quotes, AgentLedger ledger) {
    return args -> {
      Path ledgerPath = ledger.writer().path();
      Path headPath = Path.of("demo-ledger.head");

      quotes.quote("SH", "2026-12-01");
      try {
        quotes.quote("SH", "2026-13-01");
      } catch (IllegalArgumentException expected) {
        // the failure is recorded too
      }
      ledger.decision("approve_quote", Map.of("confidence", "0.87", "policy", "pricing-v3"));
      ledger.approval("refund_1200", true, "human:pm");

      System.out.println("ledger : " + ledgerPath);
      System.out.println("verify : " + new LedgerVerifier().verify(ledgerPath, headPath, "demo-key").summary());

      Path tampered = Path.of("tampered-ledger.jsonl");
      List<String> lines = Files.readAllLines(ledgerPath, StandardCharsets.UTF_8);
      Files.write(tampered, List.of(lines.get(0),
          lines.get(1).replace("\"quote_price\"", "\"refund_everything\""),
          lines.get(2), lines.get(3)));
      System.out.println("edited : " + new LedgerVerifier().verify(tampered, headPath, "demo-key").summary());

      Path truncated = Path.of("truncated-ledger.jsonl");
      Files.write(truncated, lines.subList(0, lines.size() - 1));
      System.out.println("cut    : " + new LedgerVerifier().verify(truncated, headPath, "demo-key").summary());
    };
  }
}
