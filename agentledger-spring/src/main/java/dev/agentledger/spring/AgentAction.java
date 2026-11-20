package dev.agentledger.spring;

import dev.agentledger.LedgerEntry;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose invocation must be recorded in the ledger. Arguments and
 * return values are hashed, not stored, so the ledger stays shareable.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentAction {

  /** Ledger name; defaults to the method name. */
  String value() default "";

  LedgerEntry.Kind kind() default LedgerEntry.Kind.TOOL_CALL;
}
