package org.arend.naming.error;

import org.arend.ext.error.NameResolverError;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.Scope;
import org.arend.term.group.ConcreteNamespaceCommand;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;

public class DuplicateOpenedNameError extends NameResolverError {
  public final Scope.ScopeContext context;
  public final Referable referable;
  public final ConcreteNamespaceCommand previousNamespaceCommand;

  public DuplicateOpenedNameError(Scope.ScopeContext context, Referable referable, ConcreteNamespaceCommand previous, Object cause) {
    super(Level.WARNING, "", cause);
    this.context = context;
    this.referable = referable;
    previousNamespaceCommand = previous;
  }

  @Override
  public LineDoc getShortHeaderDoc(PrettyPrinterConfig ppConfig) {
    return hList(text(context == Scope.ScopeContext.PLEVEL ? "\\plevel definition '" : context == Scope.ScopeContext.HLEVEL ? "\\hlevel definition '" : context == Scope.ScopeContext.DYNAMIC ? "Definition '." : "Definition '"),
      refDoc(referable), text("' is already imported from module "), refDoc(previousNamespaceCommand.module()));
  }
}
