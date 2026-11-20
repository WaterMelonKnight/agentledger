package dev.agentledger.spring;

import dev.agentledger.LedgerEntry;
import dev.agentledger.LedgerWriter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/** Records every {@link AgentAction} invocation, including failures, then rethrows. */
@Aspect
public class AgentActionAspect {

  private final AgentLedger ledger;

  public AgentActionAspect(AgentLedger ledger) {
    this.ledger = ledger;
  }

  @Around("@annotation(agentAction)")
  public Object record(ProceedingJoinPoint joinPoint, AgentAction agentAction) throws Throwable {
    String name = agentAction.value().isBlank()
        ? ((MethodSignature) joinPoint.getSignature()).getMethod().getName()
        : agentAction.value();
    long started = System.nanoTime();
    try {
      Object result = joinPoint.proceed();
      ledger.writer().append(
          agentAction.kind(), name, LedgerEntry.Status.OK,
          (System.nanoTime() - started) / 1_000_000,
          LedgerWriter.hashPayload(args(joinPoint)), LedgerWriter.hashPayload(String.valueOf(result)),
          java.util.Map.of());
      return result;
    } catch (Throwable failure) {
      ledger.writer().append(
          agentAction.kind(), name, LedgerEntry.Status.FAILED,
          (System.nanoTime() - started) / 1_000_000,
          LedgerWriter.hashPayload(args(joinPoint)),
          LedgerWriter.hashPayload(failure.getClass().getName()),
          java.util.Map.of("error", failure.getClass().getSimpleName()));
      throw failure;
    }
  }

  private String args(ProceedingJoinPoint joinPoint) {
    StringBuilder sb = new StringBuilder();
    for (Object arg : joinPoint.getArgs()) {
      sb.append(arg).append(';');
    }
    return sb.toString();
  }
}
