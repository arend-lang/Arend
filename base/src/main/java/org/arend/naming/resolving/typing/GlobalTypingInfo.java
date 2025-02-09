package org.arend.naming.resolving.typing;

import org.arend.ext.reference.Precedence;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.reference.UnresolvedReference;
import org.arend.naming.scope.DynamicScope;
import org.arend.naming.scope.EmptyScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GlobalTypingInfo implements TypingInfo {
  private final TypingInfo myParent;
  private final Map<GlobalReferable, DynamicScopeProvider> myDynamicScopeProviderMap = new HashMap<>();
  private final Map<Referable, AbstractBody> myBodyMap = new HashMap<>();
  private final Map<Referable, AbstractBody> myTypeMap = new HashMap<>();
  private final Map<GlobalReferable, FieldReference> myPrecedenceMap = new HashMap<>();

  private record FieldReference(TCDefReferable referable, boolean isBodyRef, Referable fieldRef) {}

  public GlobalTypingInfo(TypingInfo parent) {
    myParent = parent;
  }

  @Override
  public @Nullable DynamicScopeProvider getDynamicScopeProvider(Referable referable) {
    if (referable instanceof GlobalReferable global) {
      DynamicScopeProvider provider = myDynamicScopeProviderMap.get(global);
      if (provider != null) {
        return provider;
      }
    }
    return myParent == null ? null : myParent.getDynamicScopeProvider(referable);
  }

  @Override
  public @Nullable AbstractBody getRefBody(Referable referable) {
    AbstractBody info = myBodyMap.get(referable);
    return info != null ? info : myParent != null ? myParent.getRefBody(referable) : null;
  }

  @Override
  public @Nullable AbstractBody getRefType(Referable referable) {
    AbstractBody info = myTypeMap.get(referable);
    return info != null ? info : myParent != null ? myParent.getRefType(referable) : null;
  }

  @Override
  public @NotNull Precedence getRefPrecedence(GlobalReferable referable, TypingInfo typingInfo) {
    if (referable.getKind() != GlobalReferable.Kind.COCLAUSE_FUNCTION) return referable.getPrecedence();
    FieldReference fieldRef = myPrecedenceMap.get(referable);
    if (fieldRef != null) {
      if (fieldRef.fieldRef instanceof GlobalReferable global) {
        return global.getPrecedence();
      }
      if (fieldRef.fieldRef instanceof UnresolvedReference unresolved) {
        if (!unresolved.isResolved()) {
          DynamicScopeProvider provider = fieldRef.isBodyRef ? typingInfo.getBodyDynamicScopeProvider(fieldRef.referable) : typingInfo.getAnyTypeDynamicScopeProvider(fieldRef.referable);
          if (provider != null) {
            unresolved.resolveExpression(new DynamicScope(provider, typingInfo, DynamicScope.Extent.ONLY_FIELDS), this, null, null);
          }
        }
        if (unresolved.isResolved()) {
          Referable ref = unresolved.tryResolve(EmptyScope.INSTANCE, null, null);
          return ref instanceof GlobalReferable global ? global.getPrecedence() : referable.getPrecedence();
        }
      }
    }
    return myParent == null ? referable.getPrecedence() : myParent.getRefPrecedence(referable);
  }

  public void addDynamicScopeProvider(GlobalReferable referable, DynamicScopeProvider provider) {
    myDynamicScopeProviderMap.put(referable, provider);
  }

  public void addReferableBody(Referable referable, AbstractBody body) {
    myBodyMap.put(referable, body);
  }

  public void addReferableType(Referable referable, AbstractBody type) {
    myTypeMap.put(referable, type);
  }

  public void addReferablePrecedence(GlobalReferable referable, TCDefReferable parent, boolean isBodyRef, Referable field) {
    myPrecedenceMap.put(referable, new FieldReference(parent, isBodyRef, field));
  }
}
