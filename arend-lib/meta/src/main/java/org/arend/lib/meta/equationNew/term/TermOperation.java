package org.arend.lib.meta.equationNew.term;

import org.arend.ext.reference.ArendRef;
import org.arend.lib.meta.equation.binop_matcher.FunctionMatcher;

public record TermOperation(ArendRef reflectionRef, FunctionMatcher matcher) {}
