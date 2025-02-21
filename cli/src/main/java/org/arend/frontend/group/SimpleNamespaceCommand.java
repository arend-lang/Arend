package org.arend.frontend.group;

import org.arend.ext.error.SourceInfo;
import org.arend.ext.reference.DataContainer;
import org.arend.ext.reference.Precedence;
import org.arend.frontend.parser.Position;
import org.arend.naming.reference.NamedUnresolvedReference;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.Scope;
import org.arend.term.NameHiding;
import org.arend.term.NameRenaming;
import org.arend.term.NamespaceCommand;
import org.arend.term.abs.AbstractReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SimpleNamespaceCommand implements NamespaceCommand, SourceInfo, DataContainer {
  private final Position myPosition;
  private final Kind myKind;
  private final List<String> myPath;
  private final boolean myUsing;
  private final List<SimpleNameRenaming> myOpenedReferences;
  private final List<SimpleNameHiding> myHiddenReferences;

  public SimpleNamespaceCommand(Position position, Kind kind, List<String> path, boolean isUsing, List<SimpleNameRenaming> openedReferences, List<SimpleNameHiding> hiddenReferences) {
    myPosition = position;
    myKind = kind;
    myPath = path;
    myUsing = isUsing;
    myOpenedReferences = openedReferences;
    myHiddenReferences = hiddenReferences;
  }

  @Override
  public @NotNull Position getData() {
    return myPosition;
  }

  @NotNull
  @Override
  public Kind getKind() {
    return myKind;
  }

  @Override
  public @NotNull List<AbstractReference> getReferenceList() {
    List<AbstractReference> result = new ArrayList<>(myPath.size());
    for (String ignored : myPath) {
      result.add(null);
    }
    return result;
  }

  @NotNull
  @Override
  public List<String> getPath() {
    return myPath;
  }

  @Override
  public boolean isUsing() {
    return myUsing;
  }

  @NotNull
  @Override
  public Collection<? extends SimpleNameRenaming> getOpenedReferences() {
    return myOpenedReferences;
  }

  @NotNull
  @Override
  public Collection<? extends NameHiding> getHiddenReferences() {
    return myHiddenReferences;
  }

  @Override
  public String moduleTextRepresentation() {
    return myPosition.moduleTextRepresentation();
  }

  @Override
  public String positionTextRepresentation() {
    return myPosition.positionTextRepresentation();
  }

  public static class SimpleNameRenaming implements NameRenaming, SourceInfo {
    private final Position myPosition;
    private final Scope.ScopeContext myScopeContext;
    private final NamedUnresolvedReference myReference;
    private final Precedence myPrecedence;
    private final String myName;

    public SimpleNameRenaming(Position position, Scope.ScopeContext scopeContext, NamedUnresolvedReference reference, Precedence precedence, String name) {
      myPosition = position;
      myScopeContext = scopeContext;
      myReference = reference;
      myPrecedence = precedence;
      myName = name;
    }

    @Override
    public @NotNull Scope.ScopeContext getScopeContext() {
      return myScopeContext;
    }

    @NotNull
    @Override
    public NamedUnresolvedReference getOldReference() {
      return myReference;
    }

    @Nullable
    @Override
    public String getName() {
      return myName;
    }

    @Nullable
    @Override
    public Precedence getPrecedence() {
      return myPrecedence;
    }

    @Override
    public String moduleTextRepresentation() {
      return myPosition.moduleTextRepresentation();
    }

    @Override
    public String positionTextRepresentation() {
      return myPosition.positionTextRepresentation();
    }
  }

  public static class SimpleNameHiding implements NameHiding, SourceInfo {
    private final Position myPosition;
    private final Scope.ScopeContext myScopeContext;
    private final Referable myReference;

    public SimpleNameHiding(Position position, Scope.ScopeContext scopeContext, Referable reference) {
      myPosition = position;
      myScopeContext = scopeContext;
      myReference = reference;
    }

    @Override
    public String moduleTextRepresentation() {
      return myPosition.moduleTextRepresentation();
    }

    @Override
    public String positionTextRepresentation() {
      return myPosition.positionTextRepresentation();
    }

    @Override
    public @NotNull Scope.ScopeContext getScopeContext() {
      return myScopeContext;
    }

    @Override
    public @NotNull Referable getHiddenReference() {
      return myReference;
    }
  }
}
