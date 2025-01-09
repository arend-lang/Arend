package org.arend.naming.reference;

import org.arend.ext.reference.DataContainer;
import org.arend.ext.reference.Precedence;
import org.arend.naming.resolving.visitor.TypeClassReferenceExtractVisitor;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO[server2]: Replace this class with TypedLocatedReferable
public class ConcreteLocatedReferable extends LocatedReferableImpl implements DataContainer, TypedReferable {
  private final Object myData;
  private final String myAliasName;
  private final Precedence myAliasPrecedence;
  private Concrete.ReferableDefinition myDefinition;
  private String myDescription = "";

  public ConcreteLocatedReferable(Object data, AccessModifier accessModifier, @NotNull String name, Precedence precedence, @Nullable String aliasName, Precedence aliasPrecedence, LocatedReferable parent, Kind kind) {
    super(accessModifier, precedence, name, parent, kind);
    myData = data;
    myAliasName = aliasName;
    myAliasPrecedence = aliasPrecedence;
  }

  @Nullable
  @Override
  public Object getData() {
    return myData;
  }

  @Override
  public @Nullable String getAliasName() {
    return myAliasName;
  }

  @Override
  public @NotNull Precedence getAliasPrecedence() {
    return myAliasPrecedence;
  }

  public Concrete.ReferableDefinition getDefinition() {
    return myDefinition;
  }

  @Override
  public @NotNull String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  @Override
  public @NotNull TCDefReferable getTypecheckable() {
    return myDefinition == null ? this : myDefinition.getRelatedDefinition().getData();
  }

  public void setDefinition(Concrete.ReferableDefinition definition) {
    myDefinition = definition;
  }

  @Nullable
  @Override
  public ClassReferable getTypeClassReference() {
    return myDefinition == null ? null : myDefinition.accept(new TypeClassReferenceExtractVisitor(), null);
  }

  @Override
  public @Nullable Referable getBodyReference(TypeClassReferenceExtractVisitor visitor) {
    return myDefinition instanceof Concrete.FunctionDefinition function && function.getBody() instanceof Concrete.TermFunctionBody ? TypeClassReferenceExtractVisitor.getTypeReference(function.getBody().getTerm(), false) : null;
  }
}
