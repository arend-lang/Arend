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
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

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

  /**
   * Compares this typing info with {@code other}.
   * Global referables are compared by identity, local ones by name.
   *
   * @return null if they give the same answers, and a description of the difference otherwise.
   */
  public @Nullable String difference(GlobalTypingInfo other) {
    if (myParent != other.myParent) return "parent typing info differs";
    String difference = mapDifference("body of ", myBodyMap, other.myBodyMap, GlobalTypingInfo::sameBody);
    if (difference == null) difference = mapDifference("type of ", myTypeMap, other.myTypeMap, GlobalTypingInfo::sameBody);
    if (difference == null) difference = mapDifference("implemented field of ", myPrecedenceMap, other.myPrecedenceMap, (ref1, ref2) -> ref1.referable == ref2.referable && ref1.isBodyRef == ref2.isBodyRef && sameReferable(ref1.fieldRef, ref2.fieldRef));
    if (difference == null) difference = mapDifference("dynamic scope of ", myDynamicScopeProviderMap, other.myDynamicScopeProviderMap, GlobalTypingInfo::sameProvider);
    return difference;
  }

  private static <K extends Referable,V> String mapDifference(String what, Map<K,V> map1, Map<K,V> map2, BiPredicate<V,V> sameValues) {
    for (Map.Entry<K,V> entry : map1.entrySet()) {
      V value = map2.get(entry.getKey());
      if (value == null || !sameValues.test(entry.getValue(), value)) return what + "'" + entry.getKey().getRefName() + "' differs";
    }
    for (K key : map2.keySet()) {
      if (!map1.containsKey(key)) return what + "'" + key.getRefName() + "' differs";
    }
    return null;
  }

  private static boolean sameReferable(Referable ref1, Referable ref2) {
    if (ref1 == ref2) return true;
    if (ref1 == null || ref2 == null || ref1 instanceof GlobalReferable || ref2 instanceof GlobalReferable) return false;
    return ref1.getClass().equals(ref2.getClass()) && ref1.textRepresentation().equals(ref2.textRepresentation());
  }

  private static boolean sameBody(AbstractBody body1, AbstractBody body2) {
    return body1.getParameters() == body2.getParameters() && body1.getArguments() == body2.getArguments() && sameReferable(body1.getReferable(), body2.getReferable());
  }

  private static boolean sameProvider(DynamicScopeProvider provider1, DynamicScopeProvider provider2) {
    return provider1 instanceof DynamicScopeProviderImpl impl1 && provider2 instanceof DynamicScopeProviderImpl impl2 &&
      impl1.getReferable() == impl2.getReferable() &&
      sameList(impl1.getSuperReferables(), impl2.getSuperReferables()) &&
      sameList(impl1.getDynamicContent(), impl2.getDynamicContent());
  }

  private static boolean sameList(List<? extends GlobalReferable> list1, List<? extends GlobalReferable> list2) {
    if (list1.size() != list2.size()) return false;
    for (int i = 0; i < list1.size(); i++) {
      if (list1.get(i) != list2.get(i)) return false;
    }
    return true;
  }

  public void addReferablePrecedence(GlobalReferable referable, TCDefReferable parent, boolean isBodyRef, Referable field) {
    myPrecedenceMap.put(referable, new FieldReference(parent, isBodyRef, field));
  }
}
