package org.arend.frontend.cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.reference.Precedence;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.Scope;
import org.arend.proof.ArendExpressionMatcher;
import org.arend.proof.ProofSearchQuery;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.util.Triple;

import java.nio.file.Path;
import java.util.*;

import static org.arend.proof.Utils.getSignatures;

/**
 * {@code -psj PATTERN} — same matching as {@code -ps} but with JSON output.
 */
public final class ProofSearchJson {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ProofSearchJson() {}

  public static boolean run(CommandContext ctx, String[] args) {
    if (args == null || args.length == 0) {
      System.err.println("[ERROR] -psj requires a search pattern");
      return false;
    }

    String pattern = args[0];

    ProofSearchQuery.ParsingResult<ProofSearchQuery> queryResult = ProofSearchQuery.fromString(pattern);
    if (queryResult == null) return false;
    if (queryResult instanceof ProofSearchQuery.ParsingResult.Error<ProofSearchQuery> error) {
      System.err.println("Search pattern error at " + error.range + ": " + error.message);
      return false;
    }
    ProofSearchQuery query = ((ProofSearchQuery.ParsingResult.OK<ProofSearchQuery>) queryResult).value;
    ArendExpressionMatcher matcher = new ArendExpressionMatcher(query);

    for (SourceLibrary library : ctx.requestedLibraries) {
      ctx.server.getCheckerFor(library.findModules(false).stream()
              .map(modulePath -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath))
              .toList())
          .resolveAll(ctx.cancellation, ProgressReporter.empty());
    }

    List<Map<String, Object>> results = new ArrayList<>();

    for (ModuleLocation moduleLocation : ctx.server.getModules()) {
      for (DefinitionData data : ctx.server.getResolvedDefinitions(moduleLocation)) {
        for (Triple<Concrete.GeneralDefinition, List<Concrete.Expression>, Concrete.Expression> signature
            : getSignatures(data.definition())) {
          TCDefReferable referable = signature.first().getData();
          List<Concrete.Expression> parameters = signature.second();
          Concrete.Expression codomain = signature.third();

          Scope scope = ctx.server.getReferableScope(data.definition().getData());
          ArendExpressionMatcher.ProofSearchMatchingResult matchResult = matcher.match(parameters, codomain, scope);
          if (matchResult == null) continue;

          StringBuilder sigBuilder = new StringBuilder();
          Precedence topPrec = new Precedence(Concrete.Expression.PREC);
          codomain.prettyPrint(new PrettyPrintVisitor(sigBuilder, 0), topPrec);

          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("library", moduleLocation.getLibraryName());
          entry.put("module", moduleLocation.getModulePath().toString());
          entry.put("name", referable.getRefLongName().toString());
          entry.put("kind", kindLabel(referable, signature.first()));
          entry.put("signature", sigBuilder.toString());

          Map<String, Object> location = new LinkedHashMap<>();
          if (referable.getData() instanceof org.arend.error.SourcePosition sp) {
            location.put("line", sp.line);
            location.put("col", sp.column);
          }

          SourceLibrary lib = ctx.libraryManager.getLibrary(moduleLocation.getLibraryName());
          if (lib instanceof org.arend.frontend.library.FileSourceLibrary fl
              && moduleLocation.getLocationKind() == ModuleLocation.LocationKind.SOURCE) {
            Path src = fl.getSourceBasePath();
            if (src != null) {
              try {
                Path abs = org.arend.util.FileUtils.sourceFile(src, moduleLocation.getModulePath())
                    .toAbsolutePath().normalize();
                location.put("file", abs.toString());
              } catch (RuntimeException ignored) {}
            }
          }
          entry.put("location", location);

          results.add(entry);
        }
      }
    }

    try {
      Map<String, Object> output = new LinkedHashMap<>();
      output.put("results", results);
      output.put("count", results.size());
      System.out.println(MAPPER.writeValueAsString(output));
      return true;
    } catch (Exception e) {
      System.err.println("[ERROR] Failed to serialize results: " + e.getMessage());
      return false;
    }
  }

  private static String kindLabel(TCDefReferable ref, Concrete.GeneralDefinition def) {
    if (def instanceof Concrete.BaseFunctionDefinition fdef) {
      return switch (fdef.getKind()) {
        case FUNC -> "FUNCTION";
        case SFUNC -> "SFUNC";
        case LEMMA -> "LEMMA";
        case TYPE -> "TYPE";
        case AXIOM -> "AXIOM";
        case INSTANCE -> "INSTANCE";
        case COERCE -> "COERCE";
        case LEVEL -> "LEVEL";
        case FUNC_COCLAUSE, CLASS_COCLAUSE -> "COCLAUSE";
        case CONS -> "CONSTRUCTOR";
      };
    }
    if (def instanceof Concrete.MetaDefinition) return "META";
    if (def instanceof Concrete.DataDefinition) return "DATA";
    if (def instanceof Concrete.ClassDefinition cdef) return cdef.isRecord() ? "RECORD" : "CLASS";
    if (def instanceof Concrete.Constructor) return "CONSTRUCTOR";
    if (def instanceof Concrete.ClassField) return "FIELD";
    return ref.getKind().name();
  }
}
