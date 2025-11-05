package org.arend.typechecking.visitor;

import org.arend.ext.ArendExtension;
import org.arend.ext.error.ErrorReporter;
import org.arend.server.ArendServerResolveListener;
import org.arend.typechecking.instance.pool.GlobalInstancePool;
import org.jetbrains.annotations.NotNull;

public interface ArendCheckerFactory {
  @NotNull CheckTypeVisitor create(ErrorReporter errorReporter, GlobalInstancePool pool, ArendExtension arendExtension, ArendServerResolveListener listener);

  ArendCheckerFactory DEFAULT = CheckTypeVisitor::new;
}
