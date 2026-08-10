package org.arend.frontend;

import org.arend.ext.module.ModuleLocation;
import org.arend.prelude.Prelude;
import org.arend.frontend.source.PreludeSources;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.Collections;

public class PreludeBinaryGenerator {
  public static void main(String[] args) {
    Source rawSource = PreludeSources.getFileSource();
    PersistableBinarySource binarySource = PreludeSources.getBinarySource(Paths.get(args[0]));

    if (!(args.length >= 2 && args[1].equals("--recompile")) && rawSource.getTimeStamp() < binarySource.getTimeStamp()) {
      System.out.println("Prelude is up to date");
      return;
    }

    ArendServerRequester requester = new ArendServerRequester() {
      @Override
      public void requestModuleUpdate(@NotNull ArendServer server, @NotNull ModuleLocation module) {
        if (module.equals(Prelude.MODULE_LOCATION)) {
          rawSource.load(server, System.err::println);
        }
      }
    };
    ArendServer server = new ArendServerImpl(requester, false, false, false);
    server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION)).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    binarySource.persist(server, System.err::println);
  }
}
