package org.arend.typechecking.result;

import org.arend.core.context.param.DependentLink;
import org.arend.core.expr.Expression;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.implicitargs.equations.Equations;
import org.arend.typechecking.visitor.CheckTypeVisitor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface TResult {
  @Nullable TypecheckingResult toResult(CheckTypeVisitor typechecker);
  DependentLink getParameter();
  TResult applyExpression(Expression expression, boolean isExplicit, CheckTypeVisitor typechecker, Concrete.SourceNode sourceNode);
  List<? extends DependentLink> getImplicitParameters();
  Expression getType(@Nullable Equations equations);
}