package org.arend.lib.meta.pi_tree;

import org.arend.ext.ArendPrelude;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.ArendRef;

import java.util.Arrays;

public class PathExpression {
  public final ConcreteExpression pathExpression;

  public PathExpression(ConcreteExpression pathExpression) {
    this.pathExpression = pathExpression;
  }

  protected ConcreteExpression applyAt(ConcreteExpression arg, ArendRef iRef, ConcreteFactory factory, ArendPrelude prelude) {
    return factory.app(factory.ref(prelude.getAtRef()), true, Arrays.asList(arg, factory.ref(iRef)));
  }

  public ConcreteExpression applyAt(ArendRef iRef, ConcreteFactory factory, ArendPrelude prelude) {
    return applyAt(pathExpression, iRef, factory, prelude);
  }
}
