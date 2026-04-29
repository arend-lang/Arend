package org.arend.library.classLoader;

import java.util.List;

public class ListClassLoaderDelegate implements ClassLoaderDelegate {
  private final List<ClassLoaderDelegate> myDelegates;

  public ListClassLoaderDelegate(List<ClassLoaderDelegate> delegates) {
    myDelegates = delegates;
  }

  @Override
  public byte[] findClass(String name) throws ClassNotFoundException {
    for (ClassLoaderDelegate delegate : myDelegates) {
      byte[] result = delegate.findClass(name);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return myDelegates.toString();
  }
}
