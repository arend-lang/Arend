package org.arend.frontend.parser;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;

/**
 * An implementation of {@link BaseErrorListener} that reports syntax errors
 * from ANTLR to {@link ErrorReporter}.
 */
public class ReporterErrorListener extends BaseErrorListener {
  private final @NotNull ErrorReporter myErrorReporter;
  private final @NotNull ModuleLocation myModule;

  public ReporterErrorListener(@NotNull ErrorReporter errorReporter, @NotNull ModuleLocation module) {
    myErrorReporter = errorReporter;
    myModule = module;
  }

  @Override
  public void syntaxError(Recognizer<?, ?> recognizer, Object o, int line, int pos, String msg, RecognitionException e) {
    myErrorReporter.report(new ParserError(new Position(myModule, line, pos), msg));
  }
}
