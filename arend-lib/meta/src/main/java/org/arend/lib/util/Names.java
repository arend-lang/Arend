package org.arend.lib.util;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.reference.ArendRef;

public class Names {
  private static final String LIBRARY_NAME = "arend-lib";

  public static ModulePath getLogicModule() {
    return new ModulePath("Logic");
  }

  public static ModulePath getListModule() {
    return ModulePath.fromString("Data.List");
  }

  public static ModulePath getSetModule() {
    return new ModulePath("Set");
  }

  public static boolean isEmpty(ArendRef ref) {
    return ref.checkName(LIBRARY_NAME, getLogicModule(), new LongName("Empty"));
  }

  public static boolean isAppend(ArendRef ref) {
    return ref.checkName(LIBRARY_NAME, getListModule(), new LongName("++"));
  }

  public static boolean isCons(ArendRef ref) {
    return ref.checkName(LIBRARY_NAME, getListModule(), LongName.fromString("List.::"));
  }

  public static String getNil() {
    return "nil";
  }

  public static boolean isNil(ArendRef ref) {
    return ref.checkName(LIBRARY_NAME, getListModule(), LongName.fromString("List.nil"));
  }

  public static boolean isBaseSet(ArendRef ref) {
    return ref.checkName(LIBRARY_NAME, getSetModule(), new LongName("BaseSet"));
  }

  public static boolean isCarrier(ArendRef ref) {
    return ref.checkName(LIBRARY_NAME, getSetModule(), LongName.fromString("BaseSet.E"));
  }

  public static boolean isSetHierarchy(CoreClassDefinition definition) {
    return definition.findAncestor(superClass -> superClass.getSuperClasses().isEmpty() && isBaseSet(superClass.getRef())) != null;
  }
}
