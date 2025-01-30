package org.arend.server.impl;

import org.arend.ext.error.ErrorReporter;
import org.arend.module.ModuleLocation;
import org.arend.naming.resolving.ResolverListener;
import org.arend.server.ArendChecker;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.provider.ConcreteProvider;
import org.arend.util.FullName;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArendCheckerImpl implements ArendChecker {
  private final ArendServerImpl myServer;
  private final List<? extends ModuleLocation> myModules;
  private Map<ModuleLocation, GroupData> myDependencies;
  private ConcreteProvider myConcreteProvider;

  public ArendCheckerImpl(ArendServerImpl server, List<? extends ModuleLocation> modules) {
    myServer = server;
    myModules = modules;
  }

  private Map<ModuleLocation, GroupData> getDependenciesInternal(@NotNull CancellationIndicator indicator) {
    if (myDependencies == null) {
      myDependencies = myServer.getDependencies(myModules, indicator);
      if (myDependencies == null) {
        myDependencies = Collections.emptyMap();
      }
    }
    return myDependencies;
  }

  @Override
  public @NotNull Set<ModuleLocation> getDependencies(@NotNull CancellationIndicator indicator) {
    return Collections.unmodifiableSet(getDependenciesInternal(indicator).keySet());
  }

  @Override
  public void resolveModules(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener) {
    myServer.resolveModules(myModules, errorReporter, indicator, listener, getDependenciesInternal(indicator), false, true);
  }

  private ConcreteProvider getConcreteProvider(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener) {
    if (myConcreteProvider == null) {
      myConcreteProvider = myServer.resolveModules(myModules, errorReporter, indicator, listener, getDependenciesInternal(indicator), true, false);
    }
    return myConcreteProvider;
  }

  @Override
  public void resolveAll(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener) {
    getConcreteProvider(errorReporter, indicator, listener);
  }

  @Override
  public void typecheckModules(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator) {
    getConcreteProvider(errorReporter, indicator, ResolverListener.EMPTY);
    // TODO[server2]
  }

  @Override
  public void typecheckDefinition(@NotNull FullName definition, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator) {
    getConcreteProvider(errorReporter, indicator, ResolverListener.EMPTY);
    // TODO[server2]
  }
}
