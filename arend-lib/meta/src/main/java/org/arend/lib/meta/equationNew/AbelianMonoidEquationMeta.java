package org.arend.lib.meta.equationNew;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AbelianMonoidEquationMeta extends BaseCommutativeMonoidEquationMeta {
  @Dependency                           ArendRef AbMonoidSolverModel;
  @Dependency                           CoreClassDefinition AbMonoid;
  @Dependency(name = "AddPointed.zro")  CoreClassField zro;
  @Dependency(name = "AddMonoid.+")     CoreClassField add;
  @Dependency(name = "AbMonoidSolverModel.apply-axiom") ArendRef applyAxiom;

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
  protected @NotNull CoreClassDefinition getClassDef() {
    return AbMonoid;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return AbMonoidSolverModel;
  }

  @Override
  protected @Nullable ArendRef getApplyAxiom() {
    return applyAxiom;
  }
}