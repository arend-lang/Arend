package org.arend.ext.error;

import java.util.Arrays;
import java.util.List;

public class MergingErrorReporter implements ErrorReporter {
  private final List<ErrorReporter> myErrorReporters;

  public MergingErrorReporter(ErrorReporter... errorReporters) {
    this.myErrorReporters = Arrays.asList(errorReporters);
  }

  @Override
  public void report(GeneralError error) {
    for (ErrorReporter errorReporter : myErrorReporters) {
      errorReporter.report(error);
    }
  }
}
