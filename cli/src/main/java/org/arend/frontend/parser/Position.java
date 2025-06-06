package org.arend.frontend.parser;

import org.arend.error.SourcePosition;
import org.arend.ext.module.ModuleLocation;

public class Position extends SourcePosition {
  public final ModuleLocation module;

  public Position(ModuleLocation module, int line, int column) {
    super(module == null ? null : module.toString(), line, column + 1);
    this.module = module;
  }

  @Override
  public String toString() {
    return (module == null ? "" : module + ":") + line + ":" + column;
  }
}
