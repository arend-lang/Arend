package org.arend.lib;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.prettifier.ExpressionPrettifier;
import org.arend.lib.util.Names;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Prettifies String values back into string literals. String is a library-defined record (not a
// Prelude type with a cached kernel ref), so it is identified by name/module via ArendRef.checkName.
public class StringExpressionPrettifier implements ExpressionPrettifier {
  private final StdExtension ext;

  public StringExpressionPrettifier(StdExtension ext) {
    this.ext = ext;
  }

  @Override
  public @Nullable ConcreteExpression prettify(@NotNull CoreExpression expression, @NotNull ExpressionPrettifier defaultPrettifier) {
    // We normalize the value first so that type synonyms (e.g. `\func Code => String`) unfold to
    // the underlying String-returning term, whose computeType() still carries the `bytes` field.
    CoreExpression normalized = expression.normalize(NormalizationMode.WHNF);
    CoreExpression type = normalized instanceof CoreClassCallExpression ? normalized : normalized.computeType().normalize(NormalizationMode.WHNF);
    if (!(type instanceof CoreClassCallExpression classCall) || !classCall.getDefinition().getRef().checkName(Names.STRING)) {
      return null;
    }
    // Falling back to the default record display (by returning null) is more honest than showing
    // a literal that does not round-trip to the same value.
    String value = Utils.decodeString(classCall);
    return value == null ? null : ext.makeString(value);
  }
}
