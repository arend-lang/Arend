package org.arend.lib.meta.equationNew;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;

public class AdditiveGroupEquationMeta extends BaseGroupEquationMeta {
  @Dependency                             CoreClassDefinition AddGroup;
  @Dependency(name = "AddPointed.zro")    CoreClassField zro;
  @Dependency(name = "AddMonoid.+")       CoreClassField add;
  @Dependency(name = "AddGroup.negative") CoreClassField negative;
  @Dependency                             ArendRef AddGroupSolverModel;

  @Override
  protected CoreClassDefinition getClassDef() {
    return AddGroup;
  }

  @Override
  protected ArendRef getSolverModel() {
    return AddGroupSolverModel;
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
}
