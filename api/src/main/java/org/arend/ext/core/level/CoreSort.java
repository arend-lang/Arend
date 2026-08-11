package org.arend.ext.core.level;

import org.jetbrains.annotations.NotNull;

public interface CoreSort {
  @NotNull CoreLevel getPLevel();
  @NotNull ConstLevel getHLevel();
  boolean isProp();
  boolean isSet();
}
