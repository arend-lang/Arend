package org.arend.library;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ListErrorReporter;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.TypeCheckingTestCase;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BinaryRoundTripTest extends TypeCheckingTestCase {

  private ConcreteGroup persistAndReload(String source) throws Exception {
    typeCheckModule(source);
    MemoryBinarySource binarySource = new MemoryBinarySource(MODULE);
    ListErrorReporter errorReporter = new ListErrorReporter();
    assertTrue("persist failed", binarySource.persist(server, errorReporter));
    assertThat("persist produced errors", errorReporter.getErrorList(), is(empty()));

    ArendServer server2 = new ArendServerImpl(ArendServerRequester.TRIVIAL, false, false, false);
    server2.updateLibrary(MemoryLibrary.INSTANCE, errorReporter);
    server2.addReadOnlyModule(Prelude.MODULE_LOCATION, () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));

    ConcreteGroup loaded = binarySource.load(server2, errorReporter);
    assertThat("load returned null", loaded, is(notNullValue()));
    assertThat("load produced errors", errorReporter.getErrorList(), is(empty()));
    return loaded;
  }

  @Test
  public void functionRoundTrip() throws Exception {
    ConcreteGroup group = persistAndReload("\\func f : \\Set0 => \\Prop");
    TCDefReferable f = getDef(group, "f");
    assertNotNull(f);
    assertNotNull(f.getTypechecked());
    assertTrue(f.getTypechecked().status().isOK());
  }

  @Test
  public void dataRoundTrip() throws Exception {
    ConcreteGroup group = persistAndReload("\\data D | con");
    TCDefReferable d = getDef(group, "D");
    assertNotNull(d);
    assertNotNull(d.getTypechecked());
    assertTrue(d.getTypechecked().status().isOK());
  }

  @Test
  public void classRoundTrip() throws Exception {
    ConcreteGroup group = persistAndReload("\\class C (n : Nat)");
    TCDefReferable c = getDef(group, "C");
    assertNotNull(c);
    assertNotNull(c.getTypechecked());
    assertTrue(c.getTypechecked().status().isOK());
  }

  @Test
  public void crossReferenceRoundTrip() throws Exception {
    ConcreteGroup group = persistAndReload(
        "\\data D\n\\func f : \\Type0 => D");
    TCDefReferable f = getDef(group, "f");
    assertNotNull(f);
    assertNotNull(f.getTypechecked());
    assertTrue(f.getTypechecked().status().isOK());
  }
}
