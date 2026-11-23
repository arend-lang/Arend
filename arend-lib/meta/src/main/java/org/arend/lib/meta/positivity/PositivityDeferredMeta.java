package org.arend.lib.meta.positivity;

import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.typechecking.AttributeChecker;
import org.arend.ext.typechecking.DeferredMetaDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * The deferred wrapper of {@link PositivityMeta}; it also validates definitions marked with {@code \attr positivity}.
 */
public class PositivityDeferredMeta extends DeferredMetaDefinition implements AttributeChecker {
  public PositivityDeferredMeta(PositivityMeta meta) {
    super(meta, false);
  }

  @Override
  public void checkAttributedDefinition(@NotNull CoreDefinition definition, @NotNull ErrorReporter errorReporter) {
    if (((PositivityMeta) deferredMeta).getRule(definition) == null) {
      errorReporter.report(new TypecheckingError("A positivity lemma should be a function or a field whose result type is of the form 0 < e, 0 <= e, or e /= 0", null));
    }
  }
}
