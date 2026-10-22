package org.arend.query;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Every library a query may look into, by name: the CLI's library manager. */
public interface QueryLibraries {
  List<String> getLibraries();

  @Nullable QueryLibrary getLibrary(String name);
}
