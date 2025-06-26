package org.arend.lib;

import org.arend.ext.LiteralTypechecker;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreDataCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.module.FullName;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.reference.ExpressionResolver;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.util.Names;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public class StdNumberTypechecker implements LiteralTypechecker {
  private static ArendRef resolveName(FullName fullName, ExpressionResolver resolver) {
    ArendRef ref = resolver.resolveName(fullName.longName.getLastName());
    if (ref != null && ref.checkName(fullName)) {
      return ref;
    }
    ref = resolver.resolveLongName(fullName.longName);
    return ref != null && ref.checkName(fullName) ? ref : null;
  }

  @Override
  public @Nullable ConcreteExpression resolveNumber(@NotNull BigInteger number, @NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
    ArendRef negative;
    if (number.signum() < 0) {
      negative = resolveName(Names.NEGATIVE, resolver);
      if (negative == null) return null;
      number = number.negate();
    } else {
      negative = null;
    }

    boolean isNatCoef = false;
    FullName fullName;
    if (number.equals(BigInteger.ZERO)) {
      fullName = Names.ZRO;
    } else if (number.equals(BigInteger.ONE)) {
      fullName = Names.IDE;
    } else {
      fullName = Names.NAT_COEF;
      isNatCoef = true;
    }
    ArendRef ref = resolveName(fullName, resolver);
    if (ref == null) return null;

    ConcreteFactory factory = contextData.getFactory();
    ConcreteExpression result = factory.ref(ref);
    if (isNatCoef) {
      result = factory.app(result, true, factory.number(number));
    }
    return negative == null ? result : factory.app(factory.ref(negative), true, result);
  }

  @Override
  public @Nullable TypedExpression typecheckNumber(@NotNull BigInteger number, @Nullable ConcreteExpression resolved, @NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    if (resolved != null) {
      CoreExpression expectedType = contextData.getExpectedType() == null ? null : contextData.getExpectedType().normalize(NormalizationMode.WHNF);
      if (expectedType != null && !(expectedType instanceof CoreDataCallExpression dataCall && (dataCall.getDefinition() == typechecker.getPrelude().getNat() || dataCall.getDefinition() == typechecker.getPrelude().getInt() || dataCall.getDefinition() == typechecker.getPrelude().getFin()))) {
        return typechecker.typecheck(resolved, expectedType);
      }
    }
    return typechecker.checkNumber(number, contextData.getExpectedType(), contextData.getMarker());
  }
}