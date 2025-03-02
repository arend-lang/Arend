package org.arend.term.group;

import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrintable;
import org.arend.ext.prettyprinting.PrettyPrinterConfig;
import org.arend.ext.prettyprinting.doc.DocStringBuilder;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.ext.reference.DataContainer;
import org.arend.ext.reference.Precedence;
import org.arend.naming.reference.LongUnresolvedReference;
import org.arend.naming.reference.ModuleReferable;
import org.arend.naming.reference.NamedUnresolvedReference;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.arend.ext.prettyprinting.doc.DocFactory.*;
import static org.arend.ext.prettyprinting.doc.DocFactory.text;

public record ConcreteNamespaceCommand(@Nullable Object data, boolean isImport, @NotNull LongUnresolvedReference module, boolean isUsing, @NotNull List<NameRenaming> renamings, @NotNull List<NameHiding> hidings) implements DataContainer, PrettyPrintable {
  public record NameRenaming(@Nullable Object data, @NotNull Scope.ScopeContext scopeContext, @NotNull NamedUnresolvedReference reference, @Nullable Precedence newPrecedence, @Nullable String newName) implements DataContainer {
    @Override
    public @Nullable Object getData() {
      return data;
    }
  }

  public record NameHiding(@Nullable Object data, @NotNull Scope.ScopeContext scopeContext, @NotNull Referable reference) implements DataContainer {
    @Override
    public @Nullable Object getData() {
      return data;
    }
  }

  @Override
  public @Nullable Object getData() {
    return data;
  }

  private static String scopeContextToString(Scope.ScopeContext context) {
    return context == Scope.ScopeContext.DYNAMIC ? "." : context == Scope.ScopeContext.PLEVEL ? "\\plevel " : context == Scope.ScopeContext.HLEVEL ? "\\hlevel " : "";
  }

  @Override
  public void prettyPrint(StringBuilder builder, PrettyPrinterConfig ppConfig) {
    List<String> path = module.getPath();
    if (path.isEmpty()) {
      return;
    }

    List<LineDoc> docs = new ArrayList<>();
    docs.add(text(isImport ? "\\import" : "\\open"));

    List<LineDoc> docPath = new ArrayList<>(path.size());
    //for (int i = 1; i <= path.size(); i++) {
    docPath.add(refDoc(new ModuleReferable(new ModulePath(path))));
    //}
    LineDoc referenceDoc = hSep(text("."), docPath);

    boolean using = isUsing();
    if (!using || !renamings.isEmpty()) {
      List<LineDoc> renamingDocs = new ArrayList<>(renamings.size());
      for (NameRenaming renaming : renamings) {
        LineDoc renamingDoc = hList(text(scopeContextToString(renaming.scopeContext())), refDoc(renaming.reference()));
        String newName = renaming.newName();
        if (newName != null) {
          Precedence precedence = renaming.newPrecedence();
          renamingDoc = hList(renamingDoc, text(" \\as " + (precedence == null ? "" : precedence)), text(newName));
        }
        renamingDocs.add(renamingDoc);
      }

      LineDoc openedReferencesDoc = hSep(text(", "), renamingDocs);
      if (!using) {
        referenceDoc = hList(referenceDoc, text("("), openedReferencesDoc, text(")"));
      }
      docs.add(referenceDoc);
      if (using) {
        docs.add(text("\\using"));
        docs.add(openedReferencesDoc);
      }
    } else {
      docs.add(referenceDoc);
    }

    if (!hidings.isEmpty()) {
      docs.add(text("\\hiding"));
      docs.add(hList(text("("), hSep(text(", "), hidings.stream().map(nh -> hList(text(scopeContextToString(nh.scopeContext())), refDoc(nh.reference()))).collect(Collectors.toList())), text(")")));
    }

    DocStringBuilder.build(builder, hSep(text(" "), docs));
  }

}
