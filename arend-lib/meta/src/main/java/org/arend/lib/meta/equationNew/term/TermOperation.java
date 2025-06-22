package org.arend.lib.meta.equationNew.term;

import org.arend.ext.reference.ArendRef;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;

import java.util.List;

public record TermOperation(ArendRef reflectionRef, FunctionMatcher matcher, List<Type> argTypes) {
  public enum Type { TERM, NAT }
}
