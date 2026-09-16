package org.arend.server;

import org.arend.naming.NameResolverTestCase;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.Scope;
import org.arend.server.impl.DefinitionData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * {@link ArendServer#getReferableScope} returns null for definitions that plainly exist, once the
 * server has seen a module more than once. Every test here fails on staging; each is one shape.
 *
 * <p>The server is the one {@link org.arend.ArendTestCase} creates, and it outlives both passes --
 * which is the whole condition. A process that parses each module once never reaches any of this,
 * so a plain CLI run is clean and only a long-lived server (the IDE, or a daemon) is affected.
 *
 * <p>Cause: a module update reuses the previous pass's referable for every definition whose long
 * name and shape are unchanged, and puts that one in the new group. So the referable a caller
 * holds, and the ones on its parent chain, need not be the ones the current group holds --
 * {@code ArendCheckerImpl} already treats a recreated referable as ordinary. But
 * {@code getReferableScope} matches ancestors by object identity, so it walks the group looking
 * for a referable that is not in it, and gives up.
 */
public class ReferableScopeReproducerTest extends NameResolverTestCase {
  private static final String TWO_MEMBERS =
      "\\module M \\where {\n  \\func f : Nat => 0\n  \\func g : Nat => 1\n}\n";

  /** Reports every path at once, so a failure names all of the losers rather than the first. */
  private String scopes(String... paths) {
    StringBuilder result = new StringBuilder();
    for (String path : paths) {
      if (!result.isEmpty()) result.append(", ");
      result.append(path).append('=').append(server.getReferableScope(get(path)) == null ? "null" : "ok");
    }
    return result.toString();
  }

  /** Two passes over identical text. Both members of the block lose their scope. */
  @Test
  public void moduleBlockMembersLoseTheirScopeOnAReparse() {
    resolveNamesModule(TWO_MEMBERS);
    assertEquals("first pass", "M.f=ok, M.g=ok", scopes("M.f", "M.g"));

    incModification();
    resolveNamesModule(TWO_MEMBERS);

    assertEquals("after a re-parse of the same text", "M.f=ok, M.g=ok", scopes("M.f", "M.g"));
  }

  /**
   * Appending to a {@code \module} block: the member that was already there loses its scope, and
   * the one just added keeps it -- the appended member is the only one whose referable was not
   * reused, so it is the only one still parented to the referable the group holds.
   */
  @Test
  public void appendingToAModuleBlockLeavesTheOldMemberWithoutAScope() {
    resolveNamesModule("\\module M \\where \\func f : Nat => 0\n");
    incModification();
    resolveNamesModule(TWO_MEMBERS);

    assertEquals("after appending to the \\module block", "M.f=ok, M.g=ok", scopes("M.f", "M.g"));
  }

  /** A {@code \where} block is the mirror image: there the <em>appended</em> member is the loser. */
  @Test
  public void appendingToAWhereBlockLeavesTheNewMemberWithoutAScope() {
    resolveNamesModule("\\func d : Nat => 0\n  \\where \\func f : Nat => 0\n");
    incModification();
    resolveNamesModule("\\func d : Nat => 0\n  \\where {\n  \\func f : Nat => 0\n  \\func g : Nat => 1\n}\n");

    assertEquals("after appending to the \\where block", "d.f=ok, d.g=ok", scopes("d.f", "d.g"));
  }

  /**
   * What the reported {@code -ps} crash is, with no CLI and no daemon in the picture: proof search
   * walks the server's resolved definitions and hands each one's scope to
   * {@link CachingScope#make}, which is the first line of {@code ArendExpressionMatcher.match}.
   * A null scope there is a {@code NullPointerException} on the first definition reached, for
   * every pattern -- including one that names nothing.
   */
  @Test
  public void aProofSearchStyleSweepCrashesOnAWarmServer() {
    resolveNamesModule(TWO_MEMBERS);
    incModification();
    resolveNamesModule(TWO_MEMBERS);

    for (DefinitionData data : server.getResolvedDefinitions(MODULE)) {
      Scope scope = server.getReferableScope(data.definition().getData());
      CachingScope.make(scope);
    }
  }
}
