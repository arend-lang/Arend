package org.arend.lib;

import org.arend.ext.LiteralTypechecker;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.concrete.expr.ConcreteReferenceExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreDataCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.TypecheckingError;
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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class StdLiteralTypechecker implements LiteralTypechecker {
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

  // resolveString runs with only an ExpressionResolver (no typechecker), so it resolves the `String`
  // type and returns a reference to it; typecheckString then builds the `\new String { | bytes => ... }` value.
  // The byte array is built directly at the core level by ExpressionTypechecker.checkByteArray, which
  // is linear in the literal's length and avoids per-element elaboration. (A `::`/`nil` list literal
  // would instead be quadratic to elaborate and can overflow the concrete-tree visitor stack for
  // large literals.)
  @Override
  public @Nullable ConcreteExpression resolveString(@NotNull String unescapedString, @NotNull ExpressionResolver resolver, @NotNull ContextData contextData) {
    ArendRef stringRef = resolveName(Names.STRING, resolver);
    if (stringRef == null) return null;
    return contextData.getFactory().ref(stringRef);
  }

  @Override
  public @Nullable TypedExpression typecheckString(@NotNull String unescapedString, @Nullable ConcreteExpression resolved, @NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    // resolveString returns null (leaving the literal unresolved) when the String type isn't in
    // scope -- almost always because Data.String wasn't imported. Report an actionable error rather
    // than letting the caller fall back to the generic "Cannot check string".
    if (!(resolved instanceof ConcreteReferenceExpression stringRefExpr)) {
      typechecker.getErrorReporter().report(new TypecheckingError("String literals require the String type to be in scope; did you forget to `\\import Data.String`?", contextData.getMarker()));
      return null;
    }
    ArendRef stringRef = stringRefExpr.getReferent();
    if (!(typechecker.getCoreDefinition(stringRef) instanceof CoreClassDefinition stringClass)) {
      typechecker.getErrorReporter().report(new TypecheckingError("Data.String.String is expected to be a `\\record`", contextData.getMarker()));
      return null;
    }

    // `bytes` is String's own field, taken from the resolved class definition.
    CoreClassField bytesField = stringClass.findField("bytes");
    if (bytesField == null) {
      typechecker.getErrorReporter().report(new TypecheckingError("Data.String.String is expected to have a `bytes` field", contextData.getMarker()));
      return null;
    }

    byte[] bytes;
    try {
      ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .encode(CharBuffer.wrap(unescapedString));
      bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
    } catch (CharacterCodingException e) {
      typechecker.getErrorReporter().report(new TypecheckingError("String literal contains an invalid Unicode surrogate", contextData.getMarker()));
      return null;
    }

    TypedExpression array = typechecker.checkByteArray(bytes);

    ConcreteFactory factory = contextData.getFactory();
    ConcreteExpression newExpr = factory.newExpr(factory.classExt(factory.ref(stringRef), factory.implementation(bytesField.getRef(), factory.core(array))));
    return typechecker.typecheck(newExpr, contextData.getExpectedType());
  }
}
