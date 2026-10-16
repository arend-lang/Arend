package org.arend.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class ArendOptionsTest {
  private static CommandLine parse(String... args) throws ParseException {
    return new DefaultParser().parse(ArendOptions.create(), args);
  }

  /** A relative --log-file is the client's, however the option is spelt; an absolute one is as given. */
  @Test
  public void aRelativeLogFileIsTheClients() throws ParseException {
    assertEquals(Paths.get("/client/out.log"), ArendOptions.jsonLogPath(parse("-ss", "x", "--log-file", "out.log"), "/client"));
    assertEquals(Paths.get("/client/out.log"), ArendOptions.jsonLogPath(parse("-ss", "x", "--log-f=out.log"), "/client"));
    assertEquals(Paths.get("/abs.log"), ArendOptions.jsonLogPath(parse("-ss", "x", "--log-file", "/abs.log"), "/client"));
    assertEquals(Paths.get("out.log"), ArendOptions.jsonLogPath(parse("-ss", "x", "--log-file", "out.log"), null));
  }
}
