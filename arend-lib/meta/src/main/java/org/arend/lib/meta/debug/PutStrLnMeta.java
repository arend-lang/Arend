package org.arend.lib.meta.debug;

import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.ui.ArendConsole;
import org.arend.lib.StdExtension;
import org.arend.lib.error.TypeError;
import org.arend.lib.util.Names;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Prints a materialized String value as raw, unquoted, unescaped text (with a trailing newline).
// In deliberate contrast to `println` (PrintMeta), which renders values quoted/escaped and
// round-trippable, `putStrLnMaterialized` writes the raw characters of a String verbatim.
//
// The public `putStrLn` meta materializes its argument in Arend before invoking this primitive.
public class PutStrLnMeta extends BaseMetaDefinition {
  private final StdExtension ext;

  public PutStrLnMeta(StdExtension ext) {
    this.ext = ext;
  }

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { true, true };
  }

  @Override
  public int numberOfOptionalExplicitArguments() {
    return 1;
  }

  @Override
  public boolean allowExcessiveArguments() {
    return false;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    // Typecheck the argument (a String literal also elaborates to a String value here, so it needs
    // no special casing) and reject anything that isn't a String value.
    ConcreteExpression strArg = contextData.getArguments().getFirst().getExpression();
    TypedExpression result = typechecker.typecheck(strArg, null);
    if (result == null) {
      return null;
    }
    // Normalize the value first so that functions returning a refined class unfold to the underlying
    // String-returning term, whose computeType() carries the `bytes` field.
    CoreExpression type = result.getExpression().normalize(NormalizationMode.WHNF).computeType();
    if (!(type.normalize(NormalizationMode.WHNF) instanceof CoreClassCallExpression classCall) || !classCall.getDefinition().getRef().checkName(Names.STRING)) {
      if (!type.reportIfError(typechecker.getErrorReporter(), strArg)) {
        typechecker.getErrorReporter().report(new TypeError(ext.getExpressionPrettifier(), "putStrLn expects a String argument", type, strArg));
      }
      return null;
    }
    String text = Utils.decodeString(classCall);
    if (text == null) {
      typechecker.getErrorReporter().report(new TypecheckingError("putStrLn expects an evaluable String containing valid UTF-8", strArg));
      return null;
    }

    // Everything checks out: print the raw, unquoted, unescaped text.
    ArendConsole console = ext.ui.getConsole(contextData.getMarker());
    console.println(text);

    return typechecker.typecheck(contextData.getArguments().size() > 1 ? contextData.getArguments().get(1).getExpression() : contextData.getFactory().tuple(), contextData.getExpectedType());
  }
}
