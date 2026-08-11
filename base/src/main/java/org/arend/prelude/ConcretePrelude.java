package org.arend.prelude;

import org.arend.ext.ArendPrelude;
import org.arend.ext.core.definition.*;
import org.arend.ext.reference.ArendRef;
import org.arend.naming.scope.Scope;

import java.util.Arrays;

public class ConcretePrelude implements ArendPrelude {
  private final Scope myPreludeScope;

  public ConcretePrelude(Scope preludeScope) {
    myPreludeScope = preludeScope;
  }

  @Override
  public CoreDataDefinition getInterval() {
    return Prelude.INTERVAL;
  }

  @Override
  public CoreConstructor getLeft() {
    return Prelude.LEFT;
  }

  @Override
  public CoreConstructor getRight() {
    return Prelude.RIGHT;
  }

  @Override
  public CoreFunctionDefinition getSqueeze() {
    return Prelude.SQUEEZE;
  }

  @Override
  public CoreFunctionDefinition getSqueezeR() {
    return Prelude.SQUEEZE_R;
  }

  @Override
  public CoreDataDefinition getDI() {
    return Prelude.DI;
  }

  @Override
  public CoreConstructor getDLeft() {
    return Prelude.DLEFT;
  }

  @Override
  public CoreConstructor getDRight() {
    return Prelude.DRIGHT;
  }

  @Override
  public CoreDataDefinition getDPath() {
    return Prelude.DPATH;
  }

  @Override
  public CoreConstructor getDPathCon() {
    return Prelude.DPATH_CON;
  }

  @Override
  public CoreFunctionDefinition getDAt() {
    return Prelude.DAT;
  }

  @Override
  public CoreFunctionDefinition getHomType() {
    return Prelude.DPATH_INFIX;
  }

  @Override
  public CoreFunctionDefinition getFill2() {
    return Prelude.FILL2;
  }

  @Override
  public CoreFunctionDefinition getFill3() {
    return Prelude.FILL3;
  }

  @Override
  public CoreDataDefinition getNat() {
    return Prelude.NAT;
  }

  @Override
  public CoreConstructor getZero() {
    return Prelude.ZERO;
  }

  @Override
  public CoreConstructor getSuc() {
    return Prelude.SUC;
  }

  @Override
  public CoreFunctionDefinition getPlus() {
    return Prelude.PLUS;
  }

  @Override
  public CoreFunctionDefinition getMul() {
    return Prelude.MUL;
  }

  @Override
  public CoreFunctionDefinition getMinus() {
    return Prelude.MINUS;
  }

  @Override
  public CoreDataDefinition getFin() {
    return Prelude.FIN;
  }

  @Override
  public CoreDataDefinition getInt() {
    return Prelude.INT;
  }

  @Override
  public CoreConstructor getPos() {
    return Prelude.POS;
  }

  @Override
  public CoreConstructor getNeg() {
    return Prelude.NEG;
  }

  @Override
  public CoreDataDefinition getString() {
    return Prelude.STRING;
  }

  @Override
  public CoreFunctionDefinition getCoerce() {
    return Prelude.COERCE;
  }

  @Override
  public CoreFunctionDefinition getCoerce2() {
    return Prelude.COERCE2;
  }

  @Override
  public CoreDataDefinition getPath() {
    return Prelude.PATH;
  }

  @Override
  public CoreFunctionDefinition getEquality() {
    return Prelude.PATH_INFIX;
  }

  @Override
  public CoreConstructor getPathCon() {
    return Prelude.PATH_CON;
  }

  @Override
  public CoreFunctionDefinition getIdp() {
    return Prelude.IDP;
  }

  @Override
  public CoreFunctionDefinition getAt() {
    return Prelude.AT;
  }

  @Override
  public CoreFunctionDefinition getIso() {
    return Prelude.ISO;
  }

  @Override
  public CoreFunctionDefinition getDivMod() {
    return Prelude.DIV_MOD;
  }

  @Override
  public CoreFunctionDefinition getDiv() {
    return Prelude.DIV;
  }

  @Override
  public CoreFunctionDefinition getMod() {
    return Prelude.MOD;
  }

  @Override
  public CoreFunctionDefinition getDivModProp() {
    return Prelude.DIV_MOD_PROPERTY;
  }

  @Override
  public CoreClassDefinition getDArray() {
    return Prelude.DEP_ARRAY;
  }

  @Override
  public CoreFunctionDefinition getArray() {
    return Prelude.ARRAY;
  }

  @Override
  public CoreClassField getArrayElementsType() {
    return Prelude.ARRAY_ELEMENTS_TYPE;
  }

  @Override
  public CoreClassField getArrayLength() {
    return Prelude.ARRAY_LENGTH;
  }

  @Override
  public CoreClassField getArrayAt() {
    return Prelude.ARRAY_AT;
  }

  @Override
  public CoreFunctionDefinition getEmptyArray() {
    return Prelude.EMPTY_ARRAY;
  }

  @Override
  public CoreFunctionDefinition getArrayCons() {
    return Prelude.ARRAY_CONS;
  }

  @Override
  public CoreFunctionDefinition getArrayIndex() {
    return Prelude.ARRAY_INDEX;
  }

  @Override
  public ArendRef getIntervalRef() {
    if (Prelude.INTERVAL != null) return Prelude.INTERVAL.getRef();
    return myPreludeScope.resolveName("I");
  }

  @Override
  public ArendRef getLeftRef() {
    if (Prelude.LEFT != null) return Prelude.LEFT.getRef();
    return myPreludeScope.resolveName("left");
  }

  @Override
  public ArendRef getRightRef() {
    if (Prelude.RIGHT != null) return Prelude.RIGHT.getRef();
    return myPreludeScope.resolveName("right");
  }

  @Override
  public ArendRef getSqueezeRef() {
    if (Prelude.SQUEEZE != null) return Prelude.SQUEEZE.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("I", "squeeze"));
  }

  @Override
  public ArendRef getSqueezeRRef() {
    if (Prelude.SQUEEZE_R != null) return Prelude.SQUEEZE_R.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("I", "squeezeR"));
  }

  @Override
  public ArendRef getDIRef() {
    if (Prelude.DI != null) return Prelude.DI.getRef();
    return myPreludeScope.resolveName("DI");
  }

  @Override
  public ArendRef getDLeftRef() {
    if (Prelude.DLEFT != null) return Prelude.DLEFT.getRef();
    return myPreludeScope.resolveName("dleft");
  }

  @Override
  public ArendRef getDRightRef() {
    if (Prelude.DRIGHT != null) return Prelude.DRIGHT.getRef();
    return myPreludeScope.resolveName("dright");
  }

  @Override
  public ArendRef getDPathRef() {
    if (Prelude.DPATH != null) return Prelude.DPATH.getRef();
    return myPreludeScope.resolveName("DPath");
  }

  @Override
  public ArendRef getDPathConRef() {
    if (Prelude.DPATH_CON != null) return Prelude.DPATH_CON.getRef();
    return myPreludeScope.resolveName("dpath");
  }

  @Override
  public ArendRef getDAtRef() {
    if (Prelude.DAT != null) return Prelude.DAT.getRef();
    return myPreludeScope.resolveName("d@");
  }

  @Override
  public ArendRef getHomTypeRef() {
    if (Prelude.DPATH_INFIX != null) return Prelude.DPATH_INFIX.getRef();
    return myPreludeScope.resolveName("~>");
  }

  @Override
  public ArendRef getFill2Ref() {
    if (Prelude.FILL2 != null) return Prelude.FILL2.getRef();
    return myPreludeScope.resolveName("fill2");
  }

  @Override
  public ArendRef getFill3Ref() {
    if (Prelude.FILL3 != null) return Prelude.FILL3.getRef();
    return myPreludeScope.resolveName("fill3");
  }

  @Override
  public ArendRef getNatRef() {
    if (Prelude.NAT != null) return Prelude.NAT.getRef();
    return myPreludeScope.resolveName("Nat");
  }

  @Override
  public ArendRef getZeroRef() {
    if (Prelude.ZERO != null) return Prelude.ZERO.getRef();
    return myPreludeScope.resolveName("zero");
  }

  @Override
  public ArendRef getSucRef() {
    if (Prelude.SUC != null) return Prelude.SUC.getRef();
    return myPreludeScope.resolveName("suc");
  }

  @Override
  public ArendRef getPlusRef() {
    if (Prelude.PLUS != null) return Prelude.PLUS.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("Nat", "+"));
  }

  @Override
  public ArendRef getMulRef() {
    if (Prelude.MUL != null) return Prelude.MUL.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("Nat", "*"));
  }

  @Override
  public ArendRef getMinusRef() {
    if (Prelude.MINUS != null) return Prelude.MINUS.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("Nat", "-"));
  }

  @Override
  public ArendRef getFinRef() {
    if (Prelude.FIN != null) return Prelude.FIN.getRef();
    return myPreludeScope.resolveName("Fin");
  }

  @Override
  public ArendRef getIntRef() {
    if (Prelude.INT != null) return Prelude.INT.getRef();
    return myPreludeScope.resolveName("Int");
  }

  @Override
  public ArendRef getPosRef() {
    if (Prelude.POS != null) return Prelude.POS.getRef();
    return myPreludeScope.resolveName("pos");
  }

  @Override
  public ArendRef getNegRef() {
    if (Prelude.NEG != null) return Prelude.NEG.getRef();
    return myPreludeScope.resolveName("neg");
  }

  @Override
  public ArendRef getStringRef() {
    if (Prelude.STRING != null) return Prelude.STRING.getRef();
    return myPreludeScope.resolveName("String");
  }

  @Override
  public ArendRef getCoerceRef() {
    if (Prelude.COERCE != null) return Prelude.COERCE.getRef();
    return myPreludeScope.resolveName("coe");
  }

  @Override
  public ArendRef getCoerce2Ref() {
    if (Prelude.COERCE2 != null) return Prelude.COERCE2.getRef();
    return myPreludeScope.resolveName("coe2");
  }

  @Override
  public ArendRef getPathRef() {
    if (Prelude.PATH != null) return Prelude.PATH.getRef();
    return myPreludeScope.resolveName("Path");
  }

  @Override
  public ArendRef getEqualityRef() {
    if (Prelude.PATH_INFIX != null) return Prelude.PATH_INFIX.getRef();
    return myPreludeScope.resolveName("=");
  }

  @Override
  public ArendRef getPathConRef() {
    if (Prelude.PATH_CON != null) return Prelude.PATH_CON.getRef();
    return myPreludeScope.resolveName("path");
  }

  @Override
  public ArendRef getIdpRef() {
    if (Prelude.IDP != null) return Prelude.IDP.getRef();
    return myPreludeScope.resolveName("idp");
  }

  @Override
  public ArendRef getAtRef() {
    if (Prelude.AT != null) return Prelude.AT.getRef();
    return myPreludeScope.resolveName("@");
  }

  @Override
  public ArendRef getIsoRef() {
    if (Prelude.ISO != null) return Prelude.ISO.getRef();
    return myPreludeScope.resolveName("iso");
  }

  @Override
  public ArendRef getDivModRef() {
    if (Prelude.DIV_MOD != null) return Prelude.DIV_MOD.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("Nat", "divMod"));
  }

  @Override
  public ArendRef getDivRef() {
    if (Prelude.DIV != null) return Prelude.DIV.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("Nat", "div"));
  }

  @Override
  public ArendRef getModRef() {
    if (Prelude.MOD != null) return Prelude.MOD.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("Nat", "mod"));
  }

  @Override
  public ArendRef getDivModPropRef() {
    if (Prelude.DIV_MOD_PROPERTY != null) return Prelude.DIV_MOD_PROPERTY.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("Nat", "divModProp"));
  }

  @Override
  public ArendRef getDArrayRef() {
    if (Prelude.DEP_ARRAY != null) return Prelude.DEP_ARRAY.getRef();
    return myPreludeScope.resolveName("DArray");
  }

  @Override
  public ArendRef getArrayRef() {
    if (Prelude.ARRAY != null) return Prelude.ARRAY.getRef();
    return myPreludeScope.resolveName("Array");
  }

  @Override
  public ArendRef getArrayElementsTypeRef() {
    if (Prelude.ARRAY_ELEMENTS_TYPE != null) return Prelude.ARRAY_ELEMENTS_TYPE.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("DArray", "A"));
  }

  @Override
  public ArendRef getArrayLengthRef() {
    if (Prelude.ARRAY_LENGTH != null) return Prelude.ARRAY_LENGTH.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("DArray", "len"));
  }

  @Override
  public ArendRef getArrayAtRef() {
    if (Prelude.ARRAY_AT != null) return Prelude.ARRAY_AT.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("DArray", "at"));
  }

  @Override
  public ArendRef getEmptyArrayRef() {
    if (Prelude.EMPTY_ARRAY != null) return Prelude.EMPTY_ARRAY.getRef();
    return myPreludeScope.resolveName("nil");
  }

  @Override
  public ArendRef getArrayConsRef() {
    if (Prelude.ARRAY_CONS != null) return Prelude.ARRAY_CONS.getRef();
    return myPreludeScope.resolveName("::");
  }

  @Override
  public ArendRef getArrayIndexRef() {
    if (Prelude.ARRAY_INDEX != null) return Prelude.ARRAY_INDEX.getRef();
    return Scope.resolveName(myPreludeScope, Arrays.asList("DArray", "!!"));
  }
}
