package org.arend.naming.reference;

import org.arend.core.definition.Definition;
import org.arend.core.definition.MetaTopDefinition;
import org.arend.ext.reference.MetaRef;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.term.concrete.DefinableMetaDefinition;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MetaReferable extends LocatedReferableImpl implements MetaRef {
  private MetaDefinition myDefinition;
  private MetaResolver myResolver;

  public MetaReferable(Object data, AccessModifier accessModifier, Precedence precedence, String name, Precedence aliasPrec, String aliasName, MetaDefinition definition, MetaResolver resolver, LocatedReferable parent) {
    super(data, accessModifier, precedence, name, aliasPrec == null ? Precedence.DEFAULT : aliasPrec, aliasName, parent, Kind.OTHER);
    myDefinition = definition;
    myResolver = resolver;
  }

  public MetaReferable(AccessModifier accessModifier, Precedence precedence, String name, MetaDefinition definition, MetaResolver resolver, LocatedReferable parent) {
    this(null, accessModifier, precedence, name, null, null, definition, resolver, parent);
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

  @Override
  public void setTypechecked(@Nullable Definition definition) {
    if (definition instanceof MetaTopDefinition || definition == null) {
      super.setTypechecked(definition);
    }
  }

  @Override
  public boolean isTypechecked() {
    // If it's a definable meta, we always need to typecheck its dependencies
    Definition typechecked = getTypechecked();
    return myDefinition != null && (!(myDefinition instanceof DefinableMetaDefinition) || typechecked != null && !typechecked.status().needsTypeChecking());
  }
}
