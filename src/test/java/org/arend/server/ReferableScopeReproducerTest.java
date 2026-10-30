package org.arend.server;

import org.arend.naming.NameResolverTestCase;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.Scope;
import org.arend.server.impl.DefinitionData;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * {@link ArendServer#getReferableScope} returns null for definitions that plainly exist, once the
 * server has seen a module more than once. The first four tests reproduce this, one shape each;
 * their names and comments describe the failure as it was.
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
 *
 * <p>Fix: {@code GroupData.updateGroup} reuses every top-level subtree either entirely or not at
 * all, so the parent of every referable in the group is the referable of its enclosing group. The
 * remaining tests pin which referables are reused.
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

  /** Re-parsing the same text reuses a {@code \module} block together with its members. */
  @Test
  public void identicalReparseReusesModuleMembers() {
    resolveNamesModule(TWO_MEMBERS);
    TCDefReferable f = get("M.f"), g = get("M.g");
    incModification();
    resolveNamesModule(TWO_MEMBERS);

    assertSame(f, get("M.f"));
    assertSame(g, get("M.g"));
    assertParentsConsistent();
  }

  /** Changing only bodies reuses the definition and its {@code \where} block. */
  @Test
  public void bodyEditReusesDefinition() {
    resolveNamesModule("\\func d : Nat => 0\n  \\where \\func f : Nat => 0\n");
    TCDefReferable d = get("d"), f = get("d.f");
    incModification();
    resolveNamesModule("\\func d : Nat => 1\n  \\where \\func f : Nat => 2\n");

    assertSame(d, get("d"));
    assertSame(f, get("d.f"));
    assertParentsConsistent();
  }

  /** Removing a member of a {@code \where} block reuses the rest of it. */
  @Test
  public void removingFromAWhereBlockReusesTheRest() {
    resolveNamesModule("\\func d : Nat => 0\n  \\where {\n  \\func f : Nat => 0\n  \\func g : Nat => 1\n}\n");
    TCDefReferable d = get("d"), f = get("d.f");
    incModification();
    resolveNamesModule("\\func d : Nat => 0\n  \\where \\func f : Nat => 0\n");

    assertSame(d, get("d"));
    assertSame(f, get("d.f"));
    assertParentsConsistent();
  }

  /**
   * Adding a member to a {@code \where} block recreates the whole top-level definition, since the
   * new member is a child of a new referable. Other top-level definitions are still reused.
   */
  @Test
  public void appendingToAWhereBlockRecreatesOnlyThatDefinition() {
    resolveNamesModule("\\func d : Nat => 0\n  \\where \\func f : Nat => 0\n\\func e : Nat => 0\n");
    TCDefReferable d = get("d"), e = get("e");
    incModification();
    resolveNamesModule("\\func d : Nat => 0\n  \\where {\n  \\func f : Nat => 0\n  \\func g : Nat => 1\n}\n\\func e : Nat => 0\n");

    assertNotSame(d, get("d"));
    assertSame(e, get("e"));
    assertParentsConsistent();
  }

  /** Every referable below the top level is a child of the referable of its enclosing group. */
  private void assertParentsConsistent() {
    for (ConcreteStatement statement : getGroup().statements()) {
      if (statement.group() != null) assertParentsConsistent(statement.group());
    }
  }

  private static void assertParentsConsistent(ConcreteGroup group) {
    List<ConcreteGroup> subgroups = new ArrayList<>(group.dynamicGroups());
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) subgroups.add(statement.group());
    }
    for (ConcreteGroup subgroup : subgroups) {
      assertSame(subgroup.referable().getRefName(), group.referable(), subgroup.referable().getLocatedReferableParent());
      assertParentsConsistent(subgroup);
    }
  }
}
