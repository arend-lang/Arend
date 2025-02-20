package org.arend.naming.reference;

import org.arend.core.definition.Definition;
import org.arend.core.definition.MetaTopDefinition;
import org.arend.ext.reference.MetaRef;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.module.ModuleLocation;
import org.arend.term.concrete.DefinableMetaDefinition;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class MetaReferable implements TCDefReferable, MetaRef {
  private Object myData;
  private final AccessModifier myAccessModifier;
  private final Precedence myPrecedence;
  private final String myName;
  private MetaDefinition myDefinition;
  private MetaResolver myResolver;
  private final String myAliasName;
  private final Precedence myAliasPrecedence;
  public Supplier<GlobalReferable> underlyingReferable;
  private final LocatedReferable myParent;
  private MetaTopDefinition myTypechecked;

  public MetaReferable(Object data, AccessModifier accessModifier, Precedence precedence, String name, Precedence aliasPrec, String aliasName, MetaDefinition definition, MetaResolver resolver, LocatedReferable parent) {
    myData = data;
    myAccessModifier = accessModifier;
    myPrecedence = precedence;
    myName = name;
    myAliasName = aliasName;
    myAliasPrecedence = aliasPrec == null ? Precedence.DEFAULT : aliasPrec;
    myDefinition = definition;
    myResolver = resolver;
    myParent = parent;
  }

  public MetaReferable(AccessModifier accessModifier, Precedence precedence, String name, MetaDefinition definition, MetaResolver resolver, LocatedReferable parent) {
    this(null, accessModifier, precedence, name, null, null, definition, resolver, parent);
  }

  @Override
  public @Nullable ModuleLocation getLocation() {
    return myParent == null ? null : myParent.getLocation();
  }

  @Override
  @Contract(pure = true)
  public @Nullable LocatedReferable getLocatedReferableParent() {
    return myParent;
  }

  @Nullable
  @Override
  @Contract(pure = true)
  public MetaDefinition getDefinition() {
    return myDefinition;
  }

  public void setDefinition(@NotNull MetaDefinition definition) {
    myDefinition = definition;
  }

  public void setDefinition(@Nullable MetaDefinition definition, @Nullable MetaResolver resolver) {
    myDefinition = definition;
    myResolver = resolver;
  }

  @Override
  public @Nullable MetaResolver getResolver() {
    return myResolver;
  }

  @NotNull
  @Override
  public String textRepresentation() {
    return myName;
  }

  @NotNull
  @Override
  public Precedence getPrecedence() {
    return myPrecedence;
  }

  @Override
  public @Nullable String getAliasName() {
    return myAliasName;
  }

  @Override
  public @NotNull Precedence getAliasPrecedence() {
    return myAliasPrecedence;
  }

  @NotNull
  @Override
  public Kind getKind() {
    return Kind.OTHER;
  }

  @Override
  public @NotNull AccessModifier getAccessModifier() {
    return myAccessModifier;
  }

  @Override
  public @NotNull GlobalReferable getUnderlyingReferable() {
    GlobalReferable result = underlyingReferable == null ? null : underlyingReferable.get();
    return result == null ? this : result;
  }

  @Override
  public @Nullable Object getData() {
    return myData;
  }

  @Override
  public void setData(Object data) {
    myData = data;
  }

  @Override
  public void setTypechecked(@Nullable Definition definition) {
    if (definition instanceof MetaTopDefinition || definition == null) {
      myTypechecked = (MetaTopDefinition) definition;
    }
  }

  @Override
  public MetaTopDefinition getTypechecked() {
    return myTypechecked;
  }

  @Override
  public boolean isTypechecked() {
    // If it's a definable meta, we always need to typecheck its dependencies
    return myDefinition != null && (!(myDefinition instanceof DefinableMetaDefinition) || myTypechecked != null && !myTypechecked.status().needsTypeChecking());
  }
}
