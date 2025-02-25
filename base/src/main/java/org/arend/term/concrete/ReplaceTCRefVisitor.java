package org.arend.term.concrete;

import org.arend.naming.reference.*;

import java.util.HashMap;
import java.util.Map;

public class ReplaceTCRefVisitor extends ReplaceDataVisitor {
  private final Map<TCDefReferable, TCDefReferable> myTCMap = new HashMap<>();

  protected Referable copyRef(Referable referable) {
    if (referable instanceof TCDefReferable) {
      TCDefReferable newRef = myTCMap.get(referable);
      return newRef == null ? referable : newRef;
    } else {
      return referable;
    }
  }

  private LocatedReferableImpl copyTCReferable(TCDefReferable referable) {
    LocatedReferableImpl result = new LocatedReferableImpl(referable.getData(), referable.getAccessModifier(), referable.getPrecedence(), referable.getRefName(), referable.getAliasPrecedence(), referable.getAliasName(), referable.getLocatedReferableParent(), referable.getKind());
    myTCMap.put(referable, result);
    return result;
  }

  @Override
  public Concrete.BaseFunctionDefinition visitFunction(Concrete.BaseFunctionDefinition def, Void params) {
    LocatedReferableImpl newRef = copyTCReferable(def.getData());
    Concrete.BaseFunctionDefinition result = super.visitFunction(def, params);
    result.setReferable(newRef);
    return result;
  }

  @Override
  public Concrete.DataDefinition visitData(Concrete.DataDefinition def, Void params) {
    LocatedReferableImpl dataRef = copyTCReferable(def.getData());
    for (Concrete.ConstructorClause clause : def.getConstructorClauses()) {
      for (Concrete.Constructor constructor : clause.getConstructors()) {
        InternalReferable conRef = constructor.getData();
        myTCMap.put(conRef, new InternalReferableImpl(conRef.getData(), conRef.getAccessModifier(), conRef.getPrecedence(), conRef.getRefName(), conRef.getAliasPrecedence(), conRef.getAliasName(), conRef.isVisible(), conRef.getLocatedReferableParent(), conRef.getKind()));
      }
    }

    Concrete.DataDefinition result = super.visitData(def, params);
    result.setReferable(dataRef);
    for (Concrete.ConstructorClause clause : result.getConstructorClauses()) {
      for (Concrete.Constructor constructor : clause.getConstructors()) {
        constructor.setReferable((InternalReferable) myTCMap.get(constructor.getData()));
      }
    }

    return result;
  }

  @Override
  public Concrete.ClassDefinition visitClass(Concrete.ClassDefinition def, Void params) {
    LocatedReferableImpl classRef = copyTCReferable(def.getData());
    for (Concrete.ClassElement element : def.getElements()) {
      if (element instanceof Concrete.ClassField field) {
        FieldReferableImpl fieldRef = field.getData();
        myTCMap.put(fieldRef, new FieldReferableImpl(fieldRef.getData(), fieldRef.getAccessModifier(), fieldRef.getPrecedence(), fieldRef.getRefName(), fieldRef.getAliasPrecedence(), fieldRef.getAliasName(), fieldRef.isExplicitField(), fieldRef.isParameterField(), fieldRef.isRealParameterField(), fieldRef.getLocatedReferableParent()));
      }
    }

    Concrete.ClassDefinition result = super.visitClass(def, params);
    result.setReferable(classRef);
    for (Concrete.ClassElement element : result.getElements()) {
      if (element instanceof Concrete.ClassField field) {
        field.setReferable((FieldReferableImpl) myTCMap.get(field.getData()));
      }
    }

    return result;
  }

  @Override
  public DefinableMetaDefinition visitMeta(DefinableMetaDefinition def, Void params) {
    MetaReferable metaRef = def.getData();
    MetaReferable newRef = new MetaReferable(metaRef.getData(), metaRef.getAccessModifier(), metaRef.getPrecedence(), metaRef.getRefName(), metaRef.getAliasPrecedence(), metaRef.getAliasName(), metaRef.getDefinition(), metaRef.getResolver(), metaRef.getLocatedReferableParent());
    myTCMap.put(metaRef, newRef);

    DefinableMetaDefinition result = super.visitMeta(def, params);
    result.setReferable(newRef);
    return result;
  }
}
