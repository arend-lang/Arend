package org.arend.server.impl;

import org.arend.ext.ArendExtension;
import org.arend.ext.DefaultArendExtension;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.ui.ArendUI;
import org.arend.extImpl.*;
import org.arend.library.classLoader.ClassLoaderDelegate;
import org.arend.library.classLoader.MultiClassLoader;
import org.arend.library.error.LibraryError;
import org.arend.ext.module.ModuleLocation;
import org.arend.module.error.ExceptionError;
import org.arend.prelude.ConcretePrelude;
import org.arend.prelude.Prelude;
import org.arend.server.ArendLibrary;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class LibraryService {
  private final ArendServerImpl myServer;
  private final Logger myLogger = Logger.getLogger(LibraryService.class.getName());
  private final Map<String, ArendLibraryImpl> myLibraries = new ConcurrentHashMap<>();
  private MultiClassLoader<String> myExternalClassLoader = new MultiClassLoader<>(ArendExtension.class.getClassLoader());
  private MultiClassLoader<String> myInternalClassLoader = new MultiClassLoader<>(myExternalClassLoader);

  public LibraryService(ArendServerImpl server) {
    myServer = server;
    myLibraries.put(Prelude.LIBRARY_NAME, new ArendLibraryImpl(Prelude.LIBRARY_NAME, true, 0, Collections.emptyList(), null, null));
    server.copyLogger(myLogger);
  }

  void updateLibrary(@NotNull ArendLibrary library, @NotNull ErrorReporter errorReporter) {
    synchronized (myServer) {
      String name = library.getLibraryName();
      ArendLibraryImpl[] newLibrary = new ArendLibraryImpl[1];
      myLibraries.compute(name, (libName, prevLibrary) -> {
        long modificationStamp = library.getModificationStamp();
        if (prevLibrary != null && modificationStamp >= 0 && prevLibrary.getModificationStamp() >= modificationStamp) {
          myLogger.info(() -> "Library '" + libName + "' is not updated; previous timestamp " + prevLibrary.getModificationStamp() + " >= new timestamp " + modificationStamp);
          return prevLibrary;
        }

        myServer.clear(libName);

        boolean isExternal = library.isExternalLibrary();
        ClassLoaderDelegate delegate = library.getClassLoaderDelegate();
        if (delegate != null) {
          (isExternal ? myExternalClassLoader : myInternalClassLoader).addDelegate(libName, delegate);
        }

        ArendLibraryImpl result = new ArendLibraryImpl(libName, isExternal, modificationStamp, library.getLibraryDependencies(), loadArendExtension(delegate, name, isExternal, library, errorReporter), library.getGeneratedNames());
        newLibrary[0] = result;

        myLogger.info(() -> "Library '" + libName + "' is updated");
        return result;
      });

      if (newLibrary[0] != null) {
        try {
          setupExtension(newLibrary[0], library);
        } catch (Exception e) {
          String msg = "Library '" + newLibrary[0].getLibraryName() + "' extension is not loaded. Reason: " + e.getLocalizedMessage();
          errorReporter.report(new ExceptionError(e, "Loading extension of " + newLibrary[0].getLibraryName()));
          myLogger.severe(msg);
        }
      }
    }
  }

  void removeLibrary(String name) {
    ArendLibraryImpl library = myLibraries.remove(name);
    if (library != null) {
      (library.isExternalLibrary() ? myExternalClassLoader : myInternalClassLoader).removeDelegate(name);
    }
  }

  Set<String> unloadLibraries(boolean onlyInternal) {
    Set<String> removed = new HashSet<>();
    for (Iterator<Map.Entry<String, ArendLibraryImpl>> iterator = myLibraries.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, ArendLibraryImpl> entry = iterator.next();
      if (!onlyInternal || !entry.getValue().isExternalLibrary()) {
        iterator.remove();
        removed.add(entry.getKey());
      }
    }
    myExternalClassLoader = new MultiClassLoader<>(ArendExtension.class.getClassLoader());
    myInternalClassLoader = new MultiClassLoader<>(myExternalClassLoader);
    return removed;
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
      ArendExtension dependencyExt = dependencyLib == null ? null : dependencyLib.getExtension();
      if (dependencyExt != null) {
        dependencies.put(dependency, dependencyExt);
      }
    }

    ArendExtension extension = library.getExtension();
    SerializableKeyRegistryImpl keyRegistry = new SerializableKeyRegistryImpl();
    extension.registerKeys(keyRegistry);
    extension.setDependencies(dependencies);
    GroupData preludeData = myServer.getGroupData(Prelude.MODULE_LOCATION);
    extension.setPrelude(preludeData == null ? Prelude.INSTANCE : new ConcretePrelude(preludeData.getFileScope()));
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
      myServer.addReadOnlyModule(entry.getKey(), entry::getValue);
      myServer.getRequester().setupGeneratedModule(entry.getKey(), entry.getValue());
    }
  }
}
