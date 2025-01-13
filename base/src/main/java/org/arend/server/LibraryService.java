package org.arend.server;

import org.arend.ext.ArendExtension;
import org.arend.ext.DefaultArendExtension;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.ui.ArendUI;
import org.arend.extImpl.*;
import org.arend.library.classLoader.ClassLoaderDelegate;
import org.arend.library.classLoader.MultiClassLoader;
import org.arend.library.error.LibraryError;
import org.arend.module.ModuleLocation;
import org.arend.module.error.ExceptionError;
import org.arend.prelude.Prelude;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class LibraryService {
  private final ArendServerImpl myServer;
  private final Logger myLogger = Logger.getLogger(LibraryService.class.getName());
  private final Map<String, ArendLibraryImpl> myLibraries = new ConcurrentHashMap<>();
  private final MultiClassLoader<String> myExternalClassLoader = new MultiClassLoader<>(ArendExtension.class.getClassLoader());
  private final MultiClassLoader<String> myInternalClassLoader = new MultiClassLoader<>(myExternalClassLoader);

  public LibraryService(ArendServerImpl server) {
    myServer = server;
    server.copyLogger(myLogger);
  }

  void updateLibrary(@NotNull ArendLibrary library, @NotNull ErrorReporter errorReporter) {
    String name = library.getLibraryName();
    boolean[] updated = new boolean[1];
    myLibraries.compute(name, (k, prevLibrary) -> {
      long modificationStamp = library.getModificationStamp();
      if (prevLibrary != null && modificationStamp >= 0 && prevLibrary.getModificationStamp() >= modificationStamp) {
        myLogger.info(() -> "Library '" + name + "' is not updated; previous timestamp " + prevLibrary.getModificationStamp() + " >= new timestamp " + modificationStamp);
        return prevLibrary;
      }

      boolean isExternal = library.isExternalLibrary();
      ClassLoaderDelegate delegate = library.getClassLoaderDelegate();
      synchronized (myServer) {
        if (delegate != null) {
          (isExternal ? myExternalClassLoader : myInternalClassLoader).addDelegate(name, delegate);
        }
      }

      ArendLibraryImpl result = new ArendLibraryImpl(name, isExternal, modificationStamp, library.getLibraryDependencies(), loadArendExtension(delegate, name, isExternal, library, errorReporter));
      setupExtension(result, library);
      updated[0] = true;
      myLogger.info(() -> "Library '" + name + "' is updated");
      return result;
    });

    if (updated[0]) {
      synchronized (myServer) {
        myServer.getResolverCache().clear();
      }
    }
  }

  void removeLibrary(String name) {
    ArendLibraryImpl library = myLibraries.remove(name);
    if (library != null) {
      (library.isExternalLibrary() ? myExternalClassLoader : myInternalClassLoader).removeDelegate(name);
    }
  }

  boolean isLibraryLoaded(String name) {
    return myLibraries.containsKey(name);
  }

  public Set<String> getLibraries() {
    return myLibraries.keySet();
  }

  ArendLibraryImpl getLibrary(String name) {
    return myLibraries.get(name);
  }

  public ArendExtension loadArendExtension(ClassLoaderDelegate delegate, String libraryName, boolean isExternal, ArendLibrary library, ErrorReporter errorReporter) {
    String mainClass = delegate == null ? null : library.getExtensionMainClass();

    if (mainClass != null) {
      var classLoader = isExternal ? myExternalClassLoader : myInternalClassLoader;
      try {
        Class<?> extMainClass = classLoader.loadClass(mainClass);
        if (!ArendExtension.class.isAssignableFrom(extMainClass)) {
          errorReporter.report(LibraryError.incorrectExtensionClass(libraryName));
          extMainClass = null;
        }

        if (extMainClass != null) {
          return (ArendExtension) extMainClass.getDeclaredConstructor().newInstance();
        }
      } catch (Exception e) {
        classLoader.removeDelegate(libraryName);
        errorReporter.report(new ExceptionError(e, "loading of library " + libraryName));
      }
    }

    return new DefaultArendExtension();
  }

  private void setupExtension(ArendLibraryImpl library, ArendLibrary origLibrary) {
    Map<String, ArendExtension> dependencies = new LinkedHashMap<>();
    for (String dependency : library.getLibraryDependencies()) {
      ArendLibraryImpl dependencyLib = myLibraries.get(dependency);
      if (dependencyLib != null) {
        dependencies.put(dependency, dependencyLib.getExtension());
      }
    }

    ArendExtension extension = library.getExtension();
    SerializableKeyRegistryImpl keyRegistry = new SerializableKeyRegistryImpl();
    extension.registerKeys(keyRegistry);
    extension.setDependencies(dependencies);
    extension.setPrelude(new Prelude());
    extension.setConcreteFactory(new ConcreteFactoryImpl(null, library.getLibraryName()));
    extension.setVariableRenamerFactory(VariableRenamerFactoryImpl.INSTANCE);

    ArendUI ui = origLibrary.getArendUI();
    if (ui != null) {
      extension.setUI(ui);
    }

    DefinitionContributorImpl contributor = new DefinitionContributorImpl(library.getLibraryName());
    try {
      extension.declareDefinitions(contributor);
    } finally {
      contributor.disable();
    }
    for (Map.Entry<ModuleLocation, ConcreteGroup> entry : contributor.getModules().entrySet()) {
      myServer.addReadOnlyModule(entry.getKey(), entry.getValue());
    }

    extension.setDefinitionProvider(DefinitionProviderImpl.INSTANCE);
    /* TODO[server2]
    ArendDependencyProviderImpl provider = new ArendDependencyProviderImpl(typechecking, libraryManager.getAvailableModuleScopeProvider(this), libraryManager.getDefinitionRequester(), this);
    try {
      extension.load(provider);
    } finally {
      provider.disable();
    }
    */
  }
}
