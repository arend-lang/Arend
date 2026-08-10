package org.arend.module.error;

import org.arend.ext.error.GeneralError;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.naming.reference.ModuleReferable;

/**
 * A {@code .arc} file exists but cannot be read back. This is recoverable: the module is simply
 * recompiled from source, so it is a warning and not an error.
 *
 * <p>Reported instead of an {@link ExceptionError} because the underlying exception (a truncated
 * ZLIB stream, a protobuf tag mismatch) says nothing a user can act on, while a stack trace next
 * to a failing exit code reads like a compiler crash. The one thing worth saying is what happens
 * next, which is nothing: the cache is dropped and the module is typechecked again.
 */
public class CorruptBinaryCacheError extends GeneralError {
  public final ModulePath modulePath;
  public final Exception exception;

  public CorruptBinaryCacheError(ModulePath modulePath, Exception exception, boolean deleted) {
    super(Level.WARNING, "Binary cache for module '" + modulePath + "' is unreadable and "
        + (deleted ? "was deleted" : "will be ignored") + "; the module will be recompiled");
    this.modulePath = modulePath;
    this.exception = exception;
  }

  @Override
  public ModuleReferable getCause() {
    return new ModuleReferable(modulePath);
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    String message = exception.getMessage();
    return DocFactory.text(exception.getClass().getName() + (message == null ? "" : ": " + message));
  }

  @Override
  public boolean isShort() {
    return false;
  }
}
