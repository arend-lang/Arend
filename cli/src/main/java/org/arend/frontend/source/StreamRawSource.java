package org.arend.frontend.source;

import org.antlr.v4.runtime.*;
import org.arend.ext.error.ErrorReporter;
import org.arend.frontend.parser.*;
import org.arend.ext.module.ModuleLocation;
import org.arend.module.error.ExceptionError;
import org.arend.source.Source;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * Represents a source that loads a raw module from an {@link InputStream}.
 */
public abstract class StreamRawSource implements Source {
  private final ModuleLocation myModule;

  protected StreamRawSource(@NotNull ModuleLocation module) {
    myModule = module;
  }

  /**
   * Gets an input stream over this module's raw source bytes (UTF-8, matching the encoding
   * {@link #loadGroup} feeds to the parser). Public so the console query tools (e.g. {@code -fu})
   * can read source text through the {@link Source} abstraction — working for both file- and
   * zip-backed libraries — rather than reaching for the filesystem, which zip sources lack.
   *
   * @return an input stream from which the source will be loaded or null if some error occurred.
   */
  @NotNull
  public abstract InputStream getInputStream() throws IOException;

  @Override
  public @NotNull ModuleLocation getModule() {
    return myModule;
  }

  @Override
  public @Nullable ConcreteGroup loadGroup(@NotNull ErrorReporter errorReporter) {
    try {
      CharStream stream = CharStreams.fromStream(getInputStream());
      ReporterErrorListener errorListener = new ReporterErrorListener(errorReporter, myModule);

      ArendLexer lexer = new ArendLexer(stream);
      lexer.removeErrorListeners();
      lexer.addErrorListener(errorListener);

      ArendParser parser = new ArendParser(new CommonTokenStream(lexer));
      parser.removeErrorListeners();
      parser.addErrorListener(errorListener);

      return new BuildVisitor(myModule, errorReporter).visitStatements(parser.statements());
    } catch (IOException e) {
      errorReporter.report(new ExceptionError(e, "loading", myModule.getModulePath()));
      return null;
    }
  }
}
