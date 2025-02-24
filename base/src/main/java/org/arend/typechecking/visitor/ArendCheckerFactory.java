package org.arend.typechecking.visitor;

import org.arend.ext.ArendExtension;
import org.arend.ext.error.ErrorReporter;
import org.arend.typechecking.instance.pool.GlobalInstancePool;
import org.jetbrains.annotations.NotNull;

public interface ArendCheckerFactory {
  @NotNull CheckTypeVisitor create(ErrorReporter errorReporter, GlobalInstancePool pool, ArendExtension arendExtension);

  ArendCheckerFactory DEFAULT = new ArendCheckerFactory() {
    @Override
    public @NotNull CheckTypeVisitor create(ErrorReporter errorReporter, GlobalInstancePool pool, ArendExtension arendExtension) {
      return new CheckTypeVisitor(errorReporter, pool, arendExtension);
    }
  };
}
