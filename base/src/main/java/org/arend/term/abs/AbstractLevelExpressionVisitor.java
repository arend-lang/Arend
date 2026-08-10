package org.arend.term.abs;

import org.arend.naming.reference.Referable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public interface AbstractLevelExpressionVisitor<P, R> {
  R visitNumber(@Nullable Object data, @NotNull BigInteger number, P param);
  R visitId(@Nullable Object data, Referable ref, P param);
  R visitSuc(@Nullable Object data, /* @NotNull */ @Nullable Abstract.LevelExpression expr, P param);
  R visitMax(@Nullable Object data, /* @NotNull */ @Nullable Abstract.LevelExpression left, /* @NotNull */ @Nullable Abstract.LevelExpression right, P param);
  R visitError(@Nullable Object data, P param);
}
