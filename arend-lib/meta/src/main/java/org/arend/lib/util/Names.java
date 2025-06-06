package org.arend.lib.util;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;

public class Names {
  private static final String LIBRARY_NAME = "arend-lib";
  public static final FullName EMPTY = fullName(getLogicModule(), new LongName("Empty"));
  public static final FullName APPEND = fullName(getListModule(), new LongName("++"));
  public static final FullName CONS = fullName(getListModule(), new LongName("List", "::"));
  public static final FullName NIL = fullName(getListModule(), new LongName("List", "nil"));
  public static final FullName BASE_SET = fullName(getSetModule(), new LongName("BaseSet"));
  public static final FullName CARRIER = fullName(getSetModule(), new LongName("BaseSet", "E"));
  public static final FullName ZRO = fullName(getPointedModule(), new LongName("AddPointed", "zro"));
  public static final FullName IDE = fullName(getPointedModule(), new LongName("Pointed", "ide"));
  public static final FullName NEGATIVE = fullName(getGroupModule(), new LongName("AddGroup", "negative"));
  public static final FullName NAT_COEF = fullName(getSemiringModule(), new LongName("Semiring", "natCoef"));

  private static FullName fullName(ModulePath modulePath, LongName longName) {
    return new FullName(new ModuleLocation(LIBRARY_NAME, ModuleLocation.LocationKind.SOURCE, modulePath), longName);
  }

  public static boolean isSetHierarchy(CoreClassDefinition definition) {
    return definition.findAncestor(superClass -> superClass.getSuperClasses().isEmpty() && superClass.getRef().checkName(BASE_SET)) != null;
  }

  public static String getNil() {
    return "nil";
  }

  // Modules

  public static ModulePath getAlgebraModule() {
    return new ModulePath("Algebra", "Algebra");
  }

  public static ModulePath getGroupModule() {
    return new ModulePath("Algebra", "Group");
  }

  public static ModulePath getGroupSolverModule() {
    return new ModulePath("Algebra", "Group", "Solver");
  }

  public static ModulePath getLinearSolverModule() {
    return new ModulePath("Algebra", "Linear", "Solver");
  }

  public static ModulePath getModuleModule() {
    return new ModulePath("Algebra", "Module");
  }

  public static ModulePath getMonoidModule() {
    return new ModulePath("Algebra", "Monoid");
  }

  public static ModulePath getMonoidSolverModule() {
    return new ModulePath("Algebra", "Monoid", "Solver");
  }

  public static ModulePath getOrderedModule() {
    return new ModulePath("Algebra", "Ordered");
  }

  public static ModulePath getPointedModule() {
    return new ModulePath("Algebra", "Pointed");
  }

  public static ModulePath getRingModule() {
    return new ModulePath("Algebra", "Ring");
  }

  public static ModulePath getRingSolverModule() {
    return new ModulePath("Algebra", "Ring", "Solver");
  }

  public static ModulePath getSemiringModule() {
    return new ModulePath("Algebra", "Semiring");
  }

  public static ModulePath getIntModule() {
    return new ModulePath("Arith", "Int");
  }

  public static ModulePath getNatModule() {
    return new ModulePath("Arith", "Nat");
  }

  public static ModulePath getRatModule() {
    return new ModulePath("Arith", "Rat");
  }

  public static ModulePath getCategoryModule() {
    return new ModulePath("Category");
  }

  public static ModulePath getCategorySolverModule() {
    return new ModulePath("Category", "Solver");
  }

  public static ModulePath getBoolModule() {
    return new ModulePath("Data", "Bool");
  }

  public static ModulePath getListModule() {
    return new ModulePath("Data", "List");
  }

  public static ModulePath getEquivModule() {
    return new ModulePath("Equiv");
  }

  public static ModulePath getUnivalenceModule() {
    return new ModulePath("Equiv", "Univalence");
  }

  public static ModulePath getLogicModule() {
    return new ModulePath("Logic");
  }

  public static ModulePath getLatticeModule() {
    return new ModulePath("Order", "Lattice");
  }

  public static ModulePath getBiorderedModule() {
    return new ModulePath("Order", "Biordered");
  }

  public static ModulePath getLinearOrderModule() {
    return new ModulePath("Order", "LinearOrder");
  }

  public static ModulePath getPartialOrderModule() {
    return new ModulePath("Order", "PartialOrder");
  }

  public static ModulePath getStrictOrderModule() {
    return new ModulePath("Order", "StrictOrder");
  }

  public static ModulePath getPathsModule() {
    return new ModulePath("Paths");
  }

  public static ModulePath getSetModule() {
    return new ModulePath("Set");
  }

  public static ModulePath getSetCategoryModule() {
    return new ModulePath("Set", "SetCategory");
  }

  public static ModulePath getSetHomModule() {
    return new ModulePath("Set", "SetHom");
  }
}
