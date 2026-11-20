package dev.agentledger.demo;

import dev.agentledger.LedgerVerifier;
import dev.agentledger.spring.AgentLedger;
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
      quotes.quote("SH", "2026-12-01");
      try {
        quotes.quote("SH", "2026-13-01");
      } catch (IllegalArgumentException expected) {
        // the failure is recorded too
      }
      ledger.decision("approve_quote", Map.of("confidence", "0.87", "policy", "pricing-v3"));
      ledger.approval("refund_1200", true, "human:pm");

      LedgerVerifier.Result result = new LedgerVerifier().verify(ledger.writer().path());
      System.out.println("ledger: " + ledger.writer().path());
      System.out.println("verify: " + result.summary());
    };
  }
}
