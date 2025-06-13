package org.arend.lib.meta.equationNew;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;

public class CommutativeMonoidEquationMeta extends BaseMonoidEquationMeta {
  @Dependency ArendRef CMonoidSolverModel;
  @Dependency CoreClassDefinition CMonoid;
  @Dependency(name = "Pointed.ide") CoreClassField ide;
  @Dependency(name = "Semigroup.*") CoreClassField mul;

  public CommutativeMonoidEquationMeta() {
    super(true);
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
  protected CoreClassDefinition getMonoid() {
    return CMonoid;
  }

  @Override
  protected ArendRef getSolverModel() {
    return CMonoidSolverModel;
  }
}
