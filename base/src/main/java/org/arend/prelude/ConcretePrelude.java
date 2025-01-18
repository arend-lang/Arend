package org.arend.prelude;

import org.arend.ext.ArendPrelude;
import org.arend.ext.core.definition.*;
import org.arend.ext.reference.ArendRef;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.Group;

import java.util.HashMap;
import java.util.Map;

public class ConcretePrelude implements ArendPrelude {
  private final ConcreteGroup myPreludeGroup;
  private Map<String, ArendRef> myMap;

  public ConcretePrelude(ConcreteGroup preludeGroup) {
    myPreludeGroup = preludeGroup;
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
  public CoreFunctionDefinition getFinFromNat() {
    return Prelude.FIN_FROM_NAT;
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

  private void init() {
    if (myMap == null) {
      myMap = new HashMap<>();
      myPreludeGroup.traverseGroup(group -> {
        myMap.put(group.getReferable().getRefName(), group.getReferable());
        for (Group.InternalReferable internal : group.getInternalReferables()) {
          ArendRef ref = internal.getReferable();
          myMap.put(ref.getRefName(), ref);
        }
      });
    }
  }

  @Override
  public ArendRef getIntervalRef() {
    if (Prelude.INTERVAL != null) return Prelude.INTERVAL.getRef();
    init();
    return myMap.get("I");
  }

  @Override
  public ArendRef getLeftRef() {
    if (Prelude.LEFT != null) return Prelude.LEFT.getRef();
    init();
    return myMap.get("left");
  }

  @Override
  public ArendRef getRightRef() {
    if (Prelude.RIGHT != null) return Prelude.RIGHT.getRef();
    init();
    return myMap.get("right");
  }

  @Override
  public ArendRef getSqueezeRef() {
    if (Prelude.SQUEEZE != null) return Prelude.SQUEEZE.getRef();
    init();
    return myMap.get("squeeze");
  }

  @Override
  public ArendRef getSqueezeRRef() {
    if (Prelude.SQUEEZE_R != null) return Prelude.SQUEEZE_R.getRef();
    init();
    return myMap.get("squeezeR");
  }

  @Override
  public ArendRef getNatRef() {
    if (Prelude.NAT != null) return Prelude.NAT.getRef();
    init();
    return myMap.get("Nat");
  }

  @Override
  public ArendRef getZeroRef() {
    if (Prelude.ZERO != null) return Prelude.ZERO.getRef();
    init();
    return myMap.get("zero");
  }

  @Override
  public ArendRef getSucRef() {
    if (Prelude.SUC != null) return Prelude.SUC.getRef();
    init();
    return myMap.get("suc");
  }

  @Override
  public ArendRef getPlusRef() {
    if (Prelude.PLUS != null) return Prelude.PLUS.getRef();
    init();
    return myMap.get("+");
  }

  @Override
  public ArendRef getMulRef() {
    if (Prelude.MUL != null) return Prelude.MUL.getRef();
    init();
    return myMap.get("*");
  }

  @Override
  public ArendRef getMinusRef() {
    if (Prelude.MINUS != null) return Prelude.MINUS.getRef();
    init();
    return myMap.get("-");
  }

  @Override
  public ArendRef getFinRef() {
    if (Prelude.FIN != null) return Prelude.FIN.getRef();
    init();
    return myMap.get("Fin");
  }

  @Override
  public ArendRef getFinFromNatRef() {
    if (Prelude.FIN_FROM_NAT != null) return Prelude.FIN_FROM_NAT.getRef();
    init();
    return myMap.get("fromNat");
  }

  @Override
  public ArendRef getIntRef() {
    if (Prelude.INT != null) return Prelude.INT.getRef();
    init();
    return myMap.get("Int");
  }

  @Override
  public ArendRef getPosRef() {
    if (Prelude.POS != null) return Prelude.POS.getRef();
    init();
    return myMap.get("pos");
  }

  @Override
  public ArendRef getNegRef() {
    if (Prelude.NEG != null) return Prelude.NEG.getRef();
    init();
    return myMap.get("neg");
  }

  @Override
  public ArendRef getStringRef() {
    if (Prelude.STRING != null) return Prelude.STRING.getRef();
    init();
    return myMap.get("String");
  }

  @Override
  public ArendRef getCoerceRef() {
    if (Prelude.COERCE != null) return Prelude.COERCE.getRef();
    init();
    return myMap.get("coe");
  }

  @Override
  public ArendRef getCoerce2Ref() {
    if (Prelude.COERCE2 != null) return Prelude.COERCE2.getRef();
    init();
    return myMap.get("coe2");
  }

  @Override
  public ArendRef getPathRef() {
    if (Prelude.PATH != null) return Prelude.PATH.getRef();
    init();
    return myMap.get("Path");
  }

  @Override
  public ArendRef getEqualityRef() {
    if (Prelude.PATH_INFIX != null) return Prelude.PATH_INFIX.getRef();
    init();
    return myMap.get("=");
  }

  @Override
  public ArendRef getPathConRef() {
    if (Prelude.PATH_CON != null) return Prelude.PATH_CON.getRef();
    init();
    return myMap.get("path");
  }

  @Override
  public ArendRef getIdpRef() {
    if (Prelude.IDP != null) return Prelude.IDP.getRef();
    init();
    return myMap.get("idp");
  }

  @Override
  public ArendRef getAtRef() {
    if (Prelude.AT != null) return Prelude.AT.getRef();
    init();
    return myMap.get("@");
  }

  @Override
  public ArendRef getIsoRef() {
    if (Prelude.ISO != null) return Prelude.ISO.getRef();
    init();
    return myMap.get("iso");
  }

  @Override
  public ArendRef getDivModRef() {
    if (Prelude.DIV_MOD != null) return Prelude.DIV_MOD.getRef();
    init();
    return myMap.get("divMod");
  }

  @Override
  public ArendRef getDivRef() {
    if (Prelude.DIV != null) return Prelude.DIV.getRef();
    init();
    return myMap.get("div");
  }

  @Override
  public ArendRef getModRef() {
    if (Prelude.MOD != null) return Prelude.MOD.getRef();
    init();
    return myMap.get("mod");
  }

  @Override
  public ArendRef getDivModPropRef() {
    if (Prelude.DIV_MOD_PROPERTY != null) return Prelude.DIV_MOD_PROPERTY.getRef();
    init();
    return myMap.get("divModProp");
  }

  @Override
  public ArendRef getDArrayRef() {
    if (Prelude.DEP_ARRAY != null) return Prelude.DEP_ARRAY.getRef();
    init();
    return myMap.get("DArray");
  }

  @Override
  public ArendRef getArrayRef() {
    if (Prelude.ARRAY != null) return Prelude.ARRAY.getRef();
    init();
    return myMap.get("Array");
  }

  @Override
  public ArendRef getArrayElementsTypeRef() {
    if (Prelude.ARRAY_ELEMENTS_TYPE != null) return Prelude.ARRAY_ELEMENTS_TYPE.getRef();
    init();
    return myMap.get("A");
  }

  @Override
  public ArendRef getArrayLengthRef() {
    if (Prelude.ARRAY_LENGTH != null) return Prelude.ARRAY_LENGTH.getRef();
    init();
    return myMap.get("len");
  }

  @Override
  public ArendRef getArrayAtRef() {
    if (Prelude.ARRAY_AT != null) return Prelude.ARRAY_AT.getRef();
    init();
    return myMap.get("at");
  }

  @Override
  public ArendRef getEmptyArrayRef() {
    if (Prelude.EMPTY_ARRAY != null) return Prelude.EMPTY_ARRAY.getRef();
    init();
    return myMap.get("nil");
  }

  @Override
  public ArendRef getArrayConsRef() {
    if (Prelude.ARRAY_CONS != null) return Prelude.ARRAY_CONS.getRef();
    init();
    return myMap.get("::");
  }

  @Override
  public ArendRef getArrayIndexRef() {
    if (Prelude.ARRAY_INDEX != null) return Prelude.ARRAY_INDEX.getRef();
    init();
    return myMap.get("!!");
  }
}
