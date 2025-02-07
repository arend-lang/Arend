package org.arend.naming.resolving.typing;

import org.arend.ext.reference.Precedence;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.arend.term.concrete.Concrete;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public interface TypingInfo {
  @Nullable DynamicScopeProvider getDynamicScopeProvider(Referable referable);
  @Nullable AbstractBody getRefBody(Referable referable);
  @Nullable AbstractBody getRefType(Referable referable);
  @NotNull Precedence getRefPrecedence(GlobalReferable referable, TypingInfo typingInfo);

  default @NotNull Precedence getRefPrecedence(GlobalReferable referable) {
    return getRefPrecedence(referable, this);
  }

  private @Nullable DynamicScopeProvider getNormDynamicScopeProvider(Referable referable, int arguments) {
    Set<Referable> visited = new HashSet<>();
    while (true) {
      if (!visited.add(referable)) return null;
      AbstractBody body = getRefBody(referable);
      if (body == null) {
        return arguments == 0 ? getDynamicScopeProvider(referable) : null;
      }
      if (body.getParameters() > arguments) return null;
      referable = body.getReferable();
      arguments = arguments - body.getParameters() + body.getArguments();
    }
  }

  private @Nullable DynamicScopeProvider getBodyDynamicScopeProvider(AbstractBody body) {
    return body == null ? null : getNormDynamicScopeProvider(body.getReferable(), body.getArguments());
  }

  default @Nullable DynamicScopeProvider getBodyDynamicScopeProvider(Referable referable) {
    return getBodyDynamicScopeProvider(new AbstractBody(0, referable, 0));
  }

  default @Nullable DynamicScopeProvider getBodyDynamicScopeProvider(Concrete.Expression expr) {
    return getBodyDynamicScopeProvider(TypingInfoVisitor.resolveAbstractBodyWithoutParameters(expr));
  }

  private @Nullable DynamicScopeProvider getTypeDynamicScopeProvider(AbstractBody body) {
    if (body == null) return null;
    AbstractBody typeBody = getRefType(body.getReferable());
    if (typeBody == null || typeBody.getParameters() != body.getArguments()) return null;
    return getNormDynamicScopeProvider(typeBody.getReferable(), typeBody.getArguments());
  }

  default @Nullable DynamicScopeProvider getTypeDynamicScopeProvider(Referable referable) {
    return getTypeDynamicScopeProvider(new AbstractBody(0, referable, 0));
  }

  default @Nullable DynamicScopeProvider getTypeDynamicScopeProvider(Concrete.Expression expr) {
    return getTypeDynamicScopeProvider(TypingInfoVisitor.resolveAbstractBodyWithoutParameters(expr));
  }

  default @Nullable DynamicScopeProvider getBodyOrTypeDynamicScopeProvider(Concrete.Expression expr) {
    AbstractBody body = TypingInfoVisitor.resolveAbstractBodyWithoutParameters(expr);
    DynamicScopeProvider provider = getBodyDynamicScopeProvider(body);
    return provider != null ? provider : getTypeDynamicScopeProvider(body);
  }

  TypingInfo EMPTY = new TypingInfo() {
    @Override
    public @Nullable DynamicScopeProvider getDynamicScopeProvider(Referable referable) {
      return null;
    }

    @Override
    public @Nullable AbstractBody getRefBody(Referable referable) {
      return null;
    }

    @Override
    public @Nullable AbstractBody getRefType(Referable referable) {
      return null;
    }

    @Override
    public @NotNull Precedence getRefPrecedence(GlobalReferable referable, TypingInfo typingInfo) {
      return referable.getPrecedence();
    }
  };
}
