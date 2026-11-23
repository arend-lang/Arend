package org.arend.lib.meta.positivity;

import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.*;
import org.arend.ext.core.ops.NormalizationMode;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A relation {@code left R right} recognized by {@code positivity}, where {@code R} is either {@code <}, {@code <=}, or {@code /=}.
 * For {@code /=}, {@link #left} is the term and {@link #right} is the other side; for {@code <} and {@code <=}, {@link #right} is the term.
 *
 * @param instance  the order instance of the relation; {@code null} for {@code /=}
 * @param linear    true if the relation is {@code LinearOrder.<=} rather than {@code Preorder.<=}
 */
public record Relation(Strictness kind, CoreExpression left, CoreExpression right, @Nullable CoreExpression instance, boolean linear) {
  public CoreExpression term() {
    return kind == Strictness.NONZERO ? left : right;
  }

  public CoreExpression other() {
    return kind == Strictness.NONZERO ? right : left;
  }

  /**
   * Strips solved inference variables and type annotations such as {@code (0 : Real)}.
   */
  public static CoreExpression deref(CoreExpression expr) {
    return expr.getUnderlyingExpression();
  }

  /**
   * Recognizes {@code a < b}, {@code a <= b}, {@code a > b}, {@code a >= b}, and {@code a /= b}.
   * The type is normalized only if it is not recognized syntactically.
   */
  public static @Nullable Relation parse(CoreExpression type, PositivityMeta meta, @Nullable CoreExpression natInstance) {
    for (int i = 0; i < 2; i++) {
      type = deref(type);
      Relation result = parseSyntactically(type, meta, natInstance);
      if (result != null) return result;
      CoreExpression normType = type.normalize(NormalizationMode.WHNF);
      if (normType == type) return null;
      type = normType;
    }
    return null;
  }

  public static @Nullable Relation parseSyntactically(CoreExpression type, PositivityMeta meta, @Nullable CoreExpression natInstance) {
    if (type instanceof CoreFunCallExpression funCall) {
      List<? extends CoreExpression> args = funCall.getDefCallArguments();
      var ref = funCall.getDefinition().getRef();
      if (ref.equals(meta.notEquals) && args.size() >= 2) {
        return new Relation(Strictness.NONZERO, args.get(args.size() - 2), args.getLast(), null, false);
      }
      if (args.size() != 3) return null;
      if (ref.equals(meta.linearLessOrEquals)) {
        return new Relation(Strictness.NONNEGATIVE, args.get(1), args.get(2), args.get(0), true);
      }
      if (ref.equals(meta.addGroupLess)) {
        return new Relation(Strictness.POSITIVE, args.get(1), args.get(2), args.get(0), false);
      }
      return null;
    }

    if (type instanceof CoreDataCallExpression dataCall) {
      List<? extends CoreExpression> args = dataCall.getDefCallArguments();
      if (args.size() == 2 && dataCall.getDefinition().getRef().equals(meta.natLess)) {
        return new Relation(Strictness.POSITIVE, args.get(0), args.get(1), natInstance, false);
      }
      return null;
    }

    if (!(type instanceof CoreAppExpression app2)) return null;
    CoreExpression fun2 = deref(app2.getFunction());
    if (!(fun2 instanceof CoreAppExpression app1)) return null;
    CoreExpression fun1 = deref(app1.getFunction());
    if (!(fun1 instanceof CoreFieldCallExpression fieldCall)) return null;
    CoreClassField field = fieldCall.getDefinition();
    CoreExpression a = app1.getArgument(), b = app2.getArgument();
    if (field == meta.less) return new Relation(Strictness.POSITIVE, a, b, fieldCall.getArgument(), false);
    if (field == meta.greater) return new Relation(Strictness.POSITIVE, b, a, fieldCall.getArgument(), false);
    if (field == meta.lessOrEquals) return new Relation(Strictness.NONNEGATIVE, a, b, fieldCall.getArgument(), false);
    if (field == meta.greaterOrEquals) return new Relation(Strictness.NONNEGATIVE, b, a, fieldCall.getArgument(), false);
    return null;
  }

  /**
   * @return the head symbol of an expression: a definition, {@link #NUMBER} for numerals, or {@code null} for variables and other expressions.
   */
  public static @Nullable Object head(CoreExpression expr) {
    while (true) {
      expr = deref(expr);
      switch (expr) {
        case CoreAppExpression app -> expr = app.getFunction();
        case CoreDefCallExpression defCall -> {
          return defCall.getDefinition();
        }
        case CoreIntegerExpression ignored -> {
          return NUMBER;
        }
        case null, default -> {
          return null;
        }
      }
    }
  }

  public static final Object NUMBER = new Object();
}
