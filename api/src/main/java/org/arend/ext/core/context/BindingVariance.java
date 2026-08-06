package org.arend.ext.core.context;

public enum BindingVariance {
  COVARIANT {
    @Override public String text() { return "covariant"; }
    @Override public String capital() { return "Covariant"; }
  },
  CONTRAVARIANT {
    @Override public String text() { return "contravariant"; }
    @Override public String capital() { return "Contravariant"; }
  },
  INVARIANT {
    @Override public String text() { return "invariant"; }
    @Override public String capital() { return "Invariant"; }
  };

  public abstract String text();
  public abstract String capital();
}
