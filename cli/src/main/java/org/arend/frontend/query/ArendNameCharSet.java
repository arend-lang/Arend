package org.arend.frontend.query;

import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.Transition;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.arend.frontend.parser.ArendLexer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The characters an Arend identifier may contain, at any position ({@code START_CHAR}
 * plus the continuation-only {@code [0-9']} of the {@code ID} rule). Recovered from the
 * generated {@link ArendLexer} ATN, not hardcoded, so {@code Arend.g4} stays the single
 * source of truth. Shared by the pattern compiler and the find-usages boundary scan.
 */
public final class ArendNameCharSet {
  private ArendNameCharSet() {}

  private static final IntervalSet ID_CHARS = collectRuleChars("ID");
  private static final IntervalSet START_CHARS = collectRuleChars("START_CHAR");
  public static final String ID_CHAR_CLASS = buildCharClass(ID_CHARS);
  public static final String SEP_CHAR_CLASS = buildCharClass(separatorChars());

  /** True when {@code c} can appear in an Arend identifier. */
  public static boolean isIdChar(char c) {
    return ID_CHARS.contains(c);
  }

  /** Unions the character labels of the named lexer rule, extracted once from the generated ATN. */
  private static IntervalSet collectRuleChars(String ruleName) {
    IntervalSet result = new IntervalSet();
    collectRule(ArendLexer._ATN, ruleIndex(ruleName), result, new HashSet<>());
    return result;
  }

  /** {@link #START_CHARS} minus the ASCII letters and {@code -}; see {@link #SEP_CHAR_CLASS}. */
  private static IntervalSet separatorChars() {
    IntervalSet excluded = IntervalSet.of('a', 'z');
    excluded.add('A', 'Z');
    excluded.add('-');
    return IntervalSet.subtract(START_CHARS, excluded);
  }

  // Walks only states of the given rule, descending into rule invocations (ID -> START_CHAR)
  // but never following stop-state edges back into callers (INFIX/POSTFIX/INVALID_KEYWORD).
  private static void collectRule(ATN atn, int rule, IntervalSet acc, Set<Integer> visited) {
    Deque<ATNState> stack = new ArrayDeque<>();
    stack.push(atn.ruleToStartState[rule]);
    while (!stack.isEmpty()) {
      ATNState state = stack.pop();
      if (!visited.add(state.stateNumber)) continue;
      for (int i = 0; i < state.getNumberOfTransitions(); i++) {
        Transition t = state.transition(i);
        if (t instanceof RuleTransition rt) {
          collectRule(atn, rt.ruleIndex, acc, visited);          // descend into START_CHAR
          if (rt.followState.ruleIndex == rule) stack.push(rt.followState);
        } else {
          IntervalSet label = t.label();                         // null for epsilon transitions
          if (label != null) acc.addAll(label);
          if (t.target != null && t.target.ruleIndex == rule) stack.push(t.target);
        }
      }
    }
  }

  private static int ruleIndex(String name) {
    String[] names = ArendLexer.ruleNames;
    for (int i = 0; i < names.length; i++) {
      if (names[i].equals(name)) return i;
    }
    throw new IllegalStateException("Lexer rule not found: " + name);
  }

  private static String buildCharClass(IntervalSet set) {
    StringBuilder sb = new StringBuilder("[");
    for (Interval iv : set.getIntervals()) {
      sb.append("\\x{").append(Integer.toHexString(iv.a)).append('}');
      if (iv.b != iv.a) sb.append("-\\x{").append(Integer.toHexString(iv.b)).append('}');
    }
    return sb.append(']').toString();
  }
}
