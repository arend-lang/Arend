package org.arend.lib.meta.equationNew;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;

public class GroupEquationMeta extends BaseGroupEquationMeta {
  @Dependency                         CoreClassDefinition Group;
  @Dependency(name = "Pointed.ide")   CoreClassField ide;
  @Dependency(name = "Semigroup.*")   CoreClassField mul;
  @Dependency(name = "Group.inverse") CoreClassField inverse;
  @Dependency                         ArendRef GroupSolverModel;

  @Override
  protected CoreClassDefinition getClassDef() {
    return Group;
  }

  @Override
  protected ArendRef getSolverModel() {
    return GroupSolverModel;
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
}
