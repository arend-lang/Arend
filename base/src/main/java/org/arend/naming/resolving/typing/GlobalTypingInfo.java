package org.arend.naming.resolving.typing;

import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class GlobalTypingInfo implements TypingInfo {
  private final TypingInfo myParent;
  private final Map<GlobalReferable, DynamicScopeProvider> myDynamicScopeProviderMap = new HashMap<>();
  private final Map<Referable, AbstractBody> myBodyMap = new HashMap<>();
  private final Map<Referable, AbstractBody> myTypeMap = new HashMap<>();

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

  public void addDynamicScopeProvider(GlobalReferable referable, DynamicScopeProvider provider) {
    myDynamicScopeProviderMap.put(referable, provider);
  }

  public void addReferableBody(Referable referable, AbstractBody body) {
    myBodyMap.put(referable, body);
  }

  public void addReferableType(Referable referable, AbstractBody type) {
    myTypeMap.put(referable, type);
  }
}
