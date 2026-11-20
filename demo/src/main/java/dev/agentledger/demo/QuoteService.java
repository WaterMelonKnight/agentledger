package dev.agentledger.demo;

import dev.agentledger.spring.AgentAction;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {

  @AgentAction("quote_price")
  public String quote(String city, String date) {
    if (date.endsWith("-13-01")) {
      throw new IllegalArgumentException("no such month: " + date);
    }
    return "{\"city\":\"" + city + "\",\"price\":420}";
  }
}
