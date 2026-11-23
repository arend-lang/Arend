package org.arend.lib.meta.positivity;

import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.core.definition.*;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CorePiExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.jetbrains.annotations.Nullable;

/**
 * A lemma used by {@code positivity}: its conclusion is {@code 0 < t}, {@code 0 <= t}, or {@code t /= 0}.
 * Its explicit premises are proved recursively.
 *
 * @param head          the head symbol of {@code t} or {@code null} if it is a variable.
 * @param instanceClass if the first parameter is an instance of a class (e.g., {@code \this} parameter of a dynamic lemma), this class.
 */
public record PositivityRule(CoreDefinition definition, Strictness strictness, @Nullable Object head, @Nullable CoreClassDefinition instanceClass) {
  public static @Nullable PositivityRule make(CoreDefinition definition, PositivityMeta meta) {
    CoreExpression resultType;
    CoreClassDefinition instanceClass = null;
    if (definition instanceof CoreClassField field) {
      resultType = field.getResultType();
      instanceClass = field.getParentClass();
    } else if (definition instanceof CoreFunctionDefinition function) {
      resultType = function.getResultType();
      CoreParameter param = function.getParameters();
      if (param.hasNext() && !param.isExplicit() && "this".equals(param.getBinding().getName()) && param.getType() instanceof CoreClassCallExpression classCall) {
        instanceClass = classCall.getDefinition();
      }
    } else {
      return null;
    }
    if (resultType == null) return null;

    // Skip premises which are part of the result type, but do not unfold definitions such as /=
    while (resultType instanceof CorePiExpression piType) {
      resultType = piType.getCodomain();
    }
    Relation relation = Relation.parse(resultType, meta, null);
    if (relation == null) return null;
    return new PositivityRule(definition, relation.kind(), Relation.head(relation.term()), instanceClass);
  }
}
