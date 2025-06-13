package org.arend.lib.meta.equationNew;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;

public class MonoidEquationMeta extends BaseMonoidEquationMeta {
  @Dependency ArendRef              MonoidSolverModel;
  @Dependency CoreClassDefinition   Monoid;
  @Dependency(name = "Pointed.ide") CoreClassField ide;
  @Dependency(name = "Semigroup.*") CoreClassField mul;

  public MonoidEquationMeta() {
    super(false);
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
    return Monoid;
  }

  @Override
  protected ArendRef getSolverModel() {
    return MonoidSolverModel;
  }
}
