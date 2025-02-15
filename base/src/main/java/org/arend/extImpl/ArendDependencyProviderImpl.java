package org.arend.extImpl;

import org.arend.core.definition.Definition;
import org.arend.ext.core.definition.CoreDefinition;
import org.arend.ext.dependency.ArendDependencyProvider;
import org.arend.ext.dependency.Dependency;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.Scope;
import org.arend.server.ArendChecker;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.GroupData;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.util.FullName;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Collections;

public class ArendDependencyProviderImpl extends Disableable implements ArendDependencyProvider {
  private final ArendServerImpl myServer;
  private final String myLibrary;

  public ArendDependencyProviderImpl(ArendServerImpl server, String library) {
    myServer = server;
    myLibrary = library;
  }

  @Override
  public <T extends CoreDefinition> @NotNull T getDefinition(@NotNull ModulePath module, @NotNull LongName name, Class<T> clazz) {
    checkEnabled();
    ModuleLocation location = new ModuleLocation(myLibrary, ModuleLocation.LocationKind.SOURCE, module);
    ArendChecker checker = myServer.getCheckerFor(Collections.singletonList(location));
    checker.resolveAll(UnstoppableCancellationIndicator.INSTANCE, ArendChecker.ProgressReporter.empty());
    GroupData groupData = myServer.getGroupData(location);
    Referable resolved = groupData == null ? null : Scope.resolveName(groupData.getFileScope(), name.toList());
    TCDefReferable referable = resolved instanceof TCDefReferable ? (TCDefReferable) resolved : null;
    if (referable != null) {
      checker.typecheckExtensionDefinition(new FullName(location, referable.getKind() == GlobalReferable.Kind.CONSTRUCTOR || referable.getKind() == GlobalReferable.Kind.FIELD ? new LongName(name.toList().subList(0, name.size() - 1)) : name));
    }
    Definition result = referable == null ? null : referable.getTypechecked();
    if (!clazz.isInstance(result)) {
      throw new IllegalArgumentException(result == null ? "Cannot find definition '" + name + "'" : "Cannot cast definition '" + name + "' of type '" + result.getClass() + "' to '" + clazz + "'");
    }
    return clazz.cast(result);
  }

  @Override
  public void load(@NotNull Object dependencyContainer) {
    try {
      for (Field field : dependencyContainer.getClass().getDeclaredFields()) {
        Class<?> fieldType = field.getType();
        if (CoreDefinition.class.isAssignableFrom(fieldType)) {
          Dependency dependency = field.getAnnotation(Dependency.class);
          if (dependency != null) {
            field.setAccessible(true);
            String name = dependency.name();
            field.set(dependencyContainer, getDefinition(ModulePath.fromString(dependency.module()), name.isEmpty() ? new LongName(field.getName()) : LongName.fromString(name), fieldType.asSubclass(CoreDefinition.class)));
          }
        }
      }
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
