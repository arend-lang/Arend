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

public class CommutativeGroupEquationMeta extends BaseCommutativeGroupEquationMeta {
  @Dependency                         CoreClassDefinition CGroup;
  @Dependency(name = "Pointed.ide")   CoreClassField ide;
  @Dependency(name = "Semigroup.*")   CoreClassField mul;
  @Dependency(name = "Group.inverse") CoreClassField inverse;
  @Dependency                         ArendRef CGroupSolverModel;
  @Dependency(name = "CGroupSolverModel.terms-equality")      ArendRef termsEquality;
  @Dependency(name = "CGroupSolverModel.terms-equality-conv") ArendRef termsEqualityConv;
  @Dependency(name = "CGroupSolverModel.apply-axioms")        ArendRef applyAxioms;

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
    return CGroup;
  }

  @Override
  protected boolean isMultiplicative() {
    return true;
  }

  @Override
  protected CoreClassField getIde() {
    return ide;
  }

  @Override
  protected CoreClassField getMul() {
    return mul;
  }

  @Override
  protected CoreClassField getInverse() {
    return inverse;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return CGroupSolverModel;
  }

  @Override
  protected @NotNull ArendRef getApplyAxiom() {
    return applyAxioms;
  }
}
