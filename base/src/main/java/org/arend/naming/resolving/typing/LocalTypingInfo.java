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
  public @Nullable ReferableInfo getBodyInfo(Referable referable) {
    return myParent.getBodyInfo(referable);
  }

  @Override
  public @Nullable ReferableInfo getTypeInfo(Referable referable) {
    if (referable instanceof ParameterReferable paramRef) {
      return paramRef.getReferableInfo();
    }

    ReferableInfo info = myParent.getTypeInfo(referable);
    if (info != null) return info;
    for (int i = myContext.size() - 1; i >= 0; i--) {
      if (myContext.get(i).getReferable().equals(referable)) {
        return myContext.get(i);
      }
    }
    return null;
  }
}
