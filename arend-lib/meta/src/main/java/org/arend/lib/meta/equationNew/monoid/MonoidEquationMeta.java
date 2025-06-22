package org.arend.lib.meta.equationNew.monoid;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;
import org.jetbrains.annotations.NotNull;

public class MonoidEquationMeta extends NonCommutativeMonoidEquationMeta {
  @Dependency ArendRef              MonoidSolverModel;
  @Dependency CoreClassDefinition   Monoid;
  @Dependency(name = "Pointed.ide") CoreClassField ide;
  @Dependency(name = "Semigroup.*") CoreClassField mul;

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
    return Monoid;
  }

  @Override
  protected @NotNull ArendRef getSolverModel() {
    return MonoidSolverModel;
  }
}
