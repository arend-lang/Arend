package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.core.definition.Definition;
import org.arend.core.expr.visitor.SizeExpressionVisitor;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.ext.util.Pair;
import org.arend.frontend.library.*;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.symbol.SignatureFileWriter;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.frontend.source.PreludeResourceSource;
import org.arend.library.classLoader.FileClassLoaderDelegate;
import org.arend.library.error.LibraryIOError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.ModuleLocation;
import org.arend.proof.ArendExpressionMatcher;
import org.arend.proof.ProofSearchQuery;
import org.arend.module.error.DefinitionNotFoundError;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.naming.scope.Scope;
import org.arend.prelude.Prelude;
import org.arend.util.Triple;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer;
import org.arend.term.prettyprint.ToAbstractVisitor;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.doubleChecker.CoreModuleChecker;
import org.arend.typechecking.error.local.GoalError;
import org.arend.typechecking.order.MapTarjanSCC;
import org.arend.util.FileUtils;

import org.arend.core.definition.Definition;
import org.arend.ext.reference.Precedence;
import org.arend.naming.reference.TCDefReferable;
import org.arend.source.PersistableBinarySource;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.arend.ext.prettyprinting.PrettyPrinterConfig.DEFAULT;
import static org.arend.proof.Utils.getSignatures;

public class ConsoleMain {
  private boolean myExitWithError;
  private final Map<ModuleLocation, GeneralError.Level> myModuleResults = new LinkedHashMap<>();

  private final static String SHOW_TIMES = "show-times";
  private final static String SHOW_SIZES = "show-sizes";
  private final static String SHOW_MODULES = "show-modules";
  private final static String SHOW_MODULES_WITH_INSTANCES = "show-modules-with-instances";
  private final static String PRINT_FULL = "print-full";

  private static boolean containsHelpToken(String[] values) {
    if (values == null) return false;
    for (String v : values) if ("help".equalsIgnoreCase(v)) return true;
    return false;
  }

  private final static String SYMBOL_SEARCH_HELP = """
      arend -ss <pattern> [option ...]

      Search every loaded library for definitions whose SHORT NAME matches
      <pattern>. Loads each .ard file at most once and persists a per-library
      on-disk index under <library>/<binariesDir>/.arend-symbol-index, so
      subsequent runs with no source changes are near-instant.

      PATTERN
        Foo                literal substring match (case-insensitive by
                           default). NOTHING is interpreted as a metacharacter
                           because Arend identifiers freely use *, ^, $, ?,
                           etc. (e.g. *-comm, ^-1, <*). To search for a name
                           containing '*-comm', just type '*-comm'.
        eq:<text>          exact full-name match
        glob:<pat>         '*' = any chars, '?' = any one char.
                           Use '\\*' / '\\?' for literal stars / question
                           marks (so glob:'abs\\_\\*' matches abs_*, abs_*q,
                           and so on).
        re:<java-regex>    raw java.util.regex pattern, matched with find()
        hb:<chars>         humpback / camel-and-dash boundary fuzzy match,
                           e.g. 'hb:PMA' on 'PosetAddMonoid', 'hb:p-iP' on
                           'pi-isProp'. Use `case-sensitive` for strict camel.

      EXTRA TOKENS  (each passed as a separate -ss argument)
        case-sensitive     match name case exactly (default: case-insensitive)
        no-cache           bypass the on-disk index, re-parse everything
        limit=N            cap printed matches at N (0 = unlimited; default 200)
        kind=k1,k2,...     keep only these kinds. Recognised:
                           func, sfunc, lemma, type, axiom, instance, coclause,
                           coerce, level, data, cons|constructor, class, record,
                           field, meta, other.
        only=name|self     restrict scope. Default: every library currently
                           registered (requested libs + transitive deps +
                           prelude). 'self' means the libraries listed on the
                           command line; a literal name (e.g. 'arend-lib')
                           picks that single library. Multiple values can be
                           comma-separated (e.g. only=arend-lib,liba).

      OUTPUT
        <abs-path>:<line>:<col>           or  <library:module> for generated
        <library>::<long-name>  [<KIND>]
          <signature on a single line>

      EXAMPLES
        arend -L libs my-lib -ss 'Monoid'                  (substring)
        arend -L libs my-lib -ss '*-comm'                  (literal '*-comm')
        arend -L libs my-lib -ss 'eq:pmap' -ss kind=func,lemma
        arend -L libs my-lib -ss 'glob:abs_*' -ss limit=20
        arend -L libs my-lib -ss 're:^abs.*\\+.*$'
        arend -L libs my-lib -ss 'hb:isProp' -ss case-sensitive
        arend -L libs my-lib -ss only=self -ss 'shared-name'
      """;

  private final static String PROOF_SEARCH_HELP = """
      arend -ps <pattern> [print-full]

      Search every loaded library for definitions whose SIGNATURE (parameters
      and codomain) contains expressions matching <pattern>. Unlike -ss, this
      runs name resolution on the whole library first, so it is a lot slower
      than -ss but matches by structure rather than name.

      PATTERN GRAMMAR
        expr               a single sub-expression that must appear somewhere
                           in the signature; `_` matches any subexpression
        expr \\and expr     conjunction inside one clause: both must match
                           the same parameter (or the same codomain)
        e1 -> e2 -> codom  position-aware: e1 must match a parameter, e2 must
                           match a parameter that comes later in the pi, and
                           codom must match the codomain. With one `->` the
                           left side is 'any parameter' and the right side is
                           the codomain. Patterns may be parenthesised.

      OPTIONS
        print-full         print the entire definition with matching subterms
                           highlighted, instead of only the matching slice.

      EXAMPLES
        arend -L libs my-lib -ps 'Monoid'
        arend -L libs my-lib -ps 'Group -> _ = _'
        arend -L libs my-lib -ps 'isProp \\and _ -> _'
        arend -L libs my-lib -ps 'Monoid -> _' -ps print-full

      See also: https://arend-lang.github.io/documentation/plugin-manual/navigating#proof-search
      """;

  private final static String FIND_USAGES_HELP = """
      arend -fu <MODULE_PATH>:<GROUP_PATH> [option ...]

      Find every textual usage of the named definition that resolves to it
      after name resolution. Same flow as IntelliJ's Find Usages: text-search
      narrows files, then ArendServer resolveAll validates each candidate via
      identity comparison on the resolved Referable.

      SPEC
        MODULE_PATH    dotted module path,         e.g. Algebra.Monoid
        GROUP_PATH     dotted in-module path,      e.g. Monoid.equals
                       Reaches class fields, constructors, and \\where members.

      EXTRA TOKENS  (each as a separate -fu argument)
        with-tests        also search test sources
        no-line           omit the source line content from output
        aliases=false     don't include the target's alias name in the search
        limit=N           cap printed usages at N (0 = unlimited; default 500)
        only=name|self    restrict scope (default: every loaded library;
                          'self' = libraries listed on the command line;
                          comma-separated for multiple).

      ALIAS HANDLING
        Direct uses, the target's own alias, and locally renamed imports
        (`\\import M (foo \\as bar)` then `bar` in body) are caught. Multi-hop
        renames are followed via fixed-point iteration. Implicit references
        through instance resolution are NOT caught (they have no textual form).

      OUTPUT
        Usages of <library>::<long-name>  [<KIND>]

        <abs-path>:<line>:<col>: <source line, trimmed>
        ...
        Found N usage(s)

      EXAMPLES
        arend -L libs my-lib -fu 'Algebra.Monoid:Monoid.equals'
        arend -L libs my-lib -fu 'Paths:transport' -fu limit=20 -fu no-line
        arend -L libs my-lib -fu only=self -fu 'Foo:bar'
      """;

  private final static String CLASS_HIERARCHY_HELP = """
      arend -ch <CLASS> [option ...]

      Print the inheritance lattice around a class plus every \\instance and
      \\new construction site. Resolves <CLASS> in two ways:
        - 'MODULE_PATH:GROUP_PATH'    qualified, same shape as -fu / -p
        - '<short-name>'              looked up via the symbol index, restricted
                                      to CLASS / RECORD entries. Multiple matches
                                      print the candidates so you can pick.

      EXTRA TOKENS  (each as a separate -ch argument)
        up                only superclass chain
        down              only subclass tree (and constructors of subclasses)
        no-instances      omit the \\instance section
        no-news           omit the \\new section
        with-fields       annotate each tree node with its directly-declared fields
        with-tests        also search test sources
        only=name|self    restrict library scope (default: all loaded libraries)
        format=tree|flat  output format. tree (default) uses pseudographics for
                          a human-friendly view; flat emits one tagged relation
                          per line for grep / agentic loops:
                            TARGET      lib::M:C  KIND  abs:line:col
                            EXTENDS     lib::M:C  lib::N:Parent
                            EXTENDED-BY lib::M:C  lib::M:Sub
                            INSTANCE    lib::M:C  abs:line:col  instanceName
                            NEW         lib::M:C  abs:line:col  impl=...  miss=...
        limit=N           cap printed instance / new lines (default 200; 0 = all)

      EXAMPLES
        arend -L libs my-lib -ch 'CMonoid'
        arend -L libs my-lib -ch 'Algebra.Monoid:CMonoid' -ch with-fields
        arend -L libs my-lib -ch 'BaseSet' -ch down
        arend -L libs my-lib -ch 'Monoid' -ch format=flat -ch up

      Implicit instances inferred during typechecking are NOT shown -- they have
      no source declaration. Use -fu on the class itself to see all reference
      sites instead.
      """;
  private final static String SCOPE_HELP = """
      arend -sc <REFERABLE> [<PATTERN>] [option ...]

      Dump the ambient name scope visible at a given referable's position.
      Mainly intended for debugging reference-resolution issues: which names
      are in scope here, and what do they actually resolve to?

      Resolves <REFERABLE> in two ways:
        - 'MODULE_PATH:GROUP_PATH'    qualified, same shape as -fu / -p / -ch
        - '<short-name>'              looked up via the symbol index. Multiple
                                      matches print the candidates so you can pick.

      <PATTERN> is optional and uses the same grammar as -ss (literal substring
      by default; eq:, glob:, re:, hb: prefixes available). When given, only
      scope entries whose short name matches are printed.

      EXTRA TOKENS  (each as a separate -sc argument)
        context=static    only static-scope entries (default)
        context=dynamic   only dynamic-scope entries (record/class fields)
        context=all       print STATIC, DYNAMIC, PLEVEL, HLEVEL sections in turn

      OUTPUT FORMAT
        Each in-scope entry is printed as
            SHORT_NAME -> LIBRARY::MODULE_PATH:LONG_NAME [KIND]
        (locally-bound referables that have no global location print as
            SHORT_NAME -> (local <RefType>))

      EXAMPLES
        arend -L libs my-lib -sc 'Algebra.Monoid:Monoid'
        arend -L libs my-lib -sc 'Monoid' 'hb:CM'
        arend -L libs my-lib -sc 'Algebra.Monoid:Monoid' context=all
      """;
  private final static String ANSI_GREEN = "\u001B[32m";
  private final static String ANSI_RESET = "\u001B[0m";

  private static class HighlightingPrettyPrintVisitor extends PrettyPrintVisitor {
    private final Set<Concrete.SourceNode> highlightedNodes;
    private int highlightCount = 0;

    public HighlightingPrettyPrintVisitor(StringBuilder builder, int indent, Set<Concrete.SourceNode> highlightedNodes) {
      super(builder, indent);
      this.highlightedNodes = highlightedNodes;
    }

    @Override
    protected PrettyPrintVisitor copy(StringBuilder builder, int indent, boolean doIndent) {
      return new HighlightingPrettyPrintVisitor(builder, indent, highlightedNodes);
    }

    @Override
    public void printExpr(Concrete.Expression expr, Precedence prec) {
      if (highlightedNodes.contains(expr)) {
        myBuilder.append(ANSI_GREEN);
        highlightCount++;
      }
      super.printExpr(expr, prec);
      if (highlightedNodes.contains(expr)) {
        highlightCount--;
        if (highlightCount == 0) {
          myBuilder.append(ANSI_RESET);
        }
      }
    }

    @Override
    public void prettyPrintParameter(Concrete.Parameter parameter) {
      if (highlightedNodes.contains(parameter)) {
        myBuilder.append(ANSI_GREEN);
        highlightCount++;
      }
      super.prettyPrintParameter(parameter);
      if (highlightedNodes.contains(parameter)) {
        highlightCount--;
        if (highlightCount == 0) {
          myBuilder.append(ANSI_RESET);
        }
      }
    }
  }

  private final ErrorReporter mySystemErrErrorReporter = error -> {
    System.err.println(error);
    System.err.flush();
    myExitWithError = true;
  };

  private CommandLine parseArgs(String[] args) {
    try {
      Options cmdOptions = new Options();
      cmdOptions.addOption("h", "help", false, "print this message");
      cmdOptions.addOption(Option.builder("L").longOpt("libdir").hasArg().argName("dir").desc("directory containing libraries").build());
      cmdOptions.addOption(Option.builder("s").longOpt("sources").hasArg().argName("dir").desc("project source directory").build());
      cmdOptions.addOption(Option.builder("e").longOpt("extensions").hasArg().argName("dir").desc("language extensions directory").build());
      cmdOptions.addOption(Option.builder("m").longOpt("extension-main").hasArg().argName("class").desc("main extension class").build());
      cmdOptions.addOption(Option.builder("c").longOpt("double-check").desc("double check correctness of the result").build());
      cmdOptions.addOption(Option.builder("i").longOpt("interactive").hasArg().optionalArg(true).argName("type").desc("start an interactive REPL, type can be plain or jline (default)").build());
      cmdOptions.addOption(Option.builder("p").longOpt("print").hasArg().argName("target").desc("print a definition or a module").build());
      cmdOptions.addOption(Option.builder("ps").longOpt("proof-search").hasArgs().argName("pattern")
          .desc("search by signature shape (parameters/codomain). Pass `-ps help` for the full grammar.").build());
      cmdOptions.addOption(Option.builder("ss").longOpt("symbol-search").hasArgs().argName("pattern")
          .desc("search by short name (uses an mtime-cached on-disk index). Pass `-ss help` for the full grammar.").build());
      cmdOptions.addOption(Option.builder("fu").longOpt("find-usages").hasArgs().argName("MODULE:DEF")
          .desc("find every usage of a definition. Pass `-fu help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("ch").longOpt("class-hierarchy").hasArgs().argName("CLASS")
          .desc("print super/sub-class trees plus \\new and \\instance sites. Accepts MODULE:CLASS or a bare class name (resolved via the symbol index). Pass `-ch help` for full grammar.").build());
      cmdOptions.addOption(Option.builder("sc").longOpt("scope").hasArgs().argName("REFERABLE")
          .desc("dump the ambient scope at a referable's position; debug aid for reference-resolution issues. Accepts MODULE:PATH or a bare short name, plus an optional -ss-style pattern to filter results. Pass `-sc help` for full grammar.").build());
      cmdOptions.addOption("sig", "write-signatures", false, "in addition to the chosen mode (default typecheck, -nr, or -rr), emit a signature-only mirror of every processed module to <library>/.sig/<module>.ard. Function/lemma/instance/meta bodies and class-field implementations are replaced with the goal `{?}`; data constructors, class field declarations, namespace commands, and \\where structure are preserved. Honors positional-arg granularity. Off by default.");
      cmdOptions.addOption("nr", "name-resolve", false, "only run name resolution; do not typecheck or load binary caches. Honors granularity from positional args: no args = each requested library; MODULE = that module + its transitive raw-import closure; MODULE:DEF = same plus an existence check for DEF. Requires complete binary symbol indices (build via -ss).");
      cmdOptions.addOption("rr", "reference-resolve", false, "name-resolution + auto-fix: for each unresolved reference, look candidates up in the binary symbol indices, rewrite source files when the candidate is unique, and list alternatives otherwise. Same granularity rules as -nr. Requires complete binary symbol indices (build via -ss).");
      cmdOptions.addOption("r", "recompile", false, "recompile all modules from source, ignoring binary caches (.arc files)");
      cmdOptions.addOption("t", "test", false, "run tests");
      cmdOptions.addOption("v", "version", false, "print language version");
      cmdOptions.addOption(Option.builder().longOpt(SHOW_TIMES).build());
      cmdOptions.addOption(Option.builder().longOpt(SHOW_SIZES).build());
      cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES).build());
      cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES_WITH_INSTANCES).build());
      CommandLine cmdLine = new DefaultParser().parse(cmdOptions, args);

      if (cmdLine.hasOption("h")) {
        new HelpFormatter().printHelp("arend [FILES]", cmdOptions);
        return null;
      }

      if (cmdLine.hasOption("v")) {
        System.out.println("Arend " + Prelude.VERSION);
        return null;
      }

      if (cmdLine.hasOption("ss") && containsHelpToken(cmdLine.getOptionValues("ss"))) {
        System.out.println(SYMBOL_SEARCH_HELP);
        return null;
      }

      if (cmdLine.hasOption("ps") && containsHelpToken(cmdLine.getOptionValues("ps"))) {
        System.out.println(PROOF_SEARCH_HELP);
        return null;
      }

      if (cmdLine.hasOption("fu") && containsHelpToken(cmdLine.getOptionValues("fu"))) {
        System.out.println(FIND_USAGES_HELP);
        return null;
      }

      if (cmdLine.hasOption("ch") && containsHelpToken(cmdLine.getOptionValues("ch"))) {
        System.out.println(CLASS_HIERARCHY_HELP);
        return null;
      }

      if (cmdLine.hasOption("sc") && containsHelpToken(cmdLine.getOptionValues("sc"))) {
        System.out.println(SCOPE_HELP);
        return null;
      }

      return cmdLine;
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      return null;
    }
  }

  private void updateSourceResult(ModuleLocation module, GeneralError.Level result) {
    if (module == null) return;
    GeneralError.Level prevResult = myModuleResults.get(module);
    if (prevResult == null || result.ordinal() > prevResult.ordinal()) {
      myModuleResults.put(module, result);
    }
  }

  private void reportTypeCheckResult(ModulePath modulePath, GeneralError.Level result) {
    System.out.println("[" + resultChar(result) + "]" + " " + modulePath);
  }

  private static char resultChar(GeneralError.Level result) {
    if (result == null) {
      return ' ';
    }
    return switch (result) {
      case GOAL -> '◯';
      case ERROR -> '✗';
      default -> '·';
    };
  }

  private boolean myBufferErrors = false;
  private final List<GeneralError> myBufferedErrors = new ArrayList<>();

  private final ErrorReporter myErrorReporter = new ErrorReporter() {
    @Override
    public void report(GeneralError error) {
      error.forAffectedDefinitions((referable, err) -> {
        if (referable instanceof LocatedReferable) {
          updateSourceResult(((LocatedReferable) referable).getLocation(), err.level);
        }
      });

      if (myBufferErrors) {
        myBufferedErrors.add(error);
        return;
      }

      //Print error
      PrettyPrinterConfigWithRenamer ppConfig = new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE);
      if (error instanceof GoalError) {
        ppConfig.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE);
      }
      if (error.level == GeneralError.Level.ERROR) {
        myExitWithError = true;
      }
      String errorText = error.getDoc(ppConfig).toString();

      if (error.isSevere()) {
        System.err.println(errorText);
        System.err.flush();
      } else {
        System.out.println(errorText);
        System.out.flush();
      }
    }
  };

  private void printError(GeneralError error) {
    PrettyPrinterConfigWithRenamer ppConfig = new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE);
    if (error instanceof GoalError) {
      ppConfig.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE);
    }
    if (error.level == GeneralError.Level.ERROR) {
      myExitWithError = true;
    }
    String errorText = error.getDoc(ppConfig).toString();
    if (error.isSevere()) {
      System.err.println(errorText);
      System.err.flush();
    } else {
      System.out.println(errorText);
      System.out.flush();
    }
  }

  private void showSizes(ArendServer server, SourceLibrary library) {
    Map<Definition, Integer> sizes = new HashMap<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        for (DefinitionData definitionData : server.getResolvedDefinitions(module)) {
          Definition definition = definitionData.definition().getData().getTypechecked();
          if (definition != null) {
            sizes.put(definition, SizeExpressionVisitor.getSize(definition));
          }
        }
      }
    }

    System.out.println();
    List<Pair<Definition,Integer>> list = new ArrayList<>(sizes.size());
    for (Map.Entry<Definition, Integer> entry : sizes.entrySet()) {
      list.add(new Pair<>(entry.getKey(), entry.getValue()));
    }
    list.sort((o1, o2) -> Long.compare(o2.proj2, o1.proj2));
    for (Pair<Definition, Integer> pair : list) {
      System.out.println(pair.proj1.getReferable().getRefLongName() + ": " + pair.proj2);
    }
  }

  private void printDefinitions(ArendServer server, String printString) {
    if (printString == null) return;

    Pair<ModulePath, LongName> pair = parseFullName(printString);
    if (pair != null) {
      ModuleLocation module = server.findModule(pair.proj1, null, false, false);
      if (module == null) {
        mySystemErrErrorReporter.report(new ModuleNotFoundError(pair.proj1));
      } else {
        boolean found = pair.proj2 == null;
        for (DefinitionData definitionData : server.getResolvedDefinitions(module)) {
          if (pair.proj2 == null || definitionData.definition().getData().getRefLongName().equals(pair.proj2)) {
            Definition definition = definitionData.definition().getData().getTypechecked();
            if (definition != null) {
              System.out.println();
              StringBuilder builder = new StringBuilder();
              ToAbstractVisitor.convert(definition, DEFAULT).prettyPrint(builder, DEFAULT);
              System.out.println(builder);
            }

            if (pair.proj2 != null) {
              found = true;
              break;
            }
          }
        }
        if (!found) {
          mySystemErrErrorReporter.report(new DefinitionNotFoundError(new FullName(module, pair.proj2)));
        }
      }
    }
  }

  private void showModules(ArendServer server, SourceLibrary library, boolean allModules) {
    Map<ModulePath, List<ModulePath>> map = new HashMap<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        ConcreteGroup group = server.getRawGroup(module);
        if (group == null) continue;
        boolean withInstances = allModules;
        List<ModulePath> dependencies = new ArrayList<>();
        for (ConcreteStatement statement : group.statements()) {
          ConcreteNamespaceCommand cmd = statement.command();
          if (cmd != null && cmd.isImport()) {
            dependencies.add(new ModulePath(cmd.module().getPath()));
          }
          if (!withInstances && !dependencies.isEmpty()) {
            ConcreteGroup subgroup = statement.group();
            if (subgroup != null && subgroup.referable().getKind() == GlobalReferable.Kind.INSTANCE) {
              withInstances = true;
            }
          }
        }
        if (withInstances) {
          map.put(module.getModulePath(), dependencies);
        }
      }
    }

    new MapTarjanSCC<>(map) {
      @Override
      protected void unitFound(ModulePath unit, boolean withLoops) {
        System.out.println("[" + unit + "]");
      }

      @Override
      protected void sccFound(List<ModulePath> scc) {
        System.out.println(scc);
      }
    }.order();
  }

  private Pair<ModulePath, LongName> parseFullName(String fullName) {
    ModulePath modulePath;
    LongName longName = null;
    int index = fullName.indexOf(':');
    if (index >= 0) {
      longName = LongName.fromString(fullName.substring(index + 1));
      if (!FileUtils.isCorrectDefinitionName(longName)) {
        mySystemErrErrorReporter.report(FileUtils.illegalDefinitionName(longName.toString()));
        return null;
      }
      fullName = fullName.substring(0, index);
    }
    modulePath = ModulePath.fromString(fullName);
    if (!FileUtils.isCorrectModulePath(modulePath)) {
      mySystemErrErrorReporter.report(FileUtils.illegalModuleName(modulePath.toString()));
      return null;
    }
    return new Pair<>(modulePath, longName);
  }

  private boolean run(String[] args) {
    CommandLine cmdLine = parseArgs(args);
    if (cmdLine == null) return false;

    boolean doubleCheck = cmdLine.hasOption("c");
    boolean recompile = cmdLine.hasOption("r");
    LibraryManager libraryManager = new LibraryManager(mySystemErrErrorReporter);
    CliServerRequester requester = new CliServerRequester(libraryManager);
    if (recompile) {
      requester.setRecompile(true);
    }
    ArendServer server = new ArendServerImpl(requester, false, false, !doubleCheck);
    server.addReadOnlyModule(Prelude.MODULE_LOCATION, () -> Objects.requireNonNull(new PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE)));
    server.addErrorReporter(myErrorReporter);

    // Get library directories
    List<Path> libDirs = new ArrayList<>();
    if (cmdLine.hasOption("L")) {
      for (String libDirString : cmdLine.getOptionValues("L")) {
        Path libDir = Paths.get(libDirString);
        if (Files.isDirectory(libDir)) {
          libDirs.add(libDir);
        } else {
          myExitWithError = true;
          System.err.println("[ERROR] " + libDir + " is not a directory");
        }
      }
    } else {
      Path defaultLibrariesRoot = FileUtils.defaultLibrariesRoot();
      if (Files.isDirectory(defaultLibrariesRoot)) {
        libDirs.add(defaultLibrariesRoot);
      }
    }

    if (cmdLine.hasOption("i")) {
      String replKind = cmdLine.getOptionValue("i", "jline");
      switch (replKind.toLowerCase()) {
        case "plain":
          PlainCliRepl.launch(false, libDirs, server);
          break;
        case "jline":
          JLineCliRepl.launch(false, libDirs, server);
          break;
        default:
          System.err.println("[ERROR] Unrecognized repl type: " + replKind);
          return false;
      }
      return true;
    }

    // Get source and output directories
    String sourceDirStr = cmdLine.getOptionValue("s");
    Path sourceDir = sourceDirStr == null ? null : Paths.get(sourceDirStr);

    String binaryDirStr = cmdLine.getOptionValue("b");
    Path outDir = binaryDirStr != null ? Paths.get(binaryDirStr) : null;

    String extDirStr = cmdLine.getOptionValue("e");
    Path extDir = extDirStr != null ? Paths.get(extDirStr) : null;
    String extMainClass = cmdLine.getOptionValue("m");

    // Collect modules and libraries for which typechecking was requested
    Collection<String> argFiles = cmdLine.getArgList();
    Set<Pair<ModulePath, LongName>> requestedModules = new LinkedHashSet<>();
    List<SourceLibrary> requestedLibraries = new ArrayList<>();
    for (String fileName : argFiles) {
      Path path = Paths.get(fileName);
      if (Files.exists(path)) {
        if (Files.isDirectory(path)) {
          loadFileLibrary(path.resolve(FileUtils.LIBRARY_CONFIG_FILE), requestedLibraries);
        } else if (path.endsWith(FileUtils.LIBRARY_CONFIG_FILE)) {
          loadFileLibrary(path, requestedLibraries);
        } else if (fileName.endsWith(FileUtils.ZIP_EXTENSION)) {
          loadZipLibrary(path, requestedLibraries);
        } else {
          mySystemErrErrorReporter.report(new LibraryIOError(fileName, "not a library"));
        }
      } else if (!findLibrary(fileName, libDirs, requestedLibraries)) {
        int colonIndex = fileName.indexOf(':');
        if (colonIndex >= 0) {
          Pair<ModulePath, LongName> parsed = parseFullName(fileName);
          if (parsed != null && parsed.proj2 != null) {
            requestedModules.add(parsed);
          } else if (parsed != null) {
            mySystemErrErrorReporter.report(new GeneralError(GeneralError.Level.ERROR, "Definition name missing after ':' in " + fileName));
          }
        } else {
          ModulePath modulePath = ModulePath.fromString(fileName);
          if (FileUtils.isCorrectModulePath(modulePath)) {
            requestedModules.add(new Pair<>(modulePath, null));
          } else {
            mySystemErrErrorReporter.report(new GeneralError(GeneralError.Level.ERROR, "File " + fileName + " not found"));
          }
        }
      }
    }

    if (sourceDir != null) {
      if (outDir != null) {
        try {
          Files.createDirectories(outDir);
        } catch (IOException e) {
          mySystemErrErrorReporter.report(new LibraryIOError(outDir.toString(), "Cannot create output directory", e.getLocalizedMessage()));
          outDir = null;
        }
      }

      requestedLibraries.add(new FileSourceLibrary("\\default", false, -1,
          requestedLibraries.stream().map(SourceLibrary::getLibraryName).toList(), null, null, extMainClass, null,
          sourceDir, outDir, null, extDir == null ? null : new FileClassLoaderDelegate(extDir)));
    }

    if (requestedLibraries.isEmpty()) {
      Path config = Paths.get(FileUtils.LIBRARY_CONFIG_FILE);
      if (Files.isRegularFile(config)) {
        loadFileLibrary(config, requestedLibraries);
      } else {
        System.out.println("Nothing to load");
        return true;
      }
    }

    if (myExitWithError) {
      return false;
    }

    for (SourceLibrary library : requestedLibraries) {
      loadLibrary(libraryManager, library, server);
    }

    for (SourceLibrary library : requestedLibraries) {
      if (!loadDependencies(library, libraryManager, libDirs, server)) {
        return false;
      }
    }

    if (myExitWithError) {
      return false;
    }

    if (cmdLine.hasOption("ss")) {
      org.arend.frontend.symbol.SymbolSearch.Parsed parsed =
          org.arend.frontend.symbol.SymbolSearch.parseArgs(cmdLine.getOptionValues("ss"), mySystemErrErrorReporter);
      if (parsed == null) return false;
      org.arend.frontend.symbol.SymbolSearch.run(parsed.pattern(), parsed.options(),
          requestedLibraries, libraryManager, server, mySystemErrErrorReporter);
      return true;
    }

    if (cmdLine.hasOption("fu")) {
      org.arend.frontend.symbol.UsageSearch.Parsed parsed =
          org.arend.frontend.symbol.UsageSearch.parseArgs(cmdLine.getOptionValues("fu"));
      if (parsed == null) return false;
      org.arend.frontend.symbol.UsageSearch.run(parsed.spec(), parsed.options(),
          requestedLibraries, libraryManager, server, mySystemErrErrorReporter);
      return true;
    }

    if (cmdLine.hasOption("ch")) {
      org.arend.frontend.symbol.ClassHierarchy.Parsed parsed =
          org.arend.frontend.symbol.ClassHierarchy.parseArgs(cmdLine.getOptionValues("ch"));
      if (parsed == null) return false;
      org.arend.frontend.symbol.ClassHierarchy.run(parsed.spec(), parsed.options(),
          requestedLibraries, libraryManager, server, mySystemErrErrorReporter);
      return true;
    }

    if (cmdLine.hasOption("sc")) {
      org.arend.frontend.symbol.ReferableScope.Parsed parsed =
          org.arend.frontend.symbol.ReferableScope.parseArgs(cmdLine.getOptionValues("sc"));
      if (parsed == null) return false;
      org.arend.frontend.symbol.ReferableScope.run(parsed.spec(), parsed.pattern(), parsed.options(),
          requestedLibraries, libraryManager, server, mySystemErrErrorReporter);
      return true;
    }

    boolean writeSignatures = cmdLine.hasOption("sig");

    if (cmdLine.hasOption("nr")) {
      if (!verifyBinaryIndices(requestedLibraries)) return false;
      return runNameResolveOnly(server, requestedLibraries, requestedModules, writeSignatures);
    }

    if (cmdLine.hasOption("rr")) {
      if (!verifyBinaryIndices(requestedLibraries)) return false;
      return runReferenceResolveOnly(server, requestedLibraries, requestedModules, libraryManager, writeSignatures);
    }

    if (cmdLine.hasOption("ps")) {
      String[] psArgs = cmdLine.getOptionValues("ps");
      boolean printFull = false;
      List<String> patterns = new ArrayList<>();
      for (String arg : psArgs) {
        if (arg.equals(PRINT_FULL)) {
          printFull = true;
        } else {
          patterns.add(arg);
        }
      }
      if (patterns.isEmpty()) {
        System.err.println("[ERROR] Missing proof search pattern");
        return false;
      }
      if (patterns.size() > 1) {
        System.err.println("[ERROR] Only one proof search pattern is allowed. Use quotes if the pattern contains spaces.");
        return false;
      }
      return matchAndPrint(server, libraryManager, requestedLibraries, patterns.getFirst(), printFull);
    }

    TimedProgressReporter timedProgressReporter = cmdLine.hasOption(SHOW_TIMES) ? new TimedProgressReporter() : null;
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter = timedProgressReporter != null ? timedProgressReporter : ProgressReporter.empty();

    // Pre-load binary caches (unless --recompile is set)
    if (!recompile) {
      // Typecheck Prelude first — binary cache loading needs Prelude definitions to be available
      server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      if (requestedModules.isEmpty()) {
        // Whole-library typechecking: pre-load every module of each requested library.
        for (SourceLibrary library : requestedLibraries) {
          List<ModuleLocation> allModules = library.findModules(false).stream()
              .map(mp -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
              .toList();
          if (!allModules.isEmpty()) {
            // resolveAll forces raw loading of all modules and their transitive dependencies
            server.getCheckerFor(allModules).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
            // Now load typechecked definitions from binary caches
            requester.loadBinaryCache(library, server);
          }
        }
      } else {
        // Targeted typechecking: seed resolveAll with just the requested modules
        // so only their transitive import cone is raw-loaded. loadBinaryCache then
        // iterates server.getModules() — by now the cone — and only deserializes
        // ARCs in it, avoiding the cost of touching every cached file in a large
        // dependency library like arend-lib.
        List<ModuleLocation> targets = new ArrayList<>();
        for (Pair<ModulePath, LongName> requested : requestedModules) {
          ModuleLocation module = server.findModule(requested.proj1, null, true, false);
          if (module != null) targets.add(module);
        }
        if (!targets.isEmpty()) {
          server.getCheckerFor(targets).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
          for (SourceLibrary library : requestedLibraries) {
            requester.loadBinaryCache(library, server);
          }
        }
      }
      // Report goals from definitions loaded from binary cache
      reportCachedGoals(server, requester.getBinaryCacheLoaded());
    }

    if (requestedModules.isEmpty()) {
      for (SourceLibrary library : requestedLibraries) {
        System.out.println();
        System.out.println("--- Typechecking " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        for (ModulePath modulePath : library.findModules(false)) {
          server.getCheckerFor(Collections.singletonList(new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath))).typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);
        }

        time = System.currentTimeMillis() - time;

        // Output nice per-module typechecking results
        int numWithErrors = 0;
        int numWithGoals = 0;
        for (ModuleLocation module : server.getModules()) {
          if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
            GeneralError.Level result = myModuleResults.get(module);
            reportTypeCheckResult(module.getModulePath(), result);
            if (result == GeneralError.Level.ERROR) numWithErrors++;
            if (result == GeneralError.Level.GOAL) numWithGoals++;
          }
        }

        if (numWithErrors > 0) {
          myExitWithError = true;
          System.out.println("Number of modules with errors: " + numWithErrors);
        }
        if (numWithGoals > 0) {
          System.out.println("Number of modules with goals: " + numWithGoals);
        }
        System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");

        if (cmdLine.hasOption(SHOW_SIZES)) {
          showSizes(server, library);
        }

        if (cmdLine.hasOption(SHOW_MODULES)) {
          System.out.println();
          System.out.println("Modules cycles:");
          showModules(server, library, true);
        }

        if (cmdLine.hasOption(SHOW_MODULES_WITH_INSTANCES)) {
          System.out.println();
          System.out.println("Modules with instances cycles:");
          showModules(server, library, false);
        }

        if (doubleCheck && numWithErrors == 0) {
          System.out.println();
          System.out.println("--- Checking " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            CoreModuleChecker checker = new CoreModuleChecker(myErrorReporter);
            for (ModuleLocation module : server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
                ConcreteGroup group = server.getRawGroup(module);
                if (group != null) {
                  checker.checkGroup(group);
                }
              }
            }
          } finally {
            time = System.currentTimeMillis() - time;
            System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
        }

        persistLibrary(library, server, requester.getBinaryCacheLoaded());

        if (writeSignatures) emitSignaturesFor(server, library, null);
      }
    } else {
      for (Pair<ModulePath, LongName> requested : requestedModules) {
        ModulePath modulePath = requested.proj1;
        LongName definitionName = requested.proj2;
        ModuleLocation module = server.findModule(modulePath, null, true, false);
        if (module == null) {
          mySystemErrErrorReporter.report(new ModuleNotFoundError(modulePath));
        } else if (definitionName != null) {
          System.out.println();
          FullName fullName = new FullName(module, definitionName);
          System.out.println("--- Typechecking " + fullName + " ---");
          long time = System.currentTimeMillis();

          server.getCheckerFor(Collections.singletonList(module)).typecheck(Collections.singletonList(fullName), myErrorReporter, UnstoppableCancellationIndicator.INSTANCE, progressReporter);

          System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");
        } else {
          System.out.println();
          System.out.println("--- Typechecking " + module + " ---");
          long time = System.currentTimeMillis();

          server.getCheckerFor(Collections.singletonList(module)).typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);

          System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");

          if (doubleCheck) {
            System.out.println();
            System.out.println("--- Checking " + module + " ---");
            time = System.currentTimeMillis();

            try {
              CoreModuleChecker checker = new CoreModuleChecker(myErrorReporter);
              ConcreteGroup group = server.getRawGroup(module);
              if (group != null) {
                checker.checkGroup(group);
              }
            } finally {
              time = System.currentTimeMillis() - time;
              System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
            }
          }
        }
      }
      // Persist all libraries that had modules typechecked
      for (SourceLibrary library : requestedLibraries) {
        persistLibrary(library, server, requester.getBinaryCacheLoaded());
      }
      if (writeSignatures) {
        Set<ModulePath> only = collectModulePaths(requestedModules);
        for (SourceLibrary library : requestedLibraries) {
          emitSignaturesFor(server, library, only);
        }
      }
    }

    printDefinitions(server, cmdLine.getOptionValue("p"));

    if (cmdLine.hasOption("t")) {
      for (SourceLibrary library : requestedLibraries) {
        System.out.println();
        System.out.println("--- Running tests in " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        for (ModulePath modulePath : library.findModules(true)) {
          server.getCheckerFor(Collections.singletonList(new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.TEST, modulePath))).typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);
        }

        time = System.currentTimeMillis() - time;

        int[] total = new int[1];
        int[] failed = new int[1];
        for (ModuleLocation module : server.getModules()) {
          if (module.getLocationKind() == ModuleLocation.LocationKind.TEST && module.getLibraryName().equals(library.getLibraryName())) {
            for (ConcreteStatement statement : Objects.requireNonNull(server.getRawGroup(module)).statements()) {
              if (statement.group() != null && statement.group().referable() instanceof TCDefReferable referable) {
                Definition definition = referable.getTypechecked();
                if (definition != null || referable.getKind().isTypecheckable()) {
                  total[0]++;
                  if (definition == null || definition.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
                    failed[0]++;
                  }
                }
              }
            }
          }
        }

        System.out.println("Tests completed: " + total[0] + ", Failed: " + failed[0]);
        System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");

        if (doubleCheck) {
          System.out.println();
          System.out.println("--- Checking tests in " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            CoreModuleChecker checker = new CoreModuleChecker(myErrorReporter);
            for (ModuleLocation module : server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.TEST && module.getLibraryName().equals(library.getLibraryName())) {
                ConcreteGroup group = server.getRawGroup(module);
                if (group != null) {
                  checker.checkGroup(group);
                }
              }
            }
          } finally {
            time = System.currentTimeMillis() - time;
            System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
        }
      }
    }

    if (timedProgressReporter != null) {
      timedProgressReporter.print();
    }

    return true;
  }

  /**
   * Scans definitions loaded from binary cache for goals ({@code {?}}) and reports them.
   * The goal flag ({@code isGoal}) is preserved in .arc files, so we can detect goals
   * without re-typechecking.
   */
  private void reportCachedGoals(ArendServer server, Set<ModuleLocation> cachedModules) {
    for (ModuleLocation module : cachedModules) {
      ConcreteGroup group = server.getRawGroup(module);
      if (group == null) continue;
      reportGoalsInGroup(group, module);
    }
  }

  private void reportGoalsInGroup(ConcreteGroup group, ModuleLocation module) {
    LocatedReferable ref = group.referable();
    if (ref instanceof TCDefReferable tcRef) {
      Definition def = tcRef.getTypechecked();
      if (def != null && def.getGoals().contains(def)) {
        GeneralError goalError = new GeneralError(GeneralError.Level.GOAL, "Goal") {
          @Override
          public Object getCause() {
            return ref;
          }
        };
        myErrorReporter.report(goalError);
      }
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) {
        reportGoalsInGroup(statement.group(), module);
      }
    }
    for (ConcreteGroup dynGroup : group.dynamicGroups()) {
      reportGoalsInGroup(dynGroup, module);
    }
  }

  private void persistLibrary(SourceLibrary library, ArendServer server, Set<ModuleLocation> skipModules) {
    if (!library.supportsPersisting()) return;
    int persisted = 0;
    int skipped = 0;
    int failed = 0;
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        if (skipModules.contains(module)) {
          skipped++;
          continue;
        }
        PersistableBinarySource binarySource = library.getBinarySource(module.getModulePath());
        if (binarySource != null) {
          if (binarySource.persist(server, mySystemErrErrorReporter)) {
            persisted++;
          } else {
            failed++;
          }
        }
      }
    }
    if (persisted > 0 || failed > 0) {
      System.out.println("[INFO] Persisted " + persisted + " module(s)" + (failed > 0 ? ", " + failed + " failed" : "") + (skipped > 0 ? " (" + skipped + " up-to-date)" : ""));
    }
  }

  private void loadLibrary(LibraryManager libraryManager, SourceLibrary library, ArendServer server) {
    System.out.println("[INFO] Loading " + library.getLibraryName());
    long time = System.currentTimeMillis();
    libraryManager.updateLibrary(library, server);
    System.out.println("[INFO] " + "Loaded " + library.getLibraryName() + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
  }

  private boolean loadDependencies(SourceLibrary library, LibraryManager libraryManager, List<Path> libDirs, ArendServer server) {
    for (String dependency : library.getLibraryDependencies()) {
      if (!libraryManager.containsLibrary(dependency)) {
        List<SourceLibrary> libDependency = new ArrayList<>(1);
        findLibrary(dependency, libDirs, libDependency);
        if (libDependency.isEmpty()) return false;
        loadLibrary(libraryManager, libDependency.getFirst(), server);
        if (!loadDependencies(libDependency.getFirst(), libraryManager, libDirs, server)) return false;
      }
    }
    return true;
  }

  private boolean findLibrary(String libName, List<Path> libDirs, List<SourceLibrary> result) {
    if (!FileUtils.isLibraryName(libName)) return false;

    for (Path libDir : libDirs) {
      Path configFile = libDir.resolve(libName).resolve(FileUtils.LIBRARY_CONFIG_FILE);
      if (Files.isRegularFile(configFile)) {
        loadFileLibrary(configFile, result);
        return true;
      } else {
        Path zipFile = libDir.resolve(libName + FileUtils.ZIP_EXTENSION);
        if (Files.isRegularFile(zipFile)) {
          loadZipLibrary(zipFile, result);
          return true;
        }
      }
    }

    return false;
  }

  private void loadFileLibrary(Path configFile, List<SourceLibrary> result) {
    SourceLibrary library = FileSourceLibrary.fromConfigFile(configFile, false, mySystemErrErrorReporter);
    if (library != null) {
      result.add(library);
    } else {
      myExitWithError = true;
    }
  }

  private void loadZipLibrary(Path zipFile, List<SourceLibrary> result) {
    SourceLibrary library = ZipSourceLibrary.fromFile(zipFile.toFile(), mySystemErrErrorReporter);
    if (library != null) {
      result.add(library);
    } else {
      myExitWithError = true;
    }
  }

  private boolean verifyBinaryIndices(List<SourceLibrary> libraries) {
    boolean ok = true;
    for (SourceLibrary library : libraries) {
      if (!(library instanceof org.arend.frontend.library.FileSourceLibrary fl)) continue;
      if (fl.getBinaryBasePath() == null) continue;
      org.arend.frontend.symbol.SymbolIndex idx = org.arend.frontend.symbol.SymbolIndex.loadOrCreate(library);
      List<ModulePath> stale = new ArrayList<>();
      for (ModulePath mp : library.findModules(false)) {
        if (idx.isStale(library, mp)) stale.add(mp);
      }
      if (!stale.isEmpty()) {
        ok = false;
        System.err.println("[ERROR] Binary symbol index for library '" + library.getLibraryName()
            + "' is missing or stale (" + stale.size() + " module(s)).");
        int shown = 0;
        for (ModulePath mp : stale) {
          if (shown++ >= 5) { System.err.println("        ... and " + (stale.size() - shown + 1) + " more"); break; }
          System.err.println("        " + mp);
        }
      }
    }
    if (!ok) {
      System.err.println("        Build the indices first by running '-ss <pattern>' on each affected library.");
      myExitWithError = true;
    }
    return ok;
  }

  private boolean runReferenceResolveOnly(ArendServer server, List<SourceLibrary> requestedLibraries, Set<Pair<ModulePath, LongName>> requestedModules, LibraryManager libraryManager, boolean writeSignatures) {
    myBufferErrors = true;
    myBufferedErrors.clear();
    try {
      runResolveAllForRRArgs(server, requestedLibraries, requestedModules);
    } finally {
      myBufferErrors = false;
    }

    org.arend.frontend.symbol.ReferenceResolveAutoFix.Result result =
        org.arend.frontend.symbol.ReferenceResolveAutoFix.process(server, libraryManager, myBufferedErrors, requestedLibraries);

    // Print non-fixable errors (with candidate suggestions when present).
    for (GeneralError error : result.errorsToPrint()) {
      printError(error);
    }
    // Print suggestion blocks for ambiguous errors.
    for (String block : result.suggestionBlocks()) {
      System.out.println(block);
      System.out.flush();
    }
    // Print INFO lines for fixed references.
    for (String info : result.infoMessages()) {
      System.out.println(info);
      System.out.flush();
    }
    // Print non-core warnings (e.g. selective imports that miss a data type's constructors).
    for (String warning : result.warnings()) {
      System.out.println(warning);
      System.out.flush();
    }

    if (!result.modifiedFiles().isEmpty()) {
      System.out.println();
      System.out.println("[INFO] Modified " + result.modifiedFiles().size() + " file(s):");
      for (Path p : result.modifiedFiles()) System.out.println("        " + p);
      System.out.println("[INFO] Re-run the CLI to pick up the rewritten sources.");
    }

    if (writeSignatures) {
      Set<ModulePath> only = collectModulePaths(requestedModules);
      for (SourceLibrary library : requestedLibraries) {
        emitSignaturesFor(server, library, only);
      }
    }

    return !myExitWithError;
  }

  private void runResolveAllForRRArgs(ArendServer server, List<SourceLibrary> requestedLibraries, Set<Pair<ModulePath, LongName>> requestedModules) {
    if (requestedModules.isEmpty()) {
      for (SourceLibrary library : requestedLibraries) {
        System.out.println();
        System.out.println("--- Resolving " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();
        List<ModuleLocation> modules = library.findModules(false).stream()
            .map(mp -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
            .toList();
        if (!modules.isEmpty()) {
          server.getCheckerFor(modules).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
        }
        System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");
      }
      return;
    }

    for (Pair<ModulePath, LongName> requested : requestedModules) {
      ModulePath modulePath = requested.proj1;
      LongName definitionName = requested.proj2;
      ModuleLocation module = server.findModule(modulePath, null, true, false);
      if (module == null) {
        mySystemErrErrorReporter.report(new ModuleNotFoundError(modulePath));
        continue;
      }
      System.out.println();
      System.out.println("--- Resolving " + module + " ---");
      long time = System.currentTimeMillis();
      server.getCheckerFor(Collections.singletonList(module)).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");

      if (definitionName != null) {
        boolean found = false;
        for (DefinitionData data : server.getResolvedDefinitions(module)) {
          if (data.definition().getData().getRefLongName().equals(definitionName)) {
            found = true;
            break;
          }
        }
        if (!found) {
          mySystemErrErrorReporter.report(new DefinitionNotFoundError(new FullName(module, definitionName)));
        }
      }
    }
  }

  private boolean runNameResolveOnly(ArendServer server, List<SourceLibrary> requestedLibraries, Set<Pair<ModulePath, LongName>> requestedModules, boolean writeSignatures) {
    if (requestedModules.isEmpty()) {
      for (SourceLibrary library : requestedLibraries) {
        System.out.println();
        System.out.println("--- Resolving " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();
        List<ModuleLocation> modules = library.findModules(false).stream()
            .map(mp -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
            .toList();
        if (!modules.isEmpty()) {
          server.getCheckerFor(modules).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
        }
        System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");
        if (writeSignatures) emitSignaturesFor(server, library, null);
      }
      return !myExitWithError;
    }

    for (Pair<ModulePath, LongName> requested : requestedModules) {
      ModulePath modulePath = requested.proj1;
      LongName definitionName = requested.proj2;
      ModuleLocation module = server.findModule(modulePath, null, true, false);
      if (module == null) {
        mySystemErrErrorReporter.report(new ModuleNotFoundError(modulePath));
        continue;
      }
      System.out.println();
      System.out.println("--- Resolving " + module + " ---");
      long time = System.currentTimeMillis();
      server.getCheckerFor(Collections.singletonList(module)).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");

      if (definitionName != null) {
        boolean found = false;
        for (DefinitionData data : server.getResolvedDefinitions(module)) {
          if (data.definition().getData().getRefLongName().equals(definitionName)) {
            found = true;
            break;
          }
        }
        if (!found) {
          mySystemErrErrorReporter.report(new DefinitionNotFoundError(new FullName(module, definitionName)));
        }
      }
    }
    if (writeSignatures) {
      Set<ModulePath> only = collectModulePaths(requestedModules);
      for (SourceLibrary library : requestedLibraries) {
        emitSignaturesFor(server, library, only);
      }
    }
    return !myExitWithError;
  }

  private static Set<ModulePath> collectModulePaths(Set<Pair<ModulePath, LongName>> requestedModules) {
    Set<ModulePath> result = new HashSet<>();
    for (Pair<ModulePath, LongName> p : requestedModules) result.add(p.proj1);
    return result;
  }

  private void emitSignaturesFor(ArendServer server, SourceLibrary library, Set<ModulePath> only) {
    SignatureFileWriter.Result r = SignatureFileWriter.writeFiltered(server, library, only);
    for (String err : r.errors()) System.err.println(err);
    System.out.println("[INFO] -sig wrote " + r.written() + " signature file(s) for "
        + library.getLibraryName() + (r.skipped() > 0 ? " (skipped " + r.skipped() + ")" : ""));
  }

  private boolean matchAndPrint(ArendServer server, LibraryManager libraryManager, List<SourceLibrary> requestedLibraries, String pattern, boolean printFull) {
    ProofSearchQuery.ParsingResult<ProofSearchQuery> queryResult = ProofSearchQuery.fromString(pattern);
    if (queryResult == null) return false;
    if (queryResult instanceof ProofSearchQuery.ParsingResult.Error<ProofSearchQuery> error) {
      System.err.println("Search pattern error at " + error.range + ": " + error.message);
      return false;
    }
    ProofSearchQuery query = ((ProofSearchQuery.ParsingResult.OK<ProofSearchQuery>) queryResult).value;
    ArendExpressionMatcher matcher = new ArendExpressionMatcher(query);

    for (SourceLibrary library : requestedLibraries) {
      System.out.println("[INFO] Resolving " + library.getLibraryName());
      long time = System.currentTimeMillis();
      server.getCheckerFor(library.findModules(false).stream().map(modulePath -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath)).toList())
              .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      System.out.println("[INFO] " + "Resolved " + library.getLibraryName() + " (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ")");
    }

    int matches = 0;
    for (ModuleLocation moduleLocation : server.getModules()) {
      for (DefinitionData data : server.getResolvedDefinitions(moduleLocation)) {
        for (Triple<Concrete.GeneralDefinition, List<Concrete.Expression>, Concrete.Expression> signature : getSignatures(data.definition())) {
          TCDefReferable referable = signature.first().getData();
          List<Concrete.Expression> parameters = signature.second();
          Concrete.Expression codomain = signature.third();

          Scope scope = server.getReferableScope(data.definition().getData());

          ArendExpressionMatcher.ProofSearchMatchingResult result = matcher.match(parameters, codomain, scope);
          if (result == null) continue;
          matches++;

          Set<Concrete.SourceNode> highlightedNodes = new HashSet<>(result.inCodomain());
          if (result.inPattern() != null) {
            for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
              highlightedNodes.addAll(parameterData.proj2);
            }
          }

          // Render the signature into a single buffer so we can indent it uniformly.
          StringBuilder sigBuilder = new StringBuilder();
          Precedence topPrec = new Precedence(Concrete.Expression.PREC);
          if (printFull) {
            HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(sigBuilder, 0, highlightedNodes);
            data.definition().accept(visitor, null);
          } else {
            if (result.inPattern() != null) {
              for (Pair<Concrete.Expression, List<Concrete.Expression>> parameterData : result.inPattern()) {
                HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(sigBuilder, 0, highlightedNodes);
                sigBuilder.append("(");
                parameterData.proj1.prettyPrint(visitor, topPrec);
                sigBuilder.append(") -> ");
              }
            }
            HighlightingPrettyPrintVisitor visitor = new HighlightingPrettyPrintVisitor(sigBuilder, 0, highlightedNodes);
            codomain.prettyPrint(visitor, topPrec);
          }

          // Header line: <abs-path>:<line>:<col>  or  <library:module> when no source.
          System.out.println(headerLineFor(moduleLocation, referable, libraryManager));
          // Identity line: <library>::<long-name>  [<KIND>]
          System.out.println(moduleLocation.getLibraryName() + "::" + referable.getRefLongName() + "  [" + kindLabel(referable, signature.first()) + "]");
          // Signature, indented two spaces (and through any embedded newlines).
          System.out.println(indentMultiline(sigBuilder.toString(), "  "));
          System.out.println();
        }
      }
    }

    if (matches == 0) {
      System.out.println("No matches.");
    } else {
      System.out.println("Found " + matches + " match" + (matches == 1 ? "" : "es"));
    }
    return true;
  }

  private static String headerLineFor(ModuleLocation moduleLocation, TCDefReferable referable, LibraryManager libraryManager) {
    String libName = moduleLocation.getLibraryName();
    SourceLibrary lib = libraryManager.getLibrary(libName);
    if (lib instanceof org.arend.frontend.library.FileSourceLibrary fl
        && moduleLocation.getLocationKind() == ModuleLocation.LocationKind.SOURCE) {
      Path src = fl.getSourceBasePath();
      if (src != null) {
        try {
          Path abs = org.arend.util.FileUtils.sourceFile(src, moduleLocation.getModulePath()).toAbsolutePath().normalize();
          int line = 0, col = 0;
          if (referable.getData() instanceof org.arend.error.SourcePosition sp) {
            line = sp.line;
            col = sp.column;
          }
          return line > 0 ? abs + ":" + line + ":" + col : abs.toString();
        } catch (RuntimeException ignored) {
          // fall through to synthetic label
        }
      }
    }
    return "<" + libName + ":" + moduleLocation.getModulePath() + ">";
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

  private static String indentMultiline(String s, String prefix) {
    if (s.isEmpty()) return prefix;
    StringBuilder out = new StringBuilder(s.length() + prefix.length() * 4);
    out.append(prefix);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      out.append(c);
      if (c == '\n' && i + 1 < s.length()) out.append(prefix);
    }
    return out.toString();
  }

  public static void main(String[] args) {
    ConsoleMain main = new ConsoleMain();
    if (!main.run(args) || main.myExitWithError) {
      System.exit(1);
    }
  }
}
