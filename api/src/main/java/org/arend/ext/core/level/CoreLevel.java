package org.arend.ext.core.level;

public interface CoreLevel {
  int getConstant();
  int getMaxConstant();
  boolean isInfinity();
  boolean isCat();
  boolean isClosed();
  boolean hasInferenceVar();
}
