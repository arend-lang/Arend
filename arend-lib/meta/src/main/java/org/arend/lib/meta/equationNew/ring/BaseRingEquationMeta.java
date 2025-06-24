package org.arend.lib.meta.equationNew.ring;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.meta.equationNew.group.BaseCommutativeGroupEquationMeta;
import org.arend.lib.meta.equationNew.term.TermOperation;
import org.arend.lib.ring.Monomial;
import org.arend.lib.util.Values;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BaseRingEquationMeta extends BaseAlgebraEquationMeta {
  @Dependency(name = "AddGroup.negative")                   CoreClassField negative;
  @Dependency(name = "RingSolverModel.Term.:zro")           ArendRef zroTerm;
  @Dependency(name = "RingSolverModel.Term.:ide")           ArendRef ideTerm;
  @Dependency(name = "RingSolverModel.Term.:+")             ArendRef addTerm;
  @Dependency(name = "RingSolverModel.Term.:*")             ArendRef mulTerm;
  @Dependency(name = "RingSolverModel.Term.:negative")      ArendRef negativeTerm;
  @Dependency(name = "RingSolverModel.Term.coef")           ArendRef coefTerm;
  @Dependency(name = "RingSolverModel.Term.var")            ArendRef varTerm;

  @Override
  protected @NotNull ArendRef getZroTerm() {
    return zroTerm;
  }

  @Override
  protected @NotNull ArendRef getIdeTerm() {
    return ideTerm;
  }

  @Override
  protected @NotNull ArendRef getAddTerm() {
    return addTerm;
  }

  @Override
  protected @NotNull ArendRef getMulTerm() {
    return mulTerm;
  }

  @Override
  protected @NotNull ArendRef getCoefTerm() {
    return coefTerm;
  }

  @Override
  protected @Nullable ArendRef getNegativeTerm() {
    return negativeTerm;
  }

  @Override
  protected @Nullable CoreClassField getNegative() {
    return negative;
  }

  @Override
  protected @NotNull ArendRef getVarTerm() {
    return varTerm;
  }

  @Override
  protected @Nullable BaseCommutativeGroupEquationMeta.MyHint<List<Monomial>> parseHint(@NotNull ConcreteExpression hint, @NotNull CoreExpression hintType, @NotNull List<TermOperation> operations, @NotNull Values<CoreExpression> values, @NotNull ExpressionTypechecker typechecker) {
    return BaseCommutativeGroupEquationMeta.parseCoefHint(hint, hintType, operations, values, typechecker, this);
  }
}
