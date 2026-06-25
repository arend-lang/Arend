package org.arend.module.error;

import org.arend.ext.error.GeneralError;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.Doc;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.naming.reference.ModuleReferable;

import java.io.PrintWriter;
import java.io.StringWriter;

public class BinaryCacheError extends GeneralError {
  public final ModulePath modulePath;
  public final String phase;
  public final Exception exception;

  public BinaryCacheError(ModulePath modulePath, String phase, Exception exception) {
    super(Level.WARNING, "Cannot load binary cache for module '" + modulePath + "' during " + phase);
    this.modulePath = modulePath;
    this.phase = phase;
    this.exception = exception;
  }

  @Override
  public ModuleReferable getCause() {
    return new ModuleReferable(modulePath);
  }

  @Override
  public Doc getBodyDoc(PrettyPrinterConfig ppConfig) {
    StringWriter stringWriter = new StringWriter();
    exception.printStackTrace(new PrintWriter(stringWriter));
    return DocFactory.text(stringWriter.toString());
  }

  @Override
  public boolean isShort() {
    return false;
  }
}
