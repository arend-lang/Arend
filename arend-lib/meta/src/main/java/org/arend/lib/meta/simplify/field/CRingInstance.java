package org.arend.lib.meta.simplify.field;

import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.instance.SubclassSearchParameters;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.arend.lib.meta.simplify.SimplifyMeta;

/**
 * An instance of a subclass of {@code CRing} together with its view as a {@code CRing}.
 *
 * <p>Reflection and the correctness lemmas use the ring view. Matching of a total inverse must use the original
 * view: otherwise an inverse of another instance on the same carrier could be picked up.</p>
 *
 * @param original  the instance as it was found; for plain {@code simplify} it is the same as {@code asRing}.
 * @param asRing    the same instance as a {@code CRing}.
 */
public record CRingInstance(@NotNull View original, @NotNull View asRing) {
  public record View(@NotNull TypedExpression expression, @NotNull CoreClassCallExpression classCall) {}

  public static @Nullable CRingInstance find(SimplifyMeta meta, FieldInverses inverseSource, ExpressionTypechecker typechecker, CoreExpression carrier, ConcreteSourceNode marker) {
    var found = Utils.findInstanceWithClassCall(new SubclassSearchParameters(inverseSource.instanceClass()), meta.carrier, carrier, typechecker, marker);
    if (found == null) return null;
    var ringClassCall = found.proj2.toSuperClass(meta.CRing);
    var ringInstance = typechecker.replaceType(found.proj1, ringClassCall, marker, false);
    return ringInstance == null ? null : new CRingInstance(new View(found.proj1, found.proj2), new View(ringInstance, ringClassCall));
  }
}
