package org.arend.naming.reference;

import org.arend.core.definition.Definition;
import org.arend.core.definition.MetaTopDefinition;
import org.arend.ext.reference.MetaRef;
import org.arend.ext.reference.Precedence;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaResolver;
import org.arend.ext.typechecking.MetaTypechecker;
import org.arend.term.group.AccessModifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MetaReferable extends LocatedReferableImpl implements MetaRef {
  private final MetaResolver myResolver;
  private final MetaTypechecker myTypechecker;
  private MetaDefinition myDefinition;

  public MetaReferable(Object data, AccessModifier accessModifier, Precedence precedence, String name, Precedence aliasPrec, String aliasName, @NotNull MetaTypechecker typechecker, MetaResolver resolver, LocatedReferable parent) {
    super(data, accessModifier, precedence, name, aliasPrec == null ? Precedence.DEFAULT : aliasPrec, aliasName, parent, Kind.META);
    myResolver = resolver;
    myTypechecker = typechecker;
  }

  public MetaReferable(AccessModifier accessModifier, Precedence precedence, String name, @NotNull MetaTypechecker typechecker, MetaResolver resolver, LocatedReferable parent) {
    this(null, accessModifier, precedence, name, null, null, typechecker, resolver, parent);
  }

  public @NotNull MetaTypechecker getTypechecker() {
    return myTypechecker;
  }

  @Nullable
  @Override
  @Contract(pure = true)
  public MetaDefinition getDefinition() {
    return myDefinition;
  }

  @Override
  public @Nullable MetaResolver getResolver() {
    return myResolver;
  }

  public void setDefinition(MetaDefinition definition) {
    myDefinition = definition;
  }

  @Override
  public void setTypechecked(@Nullable Definition definition) {
    if (definition instanceof MetaTopDefinition || definition == null) {
      if (definition == null) {
        myDefinition = null;
      }
      super.setTypechecked(definition);
    }
  }

  @Override
  public boolean isTypechecked() {
    return myDefinition != null;
  }
}
