package org.arend.lib.meta.equationNew.group;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.util.Lazy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AbelianGroupEquationMeta extends BaseCommutativeGroupEquationMeta {
  @Dependency                             CoreClassDefinition AbGroup;
  @Dependency(name = "AddPointed.zro")    CoreClassField zro;
  @Dependency(name = "AddMonoid.+")       CoreClassField add;
  @Dependency(name = "AddGroup.negative") CoreClassField negative;
  @Dependency                             ArendRef AbGroupSolverModel;
  @Dependency(name = "AbGroupSolverModel.terms-equality")       ArendRef termsEquality;
  @Dependency(name = "AbGroupSolverModel.terms-equality-conv")  ArendRef termsEqualityConv;
  @Dependency(name = "AbGroupSolverModel.apply-axioms")         ArendRef applyAxioms;

  @Override
  protected @NotNull ConcreteExpression getTermsEquality(@NotNull Lazy<ArendRef> solverRef, @Nullable ConcreteExpression solver, @NotNull ConcreteFactory factory) {
    return factory.ref(termsEquality);
  }

  @Override
  protected @NotNull ConcreteExpression getTermsEqualityConv(@NotNull Lazy<ArendRef> solverRef, @NotNull ConcreteFactory factory) {
    return factory.ref(termsEqualityConv);
  }

  @Override
  protected @NotNull CoreClassDefinition getClassDef() {
    return AbGroup;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return AbGroupSolverModel;
  }

  @Override
  protected boolean isMultiplicative() {
    return false;
  }

  @Override
  protected CoreClassField getIde() {
    return zro;
  }

  @Override
  protected CoreClassField getMul() {
    return add;
  }

  @Override
  protected CoreClassField getInverse() {
    return negative;
  }

  @Override
  protected @NotNull ArendRef getApplyAxiom() {
    return applyAxioms;
  }
}
