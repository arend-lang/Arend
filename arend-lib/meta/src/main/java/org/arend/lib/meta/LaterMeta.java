package org.arend.lib.meta;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.typechecking.*;
import org.arend.lib.meta.util.MetaInvocationMeta;

import java.util.List;

public class LaterMeta extends MetaInvocationMeta {
  private final boolean requireExpectedType;

  public LaterMeta(boolean requireExpectedType) {
    this.requireExpectedType = requireExpectedType;
  }

  public LaterMeta() {
    this(true);
  }

  @Override
  public boolean requireExpectedType() {
    return requireExpectedType;
  }

  @Override
  public TypedExpression invokeMeta(MetaDefinition meta, List<ConcreteExpression> implicitArgs, ExpressionTypechecker typechecker, ContextData contextData) {
    if (!requireExpectedType && contextData.getExpectedType() == null) {
      return meta.invokeMeta(typechecker, contextData);
    }
    return typechecker.defer(meta, contextData, contextData.getExpectedType(), false);
  }
}
