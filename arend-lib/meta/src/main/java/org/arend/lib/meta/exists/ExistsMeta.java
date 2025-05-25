package org.arend.lib.meta.exists;

import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;

public class ExistsMeta extends GivenMeta {
  @Dependency private ArendRef TruncP;

  public ExistsMeta() {
    super(Kind.TRUNCATED);
  }

  @Override
  protected ConcreteExpression truncate(ConcreteExpression expression, ConcreteFactory factory) {
    return factory.app(factory.ref(TruncP), true, expression);
  }
}
