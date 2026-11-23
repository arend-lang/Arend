package org.arend.lib.meta.gcongr;

import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.TypecheckingError;
import org.arend.ext.typechecking.AttributeChecker;
import org.arend.ext.typechecking.DeferredMetaDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * The deferred wrapper of {@link GcongrMeta}; it also validates definitions marked with {@code \attr gcongr}.
 */
public class GcongrDeferredMeta extends DeferredMetaDefinition implements AttributeChecker {
  public GcongrDeferredMeta(GcongrMeta meta) {
    super(meta, false);
  }

  @Override
  public void checkAttributedDefinition(@NotNull CoreDefinition definition, @NotNull ErrorReporter errorReporter) {
    if (((GcongrMeta) deferredMeta).getGcongrRule(definition) == null) {
      errorReporter.report(new TypecheckingError("A gcongr lemma should be a function or a field whose result type is of the form f x1 ... xn R f y1 ... yn, where R is < or <=, " +
        "and which has an explicit premise relating x_i and y_i for some i such that x_i and y_i are distinct parameters", null));
    }
  }
}
