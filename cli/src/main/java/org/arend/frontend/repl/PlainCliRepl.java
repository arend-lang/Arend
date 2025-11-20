package org.arend.frontend.repl;

import org.arend.error.DummyErrorReporter;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.LibraryManager;
import org.arend.server.ArendServer;
import org.arend.server.impl.ArendServerImpl;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Scanner;

public class PlainCliRepl extends CommonCliRepl {
  public PlainCliRepl(ArendServer server) {
    super(server);
    prompt = "λ ";
  }

  @Override
  public void printlnOpt(Object anything, boolean toError) {
    (toError ? System.err : System.out).println(anything);
  }

  @Override
  public void eprintln(Object anything) {
    System.err.println(anything);
    System.err.flush();
  }

  @Override
  public void println(Object anything) {
    System.out.println(anything);
  }

  @Override
  public void println() {
    System.out.println();
  }

  @Override
  public void print(Object anything) {
    System.out.print(anything);
    System.out.flush();
  }

  public void runRepl(@NotNull InputStream inputStream) {
    var scanner = new Scanner(inputStream);
    print(prompt());
    while (scanner.hasNext()) {
      if (repl(scanner.nextLine(), scanner::nextLine)) break;
      print(prompt());
    }
    saveUserConfig();
  }

  public static void main(String... args) {
    launch(false, Collections.emptyList(), new ArendServerImpl(new CliServerRequester(new LibraryManager(DummyErrorReporter.INSTANCE)), false, false, true));
  }

  public static void launch(
    boolean recompile,
    @NotNull Collection<? extends Path> libDirs,
    @NotNull ArendServer server
  ) {
    var repl = new PlainCliRepl(server);
    repl.println(ASCII_BANNER);
    repl.println();
    repl.println("Note: you're using the plain REPL.");
    // TODO[server2]: if (recompile) repl.getReplLibrary().addFlag(SourceLibrary.Flag.RECOMPILE);
    repl.initialize();
    repl.runRepl(System.in);
  }
}
