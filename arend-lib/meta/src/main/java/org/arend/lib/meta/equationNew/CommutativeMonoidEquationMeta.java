package org.arend.lib.meta.equationNew;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;

public class CommutativeMonoidEquationMeta extends BaseMonoidEquationMeta {
  @Dependency                       ArendRef CMonoidSolverModel;
  @Dependency                       CoreClassDefinition CMonoid;
  @Dependency(name = "Pointed.ide") CoreClassField ide;
  @Dependency(name = "Semigroup.*") CoreClassField mul;

  public CommutativeMonoidEquationMeta() {
    super(true);
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
  protected @NotNull CoreClassDefinition getClassDef() {
    return CMonoid;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return CMonoidSolverModel;
  }
}
