package org.arend.typechecking.order.dependency;

import org.arend.core.definition.FunctionDefinition;
import org.arend.ext.reference.Precedence;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferableImpl;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.group.AccessModifier;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DependencyCollectorTest {
  private final DependencyCollector collector = new DependencyCollector(null);
  private final TCDefReferable x = definition("x"), y = definition("y"), d = definition("d"), top = definition("top");

  private static TCDefReferable definition(String name) {
    TCDefReferable ref = new LocatedReferableImpl(null, AccessModifier.PUBLIC, Precedence.DEFAULT, name, Precedence.DEFAULT, null, null, GlobalReferable.Kind.FUNCTION);
    ref.setTypechecked(new FunctionDefinition(ref));
    return ref;
  }

  @Test
  public void dependenciesAreRecorded() {
    collector.dependsOn(d, x);
    collector.dependsOn(d, y);
    assertEquals(Set.of(x, y), collector.getDependencies(d));
    assertTrue(collector.getDependencies(x).isEmpty());
  }

  @Test
  public void updateReachesTransitiveDependents() {
    collector.dependsOn(d, x);
    collector.dependsOn(top, d);
    collector.dependsOn(y, top);

    assertEquals(Set.of(x, d, top, y), collector.update(x));
    for (TCDefReferable ref : Set.of(x, d, top, y)) {
      assertNull(ref.getRefName() + " must lose its core", ref.getTypechecked());
    }
  }

  @Test
  public void updateLeavesOtherDefinitionsAlone() {
    collector.dependsOn(d, x);
    assertEquals(Set.of(y), collector.update(y));
    assertNotNull(x.getTypechecked());
    assertNotNull(d.getTypechecked());
    assertEquals(Set.of(x), collector.getDependencies(d));
  }

  @Test
  public void updateForgetsTheDependenciesOfWhatItReaches() {
    collector.dependsOn(d, x);
    collector.update(x);
    assertTrue(collector.getDependencies(d).isEmpty());
  }

  /** {@code d} is reached from {@code x}, so it is not the definition {@code update} was called on. */
  @Test
  public void updatedDefinitionIsNoLongerADependentOfItsOtherDependencies() {
    collector.dependsOn(d, x);
    collector.dependsOn(d, y);
    collector.update(x);
    assertEquals(Set.of(y), collector.update(y));
  }

  /** The server's collector gets its edges from the collector of each run. */
  @Test
  public void edgesCopiedFromAnotherCollectorArePruned() {
    DependencyCollector run = new DependencyCollector(null);
    run.dependsOn(d, x);
    run.dependsOn(d, y);
    run.copyTo(collector);

    assertEquals(Set.of(x, y), collector.getDependencies(d));
    collector.update(x);
    assertEquals(Set.of(y), collector.update(y));
  }

  /** Typechecking {@code d} again records its dependencies again, and they must work as before. */
  @Test
  public void anEdgeRecordedAgainIsFollowed() {
    collector.dependsOn(d, x);
    collector.dependsOn(d, y);
    collector.update(x);

    collector.dependsOn(d, y);
    assertEquals(Set.of(y, d), collector.update(y));
    assertFalse(collector.getDependencies(d).contains(x));
  }
}
