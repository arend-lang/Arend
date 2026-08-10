package org.arend.naming.error;

import org.arend.ext.error.NameResolverError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.naming.reference.Referable;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.jetbrains.annotations.Nullable;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class DuplicateOpenedNameError extends NameResolverError {
  public final boolean isStatic;
  public final Referable referable;
  public final ConcreteNamespaceCommand previousNamespaceCommand;
  public final ConcreteNamespaceCommand currentNamespaceCommand;

  public DuplicateOpenedNameError(boolean isStatic, Referable referable, ConcreteNamespaceCommand current, ConcreteNamespaceCommand previous, @Nullable ConcreteNamespaceCommand.NameRenaming renaming) {
    super(Level.WARNING, "", renaming != null ? renaming : current);
    this.isStatic = isStatic;
    this.referable = referable;
    previousNamespaceCommand = previous;
    currentNamespaceCommand = current;
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig ppConfig) {
    return hList(text(isStatic ? "Definition '" : "Definition '."),
      refDoc(referable), text("' is already imported from module "), refDoc(previousNamespaceCommand.module()));
  }
}
