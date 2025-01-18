package org.arend.ext;

import org.arend.ext.core.definition.*;
import org.arend.ext.reference.ArendRef;

/**
 * Provides access to the definitions in the prelude.
 */
public interface ArendPrelude {
  CoreDataDefinition getInterval();
  CoreConstructor getLeft();
  CoreConstructor getRight();
  CoreFunctionDefinition getSqueeze();
  CoreFunctionDefinition getSqueezeR();
  CoreDataDefinition getNat();
  CoreConstructor getZero();
  CoreConstructor getSuc();
  CoreFunctionDefinition getPlus();
  CoreFunctionDefinition getMul();
  CoreFunctionDefinition getMinus();
  CoreDataDefinition getFin();
  CoreFunctionDefinition getFinFromNat();
  CoreDataDefinition getInt();
  CoreConstructor getPos();
  CoreConstructor getNeg();
  CoreDataDefinition getString();
  CoreFunctionDefinition getCoerce();
  CoreFunctionDefinition getCoerce2();
  CoreDataDefinition getPath();
  CoreFunctionDefinition getEquality();
  CoreConstructor getPathCon();
  CoreFunctionDefinition getIdp();
  CoreFunctionDefinition getAt();
  CoreFunctionDefinition getIso();
  CoreFunctionDefinition getDivMod();
  CoreFunctionDefinition getDiv();
  CoreFunctionDefinition getMod();
  CoreFunctionDefinition getDivModProp();
  CoreClassDefinition getDArray();
  CoreFunctionDefinition getArray();
  CoreClassField getArrayElementsType();
  CoreClassField getArrayLength();
  CoreClassField getArrayAt();
  CoreFunctionDefinition getEmptyArray();
  CoreFunctionDefinition getArrayCons();
  CoreFunctionDefinition getArrayIndex();

  ArendRef getIntervalRef();
  ArendRef getLeftRef();
  ArendRef getRightRef();
  ArendRef getSqueezeRef();
  ArendRef getSqueezeRRef();
  ArendRef getNatRef();
  ArendRef getZeroRef();
  ArendRef getSucRef();
  ArendRef getPlusRef();
  ArendRef getMulRef();
  ArendRef getMinusRef();
  ArendRef getFinRef();
  ArendRef getFinFromNatRef();
  ArendRef getIntRef();
  ArendRef getPosRef();
  ArendRef getNegRef();
  ArendRef getStringRef();
  ArendRef getCoerceRef();
  ArendRef getCoerce2Ref();
  ArendRef getPathRef();
  ArendRef getEqualityRef();
  ArendRef getPathConRef();
  ArendRef getIdpRef();
  ArendRef getAtRef();
  ArendRef getIsoRef();
  ArendRef getDivModRef();
  ArendRef getDivRef();
  ArendRef getModRef();
  ArendRef getDivModPropRef();
  ArendRef getDArrayRef();
  ArendRef getArrayRef();
  ArendRef getArrayElementsTypeRef();
  ArendRef getArrayLengthRef();
  ArendRef getArrayAtRef();
  ArendRef getEmptyArrayRef();
  ArendRef getArrayConsRef();
  ArendRef getArrayIndexRef();
}
