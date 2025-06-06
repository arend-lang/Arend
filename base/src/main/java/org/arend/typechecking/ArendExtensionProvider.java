package org.arend.typechecking;

import org.arend.ext.ArendExtension;
import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.TCDefReferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ArendExtensionProvider {
  default @Nullable ArendExtension getArendExtension(@NotNull String libraryName) {
    return null;
  }

  default @Nullable ArendExtension getArendExtension(@NotNull TCDefReferable ref) {
    ModuleLocation location = ref.getLocation();
    return location == null ? null : getArendExtension(location.getLibraryName());
  }
}
