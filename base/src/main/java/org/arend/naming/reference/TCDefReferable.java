package org.arend.naming.reference;

import org.arend.core.definition.Definition;
import org.arend.core.definition.FunctionDefinition;
import org.arend.ext.reference.DataContainer;
import org.arend.ext.reference.Precedence;
import org.arend.ext.module.ModuleLocation;
import org.arend.typechecking.computation.ComputationRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public interface TCDefReferable extends LocatedReferable, DataContainer {
  void setTypechecked(@Nullable Definition definition);
  Definition getTypechecked();
  void setData(Object data);

  @Override
  @NotNull
  default Precedence getRepresentablePrecedence() {
    return hasAlias() ? getAliasPrecedence() : getTypechecked() instanceof FunctionDefinition function && function.getImplementedField() != null ? function.getImplementedField().getPrecedence() : getPrecedence();
  }

  default boolean isSimilar(@NotNull TCDefReferable referable) {
    return referable.getClass().equals(getClass()) &&
      getAccessModifier() == referable.getAccessModifier() &&
      getPrecedence().equals(referable.getPrecedence()) &&
      getAliasPrecedence().equals(referable.getAliasPrecedence()) &&
      Objects.equals(getAliasName(), referable.getAliasName()) &&
      getKind() == referable.getKind();
  }

  default void setTypecheckedIfAbsent(@NotNull Definition definition) {
    if (getTypechecked() == null) {
      setTypechecked(definition);
    }
  }

  default boolean isTypechecked() {
    Definition def = getTypechecked();
    return def != null && !def.status().needsTypeChecking();
  }

  default @NotNull TCDefReferable getTypecheckable() {
    return this;
  }

  default void setTypecheckedIfNotCancelled(@NotNull Definition definition) {
    ComputationRunner.getCancellationIndicator().checkCanceled();
    setTypecheckedIfAbsent(definition);
  }

  TCDefReferable NULL_REFERABLE = new TCDefReferable() {
    @Nullable
    @Override
    public Object getData() {
      return null;
    }

    @Override
    public @NotNull Kind getKind() {
      return Kind.OTHER;
    }

    @Nullable
    @Override
    public ModuleLocation getLocation() {
      return null;
    }

    @Nullable
    @Override
    public LocatedReferable getLocatedReferableParent() {
      return null;
    }

    @NotNull
    @Override
    public Precedence getPrecedence() {
      return Precedence.DEFAULT;
    }

    @NotNull
    @Override
    public String textRepresentation() {
      return "_";
    }

    @Override
    public void setTypechecked(@Nullable Definition definition) {}

    @Override
    public Definition getTypechecked() {
      return null;
    }

    @Override
    public void setData(Object data) {}

    @Override
    public boolean isSimilar(@NotNull TCDefReferable referable) {
      return referable == NULL_REFERABLE;
    }
  };
}
