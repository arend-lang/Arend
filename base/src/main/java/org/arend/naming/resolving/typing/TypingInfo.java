package org.arend.naming.resolving.typing;

import org.arend.naming.reference.ClassReferable;
import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.Nullable;

public interface TypingInfo {
  @Nullable ReferableInfo getBodyInfo(Referable referable);
  @Nullable ReferableInfo getTypeInfo(Referable referable);

  default @Nullable ClassReferable getBodyClassReferable(Referable referable) {
    ReferableInfo info = getBodyInfo(referable);
    return info == null || info.getParameters() != 0 ? null : info.getClassReferable();
  }

  default @Nullable ClassReferable getTypeClassReferable(Referable referable) {
    ReferableInfo info = getTypeInfo(referable);
    return info == null || info.getParameters() != 0 ? null : info.getClassReferable();
  }

  TypingInfo EMPTY = new TypingInfo() {
    @Override
    public @Nullable ReferableInfo getBodyInfo(Referable referable) {
      return null;
    }

    @Override
    public @Nullable ReferableInfo getTypeInfo(Referable referable) {
      return null;
    }
  };
}
