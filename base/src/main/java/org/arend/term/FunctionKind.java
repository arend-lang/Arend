package org.arend.term;

public enum FunctionKind {
  COERCE { @Override public boolean isUse() { return true; } },
  LEVEL {
    @Override public boolean isUse() { return true; }
    @Override public boolean isSFunc() { return true; }
  },
  SFUNC { @Override public boolean isSFunc() { return true; } },
  LEMMA { @Override public boolean isSFunc() { return true; } },
  FUNC_COCLAUSE { @Override public boolean isCoclause() { return true; } },
  CLASS_COCLAUSE { @Override public boolean isCoclause() { return true; } },
  FUNC,
  CONS,
  INSTANCE;

  public boolean isCoclause() {
    return false;
  }

  public boolean isUse() {
    return false;
  }

  public boolean isSFunc() {
    return false;
  }
}
