package org.arend.daemon.mcp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/** Which home's configs the agents' CLIs and the files read for them are of, when the process's home is not the environment's. */
public class AgentHomeTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static Map<String, String> environmentOf(Path home) {
    Map<String, String> environment = new HashMap<>();
    environment.put("HOME", home.toString());
    environment.put("USERPROFILE", home.toString());
    environment.put("PATH", "/bin");
    environment.put("CODEX_HOME", home.resolve("codex").toString());
    environment.put("CLAUDE_CONFIG_DIR", home.resolve("claude").toString());
    return environment;
  }

  /** As a test, or -Duser.home, runs the CLIs: on the home given, not on the user's, nor where a variable says. */
  @Test
  public void aCliRunsOnTheHomeGivenNotTheUsers() throws IOException {
    Path user = tmp.newFolder("user").toPath();
    Path given = tmp.newFolder("given").toPath();
    Map<String, String> environment = environmentOf(user);
    AgentHome.pointAt(given, environment);
    assertEquals(given.toString(), environment.get("HOME"));
    assertEquals(given.resolve(".codex").toString(), environment.get("CODEX_HOME"));
    assertFalse(environment.containsKey("CLAUDE_CONFIG_DIR"));
    assertEquals("/bin", environment.get("PATH"));
  }

  @Test
  public void theUsersOwnEnvironmentIsLeftAsItIs() throws IOException {
    Path user = tmp.newFolder("user").toPath();
    Map<String, String> environment = environmentOf(user);
    AgentHome.pointAt(user, environment);
    assertEquals(environmentOf(user), environment);
  }

  /** A variable pointing at a config is the user's choice for the user's home, and of no other. */
  @Test
  public void aVariableCountsForItsOwnHomeAlone() throws IOException {
    Path user = tmp.newFolder("user").toPath();
    Path given = tmp.newFolder("given").toPath();
    Map<String, String> environment = environmentOf(user);
    assertEquals(user.resolve("claude").toString(), AgentHome.variable(user, "CLAUDE_CONFIG_DIR", environment));
    assertNull(AgentHome.variable(given, "CLAUDE_CONFIG_DIR", environment));
    environment.remove("HOME");
    environment.remove("USERPROFILE");
    assertNull("an environment naming no home names none of ours", AgentHome.variable(user, "CLAUDE_CONFIG_DIR", environment));
  }
}
