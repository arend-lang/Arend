package org.arend.server;

import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class DelegateServerRequester implements ArendServerRequester {
  protected final ArendServerRequester requester;

  public DelegateServerRequester(ArendServerRequester requester) {
    this.requester = requester;
  }

  @Override
  public void requestModuleUpdate(@NotNull ArendServer server, @NotNull ModuleLocation module) {
    requester.requestModuleUpdate(server, module);
  }

  @Override
  public void setupGeneratedModule(@NotNull ModuleLocation module, @NotNull ConcreteGroup group) {
    requester.setupGeneratedModule(module, group);
  }

  @Override
  public @Nullable List<String> getFiles(@NotNull String libraryName, boolean inTests, @NotNull List<String> prefix) {
    return requester.getFiles(libraryName, inTests, prefix);
  }

  @Override
  public <T> T runUnderReadLock(@NotNull Supplier<T> supplier) {
    return requester.runUnderReadLock(supplier);
  }

  @Override
  public @NotNull List<Referable> fixModuleReferences(@NotNull List<Referable> referables) {
    return requester.fixModuleReferences(referables);
  }

  @Override
  public void addReference(@NotNull AbstractReference reference, @NotNull Referable referable) {
    requester.addReference(reference, referable);
  }

  @Override
  public void addReference(@NotNull ModuleLocation module, @NotNull AbstractReferable referable, @NotNull TCDefReferable tcReferable) {
    requester.addReference(module, referable, tcReferable);
  }

  @Override
  public void addModuleDependency(@NotNull ModuleLocation module, @NotNull ModuleLocation dependency) {
    requester.addModuleDependency(module, dependency);
  }
}
