package org.arend.library;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.frontend.parser.ArendParser;
import org.arend.frontend.parser.BuildVisitor;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.naming.reference.MetaReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Focused tests for the inline-meta recovery pass implemented in
 * {@code ArendCheckerImpl.recoverInlineMetaDefinitions}. Mirrors the section-6 concerns
 * from {@code inline-meta-recovery-plan.md}:
 * <ul>
 *   <li>meta body referencing a same-module sibling</li>
 *   <li>chained metas (meta body referencing another meta in the same module)</li>
 *   <li>requester returning null falls back to the original "is empty" behaviour</li>
 *   <li>the deserialized {@link MetaReferable} itself carries the {@link MetaDefinition} post-recovery</li>
 * </ul>
 */
public class InlineMetaRecoveryTest {
  private static final String LIB_NAME = MemoryLibrary.INSTANCE.getLibraryName();
  private long modStamp = 1;

  private final Map<ModuleLocation, String> mySources = new HashMap<>();
  private final Map<ModuleLocation, MemoryBinarySource> myBinaries = new HashMap<>();
  private ArendServer myServer;
  private ListErrorReporter myErrors;

  @Before
  public void setUp() {
    myErrors = new ListErrorReporter();
    mySources.clear();
    myBinaries.clear();
    myServer = newServer(ArendServerRequester.TRIVIAL);
  }

  private ArendServer newServer(ArendServerRequester requester) {
    ArendServer srv = new ArendServerImpl(requester, false, false, false);
    srv.addReadOnlyModule(Prelude.MODULE_LOCATION,
        () -> new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE));
    srv.updateLibrary(MemoryLibrary.INSTANCE, myErrors);
    return srv;
  }

  private ModuleLocation moduleLoc(String name) {
    return new ModuleLocation(LIB_NAME, ModuleLocation.LocationKind.SOURCE, new ModulePath(name));
  }

  private ConcreteGroup parseSource(String text, ModuleLocation module, ErrorReporter errorReporter) {
    ListErrorReporter parseErrors = new ListErrorReporter();
    ArendParser.StatementsContext tree = CommonCliRepl.createParser(text, module, parseErrors).statements();
    if (!parseErrors.getErrorList().isEmpty()) {
      for (GeneralError e : parseErrors.getErrorList()) errorReporter.report(e);
      return null;
    }
    return new BuildVisitor(module, parseErrors).visitStatements(tree);
  }

  private void addSourceModule(ArendServer srv, String name, String text) {
    ModuleLocation module = moduleLoc(name);
    mySources.put(module, text);
    ConcreteGroup group = parseSource(text, module, myErrors);
    assertNotNull("Failed to parse module " + name, group);
    srv.updateModule(modStamp++, module, () -> group);
  }

  private void typecheckAll(ArendServer srv) {
    List<ModuleLocation> modules = new ArrayList<>();
    for (ModuleLocation loc : srv.getModules()) {
      if (loc.getLibraryName().equals(LIB_NAME)) modules.add(loc);
    }
    if (!modules.isEmpty()) {
      srv.getCheckerFor(modules).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    }
  }

  private void persist(ArendServer srv, String name) {
    ModuleLocation module = moduleLoc(name);
    MemoryBinarySource bin = new MemoryBinarySource(module);
    ListErrorReporter persistErr = new ListErrorReporter();
    assertTrue("persist of " + name + " failed", bin.persist(srv, persistErr));
    assertThat("persist of " + name + " produced errors", persistErr.getErrorList(), is(empty()));
    myBinaries.put(module, bin);
  }

  /** Build a requester that serves {@code loadSourceGroup} from the recorded source strings. */
  private ArendServerRequester sourceRequester() {
    return new ArendServerRequester() {
      @Override
      public @Nullable ConcreteGroup loadSourceGroup(@NotNull ModuleLocation module, @NotNull ErrorReporter errorReporter) {
        String src = mySources.get(module);
        return src == null ? null : parseSource(src, module, errorReporter);
      }
    };
  }

  private ArendServer buildServerWithDeserializedModule(String moduleName, ArendServerRequester requester) {
    ArendServer srv = newServer(requester);
    ModuleLocation module = moduleLoc(moduleName);
    MemoryBinarySource bin = myBinaries.get(module);
    assertNotNull("no persisted binary for " + moduleName, bin);
    ListErrorReporter loadErrors = new ListErrorReporter();
    ConcreteGroup loaded = bin.load(srv, loadErrors);
    assertNotNull("load of " + moduleName + " returned null", loaded);
    assertThat("load of " + moduleName + " produced errors", loadErrors.getErrorList(), is(empty()));
    return srv;
  }

  private MetaReferable findMeta(ConcreteGroup group, String name) {
    for (ConcreteStatement s : group.statements()) {
      if (s.group() != null && s.group().referable().getRefName().equals(name)
          && s.group().referable() instanceof MetaReferable meta) {
        return meta;
      }
    }
    // Walk into where-blocks.
    MetaReferable[] found = {null};
    group.traverseGroup(g -> {
      if (found[0] == null && g.referable() instanceof MetaReferable meta
          && meta.getRefName().equals(name)) {
        found[0] = meta;
      }
    });
    return found[0];
  }

  private boolean hasEmptyMetaError(ArendServer srv) {
    for (Map.Entry<ModuleLocation, List<GeneralError>> entry : srv.getErrorMap().entrySet()) {
      for (GeneralError err : entry.getValue()) {
        if (err.getShortMessage().contains("is empty")) return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------
  // Helpers for the most common flow: define META_MODULE as source, typecheck,
  // persist, reload on a fresh server from the binary only, add a CONSUMER
  // module that references the meta, and typecheck the consumer.
  // ---------------------------------------------------------------------------

  private static final String META_MODULE = "Meta";
  private static final String CONSUMER_MODULE = "Consumer";

  private ArendServer setupMetaProducer(String metaModuleSource) {
    addSourceModule(myServer, META_MODULE, metaModuleSource);
    typecheckAll(myServer);
    persist(myServer, META_MODULE);
    return buildServerWithDeserializedModule(META_MODULE, sourceRequester());
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  @Test
  public void metaBodyReferencesSameModuleSibling() {
    // "helper" is a same-module sibling of "wrap"; after reload "wrap"'s body must still resolve it.
    ArendServer srv2 = setupMetaProducer(
        "\\func helper : \\Type => \\Prop\n" +
        "\\meta wrap => helper\n");
    addSourceModule(srv2, CONSUMER_MODULE,
        "\\import Meta\n" +
        "\\func use : \\Type => wrap\n");
    typecheckAll(srv2);

    assertFalse("Meta body failed to resolve same-module sibling: " + srv2.getErrorMap(),
        hasEmptyMetaError(srv2));
    MetaReferable wrap = findMeta(srv2.getRawGroup(moduleLoc(META_MODULE)), "wrap");
    assertNotNull("wrap MetaReferable missing after reload", wrap);
    assertNotNull("wrap body was not recovered", wrap.getDefinition());
  }

  @Test
  public void chainedMetasResolveAcrossSameModule() {
    // metaA's body references metaB in the same module; the order of recovery must not matter.
    ArendServer srv2 = setupMetaProducer(
        "\\meta metaB => \\Prop\n" +
        "\\meta metaA => metaB\n");
    addSourceModule(srv2, CONSUMER_MODULE,
        "\\import Meta\n" +
        "\\func use : \\Type => metaA\n");
    typecheckAll(srv2);

    assertFalse("Chained metas failed: " + srv2.getErrorMap(),
        hasEmptyMetaError(srv2));
    MetaReferable mA = findMeta(srv2.getRawGroup(moduleLoc(META_MODULE)), "metaA");
    MetaReferable mB = findMeta(srv2.getRawGroup(moduleLoc(META_MODULE)), "metaB");
    assertNotNull("metaA body was not recovered", mA == null ? null : mA.getDefinition());
    assertNotNull("metaB body was not recovered", mB == null ? null : mB.getDefinition());
  }

  @Test
  public void metaBodyReferencesImport() {
    // Source of truth: meta body references a name from an imported module.
    // The deserialized group strips namespace commands, so without the hybrid
    // scope wiring "Dep.val" would be unresolvable after reload.
    addSourceModule(myServer, "Dep", "\\func val : \\Type => \\Prop\n");
    addSourceModule(myServer, META_MODULE,
        "\\import Dep\n" +
        "\\meta wrap => val\n");
    typecheckAll(myServer);
    persist(myServer, META_MODULE);
    // We do NOT persist "Dep" — but on the fresh server we serve it from source via the requester
    // so that cross-module scope lookups succeed. This mirrors the partial-round-trip scenario.
    ArendServer srv2 = newServer(new ArendServerRequester() {
      @Override
      public @Nullable ConcreteGroup loadSourceGroup(@NotNull ModuleLocation module, @NotNull ErrorReporter er) {
        String src = mySources.get(module);
        return src == null ? null : parseSource(src, module, er);
      }
      @Override
      public void requestModuleUpdate(@NotNull ArendServer s, @NotNull ModuleLocation module) {
        String src = mySources.get(module);
        if (src != null) {
          ConcreteGroup g = parseSource(src, module, myErrors);
          if (g != null) s.updateModule(modStamp++, module, () -> g);
        }
      }
    });
    // Load the deserialized meta module.
    MemoryBinarySource bin = myBinaries.get(moduleLoc(META_MODULE));
    ListErrorReporter le = new ListErrorReporter();
    ConcreteGroup loaded = bin.load(srv2, le);
    assertNotNull(loaded);
    assertThat(le.getErrorList(), is(empty()));

    addSourceModule(srv2, CONSUMER_MODULE,
        "\\import Meta\n" +
        "\\func use : \\Type => wrap\n");
    typecheckAll(srv2);

    assertFalse("Meta body referencing an import failed to resolve: " + srv2.getErrorMap(),
        hasEmptyMetaError(srv2));
  }

  @Test
  public void requesterReturningNullFallsBackToEmpty() {
    // The default requester does not implement loadSourceGroup (returns null).  The recovery
    // pass must quietly skip and leave the original "Meta X is empty" behaviour intact; it
    // must NOT crash the resolver.
    addSourceModule(myServer, META_MODULE, "\\meta wrap => \\Prop\n");
    typecheckAll(myServer);
    persist(myServer, META_MODULE);

    ArendServer srv2 = buildServerWithDeserializedModule(META_MODULE, ArendServerRequester.TRIVIAL);
    addSourceModule(srv2, CONSUMER_MODULE,
        "\\import Meta\n" +
        "\\func use : \\Type => wrap\n");
    typecheckAll(srv2);

    // Without a source requester we cannot recover the body, so we expect to see the
    // historical "Meta 'wrap' is empty" error — the test is asserting the FALLBACK, not
    // the fix.  Assert that (a) something went wrong (b) but the run did not throw.
    assertTrue("Without source recovery the consumer must report a meta-related error; errors=" + srv2.getErrorMap(),
        hasEmptyMetaError(srv2));
    MetaReferable wrap = findMeta(srv2.getRawGroup(moduleLoc(META_MODULE)), "wrap");
    assertNotNull("wrap MetaReferable missing after reload", wrap);
    assertNull("Without a source requester the meta's body must stay null", wrap.getDefinition());
  }

  @Test
  public void metaRefIdentityPreservedAcrossRecovery() {
    // The recovery must call setDefinition on the SAME MetaReferable instance that is stored
    // in the deserialized group, not on a fresh one produced by parsing source.
    addSourceModule(myServer, META_MODULE, "\\meta wrap => \\Prop\n");
    typecheckAll(myServer);
    persist(myServer, META_MODULE);

    ArendServer srv2 = buildServerWithDeserializedModule(META_MODULE, sourceRequester());

    // Snapshot the deserialized MetaReferable BEFORE triggering the recovery via typecheck.
    MetaReferable deserMeta = findMeta(srv2.getRawGroup(moduleLoc(META_MODULE)), "wrap");
    assertNotNull(deserMeta);
    assertNull("pre-recovery, deserialized meta must be empty", deserMeta.getDefinition());
    // getTypechecked() is the core half — it IS serialized and must already be set.
    assertNotNull("pre-recovery, MetaTopDefinition must be attached",
        ((TCDefReferable) deserMeta).getTypechecked());

    addSourceModule(srv2, CONSUMER_MODULE,
        "\\import Meta\n" +
        "\\func use : \\Type => wrap\n");
    typecheckAll(srv2);

    // The recovery must have populated the SAME instance, not a replacement.
    MetaReferable sameRef = findMeta(srv2.getRawGroup(moduleLoc(META_MODULE)), "wrap");
    assertTrue("recovery replaced the MetaReferable instance (identity lost)", sameRef == deserMeta);
    assertNotNull("post-recovery, meta body must be present", deserMeta.getDefinition());
  }
}
