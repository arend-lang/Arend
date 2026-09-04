package org.arend.library.classLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MultiClassLoader<T> extends ClassLoader {
  private final Map<T, ClassLoaderDelegate> myDelegates = Collections.synchronizedMap(new LinkedHashMap<>());

  public MultiClassLoader(ClassLoader parent) {
    super(parent);
  }

  public void addDelegate(T name, ClassLoaderDelegate delegate) {
    myDelegates.put(name, delegate);
  }

  public void removeDelegate(T name) {
    myDelegates.remove(name);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    // Copied before iterating: delegates are added and removed under the server lock, while
    // extension classes are loaded lazily during resolving, outside it.
    Map<T, ClassLoaderDelegate> delegates;
    synchronized (myDelegates) {
      delegates = new LinkedHashMap<>(myDelegates);
    }

    List<Exception> failures = null;
    for (ClassLoaderDelegate delegate : delegates.values()) {
      byte[] bytes;
      try {
        bytes = delegate.findClass(name);
      } catch (Exception e) {
        // A delegate whose location is broken -- an unreadable archive, a deleted directory --
        // must not hide the delegates after it. The failure is not dropped: it is attached to the
        // exception below if no other location provides the class. `Exception` rather than
        // `ClassNotFoundException` because a delegate is arbitrary code behind a public
        // interface, and even the two bundled ones can raise unchecked path errors.
        if (failures == null) {
          failures = new ArrayList<>();
        }
        failures.add(e);
        continue;
      }
      if (bytes != null) {
        return defineClass(name, bytes, 0, bytes.length);
      }
    }

    ClassNotFoundException error = new ClassNotFoundException("Cannot find class " + name + " in any of the following locations " + delegates + " or in the classpath");
    if (failures != null) {
      for (Exception failure : failures) {
        error.addSuppressed(failure);
      }
    }
    throw error;
  }
}
