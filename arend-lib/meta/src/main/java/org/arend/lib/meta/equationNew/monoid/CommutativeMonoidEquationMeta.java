package org.arend.lib.meta.equationNew.monoid;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;

public class CommutativeMonoidEquationMeta extends BaseCommutativeMonoidEquationMeta {
  @Dependency                       ArendRef CMonoidSolverModel;
  @Dependency                       CoreClassDefinition CMonoid;
  @Dependency(name = "Pointed.ide") CoreClassField ide;
  @Dependency(name = "Semigroup.*") CoreClassField mul;
  @Dependency(name = "CMonoidSolverModel.apply-axiom")  ArendRef applyAxiom;

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
  protected @NotNull CoreClassDefinition getClassDef() {
    return CMonoid;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return CMonoidSolverModel;
  }

  @Override
  protected @NotNull ArendRef getApplyAxiom() {
    return applyAxiom;
  }
}
