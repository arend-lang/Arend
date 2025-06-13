package org.arend.lib.meta.equationNew;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;

public class AbelianMonoidEquationMeta extends BaseMonoidEquationMeta {
  @Dependency                           ArendRef AbMonoidSolverModel;
  @Dependency                           CoreClassDefinition AbMonoid;
  @Dependency(name = "AddPointed.zro")  CoreClassField zro;
  @Dependency(name = "AddMonoid.+")     CoreClassField add;

  public AbelianMonoidEquationMeta() {
    super(true);
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
  protected CoreClassDefinition getMonoid() {
    return AbMonoid;
  }

  @Override
  protected ArendRef getSolverModel() {
    return AbMonoidSolverModel;
  }
}