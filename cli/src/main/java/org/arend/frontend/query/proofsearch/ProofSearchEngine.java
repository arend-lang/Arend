package org.arend.frontend.query.proofsearch;

import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.TimedProgressReporter;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.Scope;
import org.arend.proof.ArendExpressionMatcher;
import org.arend.proof.ProofSearchQuery;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.util.Triple;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.arend.proof.Utils.getSignatures;

/**
 * The signature-matching engine behind {@code -ps}: resolves the search libraries, matches every
 * definition's signature against the query, and returns the hits (capped to {@code limit}) plus
 * the exact total. Rendering lives in the {@code *ProofSearchPrinter}s.
 */
final class ProofSearchEngine {
  private ProofSearchEngine() {}

  /** A definition whose signature matched, with the data the printers need to render its slice. */
  record ProofMatch(ModuleLocation module, TCDefReferable referable, Concrete.GeneralDefinition definition,
                    ArendExpressionMatcher.ProofSearchMatchingResult result, Concrete.Expression codomain) {}

  /** The matches kept for display (capped at {@code limit}) and the exact total across all libraries. */
  record Result(List<ProofMatch> matches, int total) {}

  /**
   * Every definition in {@code searchLibs} (and their resolved dependencies on {@code server})
   * whose signature matches {@code query}. {@code self} restricts matches to {@code searchLibs};
   * {@code limit} caps the returned list but not the reported total.
   */
  static Result find(ProofSearchQuery query, List<SourceLibrary> searchLibs, ArendServer server,
                     Set<String> excludeLibraries, boolean self, int limit) {
    ArendExpressionMatcher matcher = new ArendExpressionMatcher(query);
    Set<String> scopeNames = new HashSet<>();
    for (SourceLibrary library : searchLibs) scopeNames.add(library.getLibraryName());

    resolveLibraries(searchLibs, server);

    List<ProofMatch> matches = new ArrayList<>();
    int total = 0;
    for (ModuleLocation moduleLocation : server.getModules()) {
      if (excludeLibraries.contains(moduleLocation.getLibraryName())) continue;
      // Default matches every module the server knows (minus excluded); `self`
      // additionally drops anything outside the requested (top-level) libraries.
      if (self && !scopeNames.contains(moduleLocation.getLibraryName())) continue;
      for (DefinitionData data : server.getResolvedDefinitions(moduleLocation)) {
        for (Triple<Concrete.GeneralDefinition, List<Concrete.Expression>, Concrete.Expression> signature : getSignatures(data.definition())) {
          Scope scope = server.getReferableScope(data.definition().getData());
          ArendExpressionMatcher.ProofSearchMatchingResult result = matcher.match(signature.second(), signature.third(), scope);
          if (result == null) continue;
          // Count every match for the total; collect only up to `limit` (0 = all).
          total++;
          if (limit > 0 && matches.size() >= limit) continue;
          matches.add(new ProofMatch(moduleLocation, signature.first().getData(), signature.first(), result, signature.third()));
        }
      }
    }
    return new Result(matches, total);
  }

  private static void resolveLibraries(List<SourceLibrary> searchLibs, ArendServer server) {
    // searchLibs is already exclude-free, so no exclusion check is needed here.
    for (SourceLibrary library : searchLibs) {
      System.out.println("[INFO] Resolving " + library.getLibraryName());
      long time = System.currentTimeMillis();
      server.getCheckerFor(library.findModules(false).stream()
              .map(modulePath -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath)).toList())
          .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      System.out.println("[INFO] Resolved " + library.getLibraryName() + " ("
          + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
    }
  }
}
