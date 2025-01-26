package org.arend.naming.resolving.typing;

import org.arend.naming.reference.ParameterReferable;
import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LocalTypingInfo implements TypingInfo {
  private final TypingInfo myParent;
  private final List<TypedReferable> myContext;

  public LocalTypingInfo(TypingInfo parent, List<TypedReferable> context) {
    myParent = parent;
    myContext = context;
  }

  @Override
  public @Nullable DynamicScopeProvider getDynamicScopeProvider(Referable referable) {
    return myParent.getDynamicScopeProvider(referable);
  }

  @Override
  public @Nullable AbstractBody getRefBody(Referable referable) {
    return myParent.getRefBody(referable);
  }

  @Override
  public @Nullable AbstractBody getRefType(Referable referable) {
    if (referable instanceof ParameterReferable paramRef) {
      return paramRef.getAbstractBody();
    }

    AbstractBody info = myParent.getRefType(referable);
    if (info != null) return info;
    for (int i = myContext.size() - 1; i >= 0; i--) {
      if (myContext.get(i).getReferable().equals(referable)) {
        return myContext.get(i).getAbstractBody();
      }
    }
    return null;
  }
}
