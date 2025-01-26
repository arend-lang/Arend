package org.arend.naming.resolving.typing;

import org.arend.naming.reference.ClassReferable;
import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.Nullable;

public interface TypingInfo {
  @Nullable ReferableInfo getBodyInfo(Referable referable);
  @Nullable ReferableInfo getTypeInfo(Referable referable);

  default @Nullable ClassReferable getBodyClassReferable(GlobalTypingInfo.Builder.MyInfo info) {
    if (info == null || info.parameters() != 0) return null;
    ReferableInfo refInfo = getBodyInfo(info.referable());
    return refInfo == null || refInfo.getParameters() != info.arguments() ? null : refInfo.getClassReferable();
  }

  default @Nullable ClassReferable getTypeClassReferable(Referable referable, int arguments) {
    ReferableInfo info = getTypeInfo(referable);
    return info == null || info.getParameters() != arguments ? null : info.getClassReferable();
  }

  default @Nullable ClassReferable getTypeClassReferable(Referable referable) {
    return getTypeClassReferable(referable, 0);
  }

  default @Nullable ClassReferable getTypeClassReferable(GlobalTypingInfo.Builder.MyInfo info) {
    if (info == null || info.parameters() != 0) return null;
    ReferableInfo refInfo = getTypeInfo(info.referable());
    return refInfo == null || refInfo.getParameters() != info.arguments() ? null : refInfo.getClassReferable();
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
