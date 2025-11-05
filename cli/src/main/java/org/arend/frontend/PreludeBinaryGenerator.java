package org.arend.frontend;

import org.arend.prelude.Prelude;
import org.arend.frontend.source.PreludeSources;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;

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

    Prelude.initialize();
    ArendServer server = new ArendServerImpl(ArendServerRequester.TRIVIAL, false, false, false);
    server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION)).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    binarySource.persist(server, System.err::println);
  }
}
