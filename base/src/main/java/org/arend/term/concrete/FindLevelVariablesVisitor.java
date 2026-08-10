package org.arend.term.concrete;

import org.arend.naming.reference.Referable;
import org.arend.typechecking.visitor.VoidConcreteVisitor;

import java.util.Set;

public class FindLevelVariablesVisitor extends VoidConcreteVisitor<Void> {
  private final Set<Referable> myReferables;

  public FindLevelVariablesVisitor(Set<Referable> referables) {
    myReferables = referables;
  }

  @Override
  public Void visitReference(Concrete.ReferenceExpression expr, Void params) {
    if (expr.getLevels() != null) {
      for (Concrete.LevelExpression level : expr.getLevels()) {
        level.accept(this, null);
      }
    }
    return null;
  }

  @Override
  public Void visitUniverse(Concrete.UniverseExpression expr, Void params) {
    if (expr.getPLevel() != null) {
      expr.getPLevel().accept(this, null);
    }
    return null;
  }

  @Override
  public Void visitVar(Concrete.VarLevelExpression expr, Void param) {
    myReferables.add(expr.getReferent());
    return null;
  }
}
