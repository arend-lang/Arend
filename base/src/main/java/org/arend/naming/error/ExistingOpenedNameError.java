package org.arend.naming.error;

import org.arend.ext.error.NameResolverError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.term.group.ConcreteNamespaceCommand;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class ExistingOpenedNameError extends NameResolverError {
  public ExistingOpenedNameError(ConcreteNamespaceCommand.NameRenaming cause) {
    super(Level.WARNING, "", cause);
  }

  @Override
  public ConcreteNamespaceCommand.NameRenaming getCause() {
    return (ConcreteNamespaceCommand.NameRenaming) super.getCause();
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig ppConfig) {
    ConcreteNamespaceCommand.NameRenaming renaming = getCause();
    String newName = renaming.newName();
    return hList(text("Definition '"), refDoc(renaming.reference()), text("' is not imported since " + (newName != null ? "'" + newName + "'" : "it") + " is defined in this module"));
  }
}
