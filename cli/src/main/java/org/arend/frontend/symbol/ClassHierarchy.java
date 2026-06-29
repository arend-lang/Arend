package org.arend.frontend.symbol;

import org.arend.error.SourcePosition;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.BaseConcreteExpressionVisitor;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

/**
 * Implements `-ch` class hierarchy.
 *
 * Pipeline:
 *   1. Resolve the spec into a single class referable. Accepts either
 *      MODULE_PATH:GROUP_PATH (same shape as -fu / -p) or a bare short name
 *      that the symbol index disambiguates.
 *   2. Refresh symbol indexes for in-scope libraries.
 *   3. resolveAll on every source module in scope, then walk the resolved
 *      Concrete.ClassDefinitions to build directParents / directChildren.
 *   4. Walk every resolved definition with two visitors:
 *        a) NewSiteVisitor: catches Concrete.NewExpression and records its
 *           target class plus the coclause-implemented field set.
 *        b) For each Concrete.FunctionDefinition with INSTANCE kind, peel
 *           the result type to find the underlying class ReferenceExpression.
 *   5. Print as tree (default; pseudo-graphics, with diamond dedup) or flat
 *      (one tagged relation per line, agent-friendly).
 */
public final class ClassHierarchy {

  // ---- options + arg parsing ---------------------------------------------

  public enum Direction { BOTH, UP, DOWN }
  public enum Format { TREE, FLAT }

  public static final class Options {
    public Direction direction = Direction.BOTH;
    public Format format = Format.TREE;
    public boolean noInstances = false;
    public boolean noNews = false;
    public boolean withFields = false;
    public boolean withTests = false;
    public int limit = 200;
    public @Nullable Set<String> onlyLibraries = null;
  }

  public record Parsed(String spec, Options options) {}

  public static @Nullable Parsed parseArgs(String[] args) {
    Options opts = new Options();
    String spec = null;
    for (String arg : args) {
      switch (arg) {
        case "up" -> opts.direction = Direction.UP;
        case "down" -> opts.direction = Direction.DOWN;
        case "no-instances" -> opts.noInstances = true;
        case "no-news" -> opts.noNews = true;
        case "with-fields" -> opts.withFields = true;
        case "with-tests" -> opts.withTests = true;
        case "format=tree" -> opts.format = Format.TREE;
        case "format=flat" -> opts.format = Format.FLAT;
        default -> {
          if (arg.startsWith("limit=")) {
            try { opts.limit = Integer.parseInt(arg.substring("limit=".length())); }
            catch (NumberFormatException e) {
              System.err.println("[ERROR] Bad -ch limit: " + arg);
              return null;
            }
          } else if (arg.startsWith("only=")) {
            if (opts.onlyLibraries == null) opts.onlyLibraries = new HashSet<>();
            for (String s : arg.substring("only=".length()).split(",")) {
              if (!s.isEmpty()) opts.onlyLibraries.add(s.trim());
            }
          } else if (arg.startsWith("format=")) {
            System.err.println("[ERROR] Unknown -ch format: " + arg);
            return null;
          } else if (spec == null) {
            spec = arg;
          } else {
            System.err.println("[ERROR] Multiple -ch specs: '" + spec + "' and '" + arg + "'.");
            return null;
          }
        }
      }
    }
    if (spec == null) {
      System.err.println("[ERROR] -ch requires a class spec");
      return null;
    }
    return new Parsed(spec, opts);
  }

  // ---- top-level driver --------------------------------------------------

  public static int run(@NotNull String spec,
                        @NotNull Options options,
                        @NotNull List<SourceLibrary> requestedLibraries,
                        @NotNull LibraryManager libraryManager,
                        @NotNull ArendServer server,
                        @NotNull ErrorReporter errorReporter) {
    List<SourceLibrary> libsInScope = librariesInScope(requestedLibraries, libraryManager, options);
    if (libsInScope.isEmpty()) {
      System.err.println("[ERROR] No libraries in scope.");
      return 0;
    }

    // 1) Refresh per-library symbol indexes (needed for bare-name lookup, and
    //    cheaply registers every source module on the server so we can query
    //    raw groups below).
    Map<SourceLibrary, SymbolIndex> indexes = new LinkedHashMap<>();
    for (SourceLibrary lib : libsInScope) {
      SymbolIndex idx = SymbolIndex.loadOrCreate(lib);
      for (ModulePath mp : lib.findModules(false)) {
        if (idx.isStale(lib, mp)) {
          server.findModule(mp, lib.getLibraryName(), false, false);
        }
      }
      if (options.withTests) {
        for (ModulePath mp : lib.findModules(true)) {
          server.findModule(mp, lib.getLibraryName(), true, false);
        }
      }
      idx.refresh(lib, server, false);
      idx.save();
      indexes.put(lib, idx);
    }

    // 2) Resolve target spec: qualified, or bare-name via index.
    ResolvedTarget target = resolveTarget(spec, server, libsInScope, indexes);
    if (target == null) return 0;
    SymbolIndex.Kind targetKind = target.kind;
    if (targetKind != SymbolIndex.Kind.CLASS && targetKind != SymbolIndex.Kind.RECORD) {
      System.err.println("[ERROR] " + target.fullLabel() + " is " + targetKind + ", not a class/record.");
      return 0;
    }

    // 3) resolveAll on every source module in scope.
    List<ModuleLocation> allSources = new ArrayList<>();
    for (SourceLibrary lib : libsInScope) {
      for (ModulePath mp : lib.findModules(false)) {
        allSources.add(new ModuleLocation(lib.getLibraryName(),
            ModuleLocation.LocationKind.SOURCE, mp));
      }
      if (options.withTests) {
        for (ModulePath mp : lib.findModules(true)) {
          allSources.add(new ModuleLocation(lib.getLibraryName(),
              ModuleLocation.LocationKind.TEST, mp));
        }
      }
    }
    if (!allSources.isEmpty()) {
      server.getCheckerFor(allSources)
          .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    }

    // 4) Walk every resolved class to build parent / child maps + concrete
    //    bookkeeping for missing-field calculations.
    Map<LocatedReferable, ClassNode> graph = new HashMap<>();
    Map<LocatedReferable, ModuleLocation> moduleOf = new HashMap<>();
    NewSiteCollector newCollector = new NewSiteCollector();
    InstanceCollector instanceCollector = new InstanceCollector();

    for (ModuleLocation moduleLoc : allSources) {
      for (DefinitionData data : server.getResolvedDefinitions(moduleLoc)) {
        Concrete.ResolvableDefinition def = data.definition();
        if (def == null) continue;
        if (def instanceof Concrete.ClassDefinition cdef) {
          LocatedReferable ref = cdef.getData();
          ClassNode node = graph.computeIfAbsent(ref, k -> new ClassNode());
          node.referable = ref;
          node.isRecord = cdef.isRecord();
          node.module = moduleLoc;
          moduleOf.put(ref, moduleLoc);
          for (Concrete.ReferenceExpression sup : cdef.getSuperClasses()) {
            if (sup.getReferent() instanceof LocatedReferable parent) {
              node.directParents.add(parent);
              ClassNode parentNode = graph.computeIfAbsent(parent, k -> new ClassNode());
              parentNode.referable = parent;
              parentNode.directChildren.add(ref);
            }
          }
          // Record direct field names (concrete class elements only).
          for (Concrete.ClassElement el : cdef.getElements()) {
            if (el instanceof Concrete.ClassField field) {
              node.directFieldNames.add(field.getData().textRepresentation());
            }
          }
        }
        // Visit body for \new sites.
        try {
          newCollector.context = new VisitorContext(moduleLoc, data);
          def.accept(newCollector, null);
        } catch (RuntimeException ignored) {}

        // Detect \instance declarations.
        if (def instanceof Concrete.FunctionDefinition fdef
            && fdef.getKind() == org.arend.ext.concrete.definition.FunctionKind.INSTANCE) {
          LocatedReferable cls = peelClassFromType(fdef.getResultType());
          if (cls != null) {
            instanceCollector.add(cls, fdef.getData(), moduleLoc);
          }
        }
      }
    }

    // 5) Print.
    Printer printer = options.format == Format.FLAT ? new FlatPrinter() : new TreePrinter();
    printer.print(target, graph, moduleOf, newCollector, instanceCollector,
        options, libraryManager);
    return 0;
  }

  // ---- target resolution -------------------------------------------------

  private static final class ResolvedTarget {
    final LocatedReferable referable;
    final ModuleLocation module;
    final SymbolIndex.Kind kind;
    final String libraryName;

    ResolvedTarget(LocatedReferable referable, ModuleLocation module, SymbolIndex.Kind kind, String libraryName) {
      this.referable = referable;
      this.module = module;
      this.kind = kind;
      this.libraryName = libraryName;
    }

    String fullLabel() {
      return libraryName + "::" + module.getModulePath() + ":" + referable.getRefLongName();
    }
  }

  private static @Nullable ResolvedTarget resolveTarget(String spec, ArendServer server,
      List<SourceLibrary> libsInScope, Map<SourceLibrary, SymbolIndex> indexes) {
    int colon = spec.indexOf(':');
    if (colon >= 0) {
      String modStr = spec.substring(0, colon);
      String defStr = spec.substring(colon + 1);
      if (modStr.isEmpty() || defStr.isEmpty()) {
        System.err.println("[ERROR] empty module or definition path in -ch spec '" + spec + "'");
        return null;
      }
      ModulePath mp = ModulePath.fromString(modStr);
      if (!FileUtils.isCorrectModulePath(mp)) {
        System.err.println("[ERROR] invalid module path '" + modStr + "'");
        return null;
      }
      LongName ln = LongName.fromString(defStr);
      if (!FileUtils.isCorrectDefinitionName(ln)) {
        System.err.println("[ERROR] invalid definition name '" + defStr + "'");
        return null;
      }
      ModuleLocation found = server.findModule(mp, null, true, true);
      if (found == null) {
        System.err.println("[ERROR] Module not found: " + modStr);
        return null;
      }
      ConcreteGroup group = server.getRawGroup(found);
      if (group == null) {
        System.err.println("[ERROR] Module not loaded: " + modStr);
        return null;
      }
      LocatedReferable ref = walkLongName(group, ln);
      if (ref == null) {
        System.err.println("[ERROR] Definition not found: " + spec);
        return null;
      }
      SymbolIndex.Kind kind = kindOf(ref);
      return new ResolvedTarget(ref, found, kind, found.getLibraryName());
    }

    // Bare-name lookup via the symbol index, restricted to CLASS / RECORD.
    List<SymbolIndex.Entry> matches = new ArrayList<>();
    Map<SymbolIndex.Entry, SourceLibrary> libOf = new HashMap<>();
    for (Map.Entry<SourceLibrary, SymbolIndex> e : indexes.entrySet()) {
      for (SymbolIndex.Entry entry : e.getValue().allEntries()) {
        if ((entry.kind() == SymbolIndex.Kind.CLASS || entry.kind() == SymbolIndex.Kind.RECORD)
            && entry.shortName().equals(spec)) {
          matches.add(entry);
          libOf.put(entry, e.getKey());
        }
      }
    }
    if (matches.isEmpty()) {
      System.err.println("[ERROR] No class or record named '" + spec + "' in scope. Use -ss to find candidates.");
      return null;
    }
    if (matches.size() > 1) {
      System.err.println("[ERROR] '" + spec + "' is ambiguous. Use one of:");
      List<String> labels = new ArrayList<>();
      for (SymbolIndex.Entry e : matches) {
        labels.add("  " + libOf.get(e).getLibraryName() + "::" + e.modulePath() + ":" + e.longName());
      }
      Collections.sort(labels);
      for (String l : labels) System.err.println(l);
      return null;
    }
    SymbolIndex.Entry only = matches.getFirst();
    SourceLibrary lib = libOf.get(only);
    ModulePath mp = only.modulePath();
    ModuleLocation moduleLoc = server.findModule(mp, lib.getLibraryName(), true, true);
    if (moduleLoc == null) {
      System.err.println("[ERROR] Could not load module " + mp);
      return null;
    }
    LongName ln = LongName.fromString(only.longName());
    ConcreteGroup group = server.getRawGroup(moduleLoc);
    if (group == null) {
      System.err.println("[ERROR] Module not loaded: " + mp);
      return null;
    }
    LocatedReferable ref = walkLongName(group, ln);
    if (ref == null) {
      System.err.println("[ERROR] Definition not found via index: " + only.longName());
      return null;
    }
    System.out.println("[INFO] Resolved '" + spec + "' -> " + lib.getLibraryName() + "::"
        + mp + ":" + only.longName());
    return new ResolvedTarget(ref, moduleLoc, only.kind(), lib.getLibraryName());
  }

  private static @Nullable LocatedReferable walkLongName(ConcreteGroup group, LongName ln) {
    LocatedReferable result = group.referable();
    List<String> names = ln.toList();
    for (int i = 0; i < names.size(); i++) {
      String segment = names.get(i);
      ConcreteGroup next = findChildGroup(group, segment);
      if (next != null) {
        group = next;
        result = next.referable();
        continue;
      }
      LocatedReferable internal = findInternalReferable(group, segment);
      if (internal != null) {
        return i == names.size() - 1 ? internal : null;
      }
      return null;
    }
    return result;
  }

  private static @Nullable ConcreteGroup findChildGroup(ConcreteGroup group, String name) {
    for (ConcreteStatement stmt : group.statements()) {
      ConcreteGroup sub = stmt.group();
      if (sub != null && sub.referable() != null
          && name.equals(sub.referable().textRepresentation())) {
        return sub;
      }
    }
    for (ConcreteGroup dyn : group.dynamicGroups()) {
      if (dyn.referable() != null && name.equals(dyn.referable().textRepresentation())) {
        return dyn;
      }
    }
    return null;
  }

  private static @Nullable LocatedReferable findInternalReferable(ConcreteGroup group, String name) {
    for (LocatedReferable r : group.getInternalReferables()) {
      if (name.equals(r.textRepresentation())) return r;
    }
    return null;
  }

  private static SymbolIndex.Kind kindOf(LocatedReferable ref) {
    if (!(ref instanceof GlobalReferable g)) return SymbolIndex.Kind.OTHER;
    return switch (g.getKind()) {
      case CLASS -> SymbolIndex.Kind.CLASS;
      case RECORD -> SymbolIndex.Kind.RECORD;
      case DATA -> SymbolIndex.Kind.DATA;
      case FUNCTION -> SymbolIndex.Kind.FUNCTION;
      case COCLAUSE_FUNCTION -> SymbolIndex.Kind.COCLAUSE;
      case INSTANCE -> SymbolIndex.Kind.INSTANCE;
      case CONSTRUCTOR, DEFINED_CONSTRUCTOR -> SymbolIndex.Kind.CONSTRUCTOR;
      case FIELD -> SymbolIndex.Kind.FIELD;
      case LEVEL -> SymbolIndex.Kind.LEVEL;
      case META -> SymbolIndex.Kind.META;
      case OTHER -> SymbolIndex.Kind.OTHER;
    };
  }

  // ---- graph + visitors --------------------------------------------------

  private static final class ClassNode {
    LocatedReferable referable;
    ModuleLocation module;
    boolean isRecord;
    final List<LocatedReferable> directParents = new ArrayList<>();
    final List<LocatedReferable> directChildren = new ArrayList<>();
    final Set<String> directFieldNames = new LinkedHashSet<>();
  }

  private record VisitorContext(ModuleLocation module, DefinitionData data) {}

  private record NewSite(LocatedReferable targetClass, ModuleLocation module,
                         int line, int column, Set<String> implementedFieldNames) {}

  private record InstanceSite(LocatedReferable targetClass, LocatedReferable instanceRef,
                              ModuleLocation module, int line, int column) {}

  private static final class NewSiteCollector extends BaseConcreteExpressionVisitor<Void> {
    final List<NewSite> sites = new ArrayList<>();
    VisitorContext context;

    @Override
    public Concrete.Expression visitNew(Concrete.NewExpression expr, Void params) {
      Concrete.Expression inner = expr.expression;
      LocatedReferable cls = peelClassFromExpr(inner);
      if (cls != null) {
        Set<String> implemented = new LinkedHashSet<>();
        if (inner instanceof Concrete.ClassExtExpression ext) {
          for (Concrete.ClassFieldImpl impl : ext.getStatements()) {
            Referable f = impl.getImplementedField();
            if (f != null) implemented.add(f.textRepresentation());
          }
        }
        SourcePosition pos = positionOf(expr.getData());
        if (pos != null) {
          sites.add(new NewSite(cls, context.module, pos.line, pos.column, implemented));
        }
      }
      return super.visitNew(expr, params);
    }
  }

  private static final class InstanceCollector {
    final List<InstanceSite> sites = new ArrayList<>();

    void add(LocatedReferable targetClass, LocatedReferable instanceRef, ModuleLocation module) {
      SourcePosition pos = positionOf(instanceRef instanceof org.arend.ext.reference.DataContainer dc ? dc.getData() : null);
      if (pos == null) return;
      sites.add(new InstanceSite(targetClass, instanceRef, module, pos.line, pos.column));
    }
  }

  /**
   * Peels off App / ClassExt to find the underlying class ReferenceExpression.
   * Returns null when the expression is not a class reference shape.
   */
  private static @Nullable LocatedReferable peelClassFromExpr(@Nullable Concrete.Expression expr) {
    while (expr != null) {
      if (expr instanceof Concrete.ReferenceExpression refE) {
        return refE.getReferent() instanceof LocatedReferable lr ? lr : null;
      } else if (expr instanceof Concrete.AppExpression app) {
        expr = app.getFunction();
      } else if (expr instanceof Concrete.ClassExtExpression ext) {
        expr = ext.getBaseClassExpression();
      } else if (expr instanceof Concrete.BinOpSequenceExpression seq && seq.getSequence().size() == 1) {
        expr = seq.getSequence().getFirst().getComponent();
      } else {
        return null;
      }
    }
    return null;
  }

  private static @Nullable LocatedReferable peelClassFromType(@Nullable Concrete.Expression type) {
    return peelClassFromExpr(type);
  }

  // ---- printers ----------------------------------------------------------

  interface Printer {
    void print(ResolvedTarget target,
               Map<LocatedReferable, ClassNode> graph,
               Map<LocatedReferable, ModuleLocation> moduleOf,
               NewSiteCollector newCollector,
               InstanceCollector instanceCollector,
               Options options,
               LibraryManager libraryManager);
  }

  private static final class TreePrinter implements Printer {
    @Override
    public void print(ResolvedTarget target,
                      Map<LocatedReferable, ClassNode> graph,
                      Map<LocatedReferable, ModuleLocation> moduleOf,
                      NewSiteCollector newCollector,
                      InstanceCollector instanceCollector,
                      Options options,
                      LibraryManager libraryManager) {
      ClassNode root = graph.get(target.referable);
      String header = "Class " + target.fullLabel() + "  [" + target.kind + "]";
      String headerPos = positionLabel(target.referable, moduleOf.get(target.referable), libraryManager);
      System.out.println(header);
      if (!headerPos.isEmpty()) System.out.println("  " + headerPos);

      if (options.direction != Direction.DOWN) {
        System.out.println();
        System.out.println("Superclasses:");
        if (root == null || root.directParents.isEmpty()) {
          System.out.println("  (none)");
        } else {
          Set<LocatedReferable> seen = new HashSet<>();
          printSuperTree(root, "", true, true, graph, moduleOf, libraryManager, seen, options);
        }
      }

      if (options.direction != Direction.UP) {
        System.out.println();
        System.out.println("Subclasses:");
        if (root == null || root.directChildren.isEmpty()) {
          System.out.println("  (none)");
        } else {
          Set<LocatedReferable> seen = new HashSet<>();
          printSubTree(root, "", true, true, graph, moduleOf, libraryManager, seen, options);
        }
      }

      if (!options.noInstances) printInstances(target, graph, moduleOf, instanceCollector, options, libraryManager);
      if (!options.noNews) printNewSites(target, graph, moduleOf, newCollector, options, libraryManager);
    }

    private void printSuperTree(ClassNode node, String prefix, boolean isRoot, boolean isTail,
                                Map<LocatedReferable, ClassNode> graph,
                                Map<LocatedReferable, ModuleLocation> moduleOf,
                                LibraryManager libraryManager,
                                Set<LocatedReferable> seen,
                                Options options) {
        boolean repeat = !seen.add(node.referable);
        String label = nodeLabel(node, moduleOf, libraryManager, options);
        if (repeat) label += "  …";
        if (isRoot) {
          System.out.println("└── " + label);
        } else {
          System.out.println(prefix + (isTail ? "└── " : "├── ") + label);
        }
        if (repeat) return;
        String childPrefix = isRoot ? "    " : prefix + (isTail ? "    " : "│   ");
        List<LocatedReferable> parents = node.directParents;
        for (int i = 0; i < parents.size(); i++) {
          ClassNode p = graph.get(parents.get(i));
          if (p == null) continue;
          printSuperTree(p, childPrefix, false, i == parents.size() - 1, graph, moduleOf, libraryManager, seen, options);
        }
    }

    private void printSubTree(ClassNode node, String prefix, boolean isRoot, boolean isTail,
                              Map<LocatedReferable, ClassNode> graph,
                              Map<LocatedReferable, ModuleLocation> moduleOf,
                              LibraryManager libraryManager,
                              Set<LocatedReferable> seen,
                              Options options) {
      boolean repeat = !seen.add(node.referable);
      String label = nodeLabel(node, moduleOf, libraryManager, options);
      if (repeat) label += "  …";
      if (isRoot) {
        System.out.println("└── " + label);
      } else {
        System.out.println(prefix + (isTail ? "└── " : "├── ") + label);
      }
      if (repeat) return;
      String childPrefix = isRoot ? "    " : prefix + (isTail ? "    " : "│   ");
      List<LocatedReferable> kids = new ArrayList<>(node.directChildren);
      kids.sort(Comparator.comparing(r -> r.getRefLongName().toString()));
      for (int i = 0; i < kids.size(); i++) {
        ClassNode c = graph.get(kids.get(i));
        if (c == null) continue;
        printSubTree(c, childPrefix, false, i == kids.size() - 1, graph, moduleOf, libraryManager, seen, options);
      }
    }

    private void printInstances(ResolvedTarget target,
                                Map<LocatedReferable, ClassNode> graph,
                                Map<LocatedReferable, ModuleLocation> moduleOf,
                                InstanceCollector collector, Options options,
                                LibraryManager libraryManager) {
      Set<LocatedReferable> closure = subclassClosure(target.referable, graph, options.direction);
      List<InstanceSite> filtered = new ArrayList<>();
      for (InstanceSite s : collector.sites) {
        if (closure.contains(s.targetClass())) filtered.add(s);
      }
      System.out.println();
      System.out.println("Instances (" + filtered.size() + "):");
      if (filtered.isEmpty()) { System.out.println("  (none)"); return; }
      filtered.sort(Comparator
          .comparing((InstanceSite s) -> sortKeyPath(s.module(), libraryManager))
          .thenComparingInt(InstanceSite::line)
          .thenComparingInt(InstanceSite::column));
      int printed = 0;
      for (InstanceSite s : filtered) {
        if (options.limit > 0 && printed >= options.limit) {
          System.out.println("  ... " + (filtered.size() - printed) + " more (raise limit=N)");
          break;
        }
        Path file = sourcePathFor(libraryManager.getLibrary(s.module().getLibraryName()), s.module());
        String pathLabel = file == null
            ? "<" + s.module().getLibraryName() + ":" + s.module().getModulePath() + ">"
            : PathDisplay.shorten(file, libraryManager);
        System.out.println("  " + pathLabel + ":" + s.line() + ":" + s.column()
            + "  " + s.instanceRef().textRepresentation() + " : " + s.targetClass().textRepresentation());
        printed++;
      }
    }

    private void printNewSites(ResolvedTarget target,
                               Map<LocatedReferable, ClassNode> graph,
                               Map<LocatedReferable, ModuleLocation> moduleOf,
                               NewSiteCollector collector, Options options,
                               LibraryManager libraryManager) {
      Set<LocatedReferable> closure = subclassClosure(target.referable, graph, options.direction);
      List<NewSite> filtered = new ArrayList<>();
      for (NewSite s : collector.sites) {
        if (closure.contains(s.targetClass())) filtered.add(s);
      }
      System.out.println();
      System.out.println("\\new sites (" + filtered.size() + "):");
      if (filtered.isEmpty()) { System.out.println("  (none)"); return; }
      filtered.sort(Comparator
          .comparing((NewSite s) -> sortKeyPath(s.module(), libraryManager))
          .thenComparingInt(NewSite::line)
          .thenComparingInt(NewSite::column));
      int printed = 0;
      for (NewSite s : filtered) {
        if (options.limit > 0 && printed >= options.limit) {
          System.out.println("  ... " + (filtered.size() - printed) + " more (raise limit=N)");
          break;
        }
        Path file = sourcePathFor(libraryManager.getLibrary(s.module().getLibraryName()), s.module());
        String pathLabel = file == null
            ? "<" + s.module().getLibraryName() + ":" + s.module().getModulePath() + ">"
            : PathDisplay.shorten(file, libraryManager);
        Set<String> transitive = transitiveFields(s.targetClass(), graph);
        Set<String> missing = new LinkedHashSet<>(transitive);
        missing.removeAll(s.implementedFieldNames());
        String suffix;
        if (s.implementedFieldNames().isEmpty()) {
          suffix = "  missing: " + setLabel(missing);
        } else {
          suffix = "  impl: " + setLabel(s.implementedFieldNames())
              + (missing.isEmpty() ? "" : "  missing: " + setLabel(missing));
        }
        System.out.println("  " + pathLabel + ":" + s.line() + ":" + s.column()
            + "  \\new " + s.targetClass().textRepresentation() + suffix);
        printed++;
      }
    }
  }

  private static final class FlatPrinter implements Printer {
    @Override
    public void print(ResolvedTarget target,
                      Map<LocatedReferable, ClassNode> graph,
                      Map<LocatedReferable, ModuleLocation> moduleOf,
                      NewSiteCollector newCollector,
                      InstanceCollector instanceCollector,
                      Options options,
                      LibraryManager libraryManager) {
      ClassNode root = graph.get(target.referable);
      Path tgtPath = sourcePathFor(libraryManager.getLibrary(target.libraryName), target.module);
      String tgtLoc = (tgtPath == null ? "<" + target.libraryName + ":" + target.module.getModulePath() + ">"
          : PathDisplay.shorten(tgtPath, libraryManager));
      int[] tgtPos = posOf(target.referable);
      String tgtLocFull = tgtPos[0] > 0 ? tgtLoc + ":" + tgtPos[0] + ":" + tgtPos[1] : tgtLoc;
      System.out.println("TARGET\t" + target.fullLabel() + "\t" + target.kind + "\t" + tgtLocFull);

      if (options.direction != Direction.DOWN && root != null) {
        Set<LocatedReferable> visited = new HashSet<>();
        Deque<LocatedReferable> stack = new ArrayDeque<>();
        stack.push(target.referable);
        while (!stack.isEmpty()) {
          LocatedReferable cur = stack.pop();
          if (!visited.add(cur)) continue;
          ClassNode node = graph.get(cur);
          if (node == null) continue;
          for (LocatedReferable parent : node.directParents) {
            System.out.println("EXTENDS\t" + qualifiedLabel(cur, moduleOf, libraryManager)
                + "\t" + qualifiedLabel(parent, moduleOf, libraryManager));
            stack.push(parent);
          }
        }
      }
      if (options.direction != Direction.UP && root != null) {
        Set<LocatedReferable> visited = new HashSet<>();
        Deque<LocatedReferable> stack = new ArrayDeque<>();
        stack.push(target.referable);
        while (!stack.isEmpty()) {
          LocatedReferable cur = stack.pop();
          if (!visited.add(cur)) continue;
          ClassNode node = graph.get(cur);
          if (node == null) continue;
          for (LocatedReferable child : node.directChildren) {
            System.out.println("EXTENDED-BY\t" + qualifiedLabel(cur, moduleOf, libraryManager)
                + "\t" + qualifiedLabel(child, moduleOf, libraryManager));
            stack.push(child);
          }
        }
      }
      if (!options.noInstances) {
        Set<LocatedReferable> closure = subclassClosure(target.referable, graph, options.direction);
        List<InstanceSite> sorted = new ArrayList<>();
        for (InstanceSite s : instanceCollector.sites) {
          if (closure.contains(s.targetClass())) sorted.add(s);
        }
        sorted.sort(Comparator
            .comparing((InstanceSite s) -> sortKeyPath(s.module(), libraryManager))
            .thenComparingInt(InstanceSite::line)
            .thenComparingInt(InstanceSite::column));
        for (InstanceSite s : sorted) {
          Path file = sourcePathFor(libraryManager.getLibrary(s.module().getLibraryName()), s.module());
          String loc = (file == null ? "<" + s.module().getLibraryName() + ":" + s.module().getModulePath() + ">"
              : PathDisplay.shorten(file, libraryManager)) + ":" + s.line() + ":" + s.column();
          System.out.println("INSTANCE\t" + qualifiedLabel(s.targetClass(), moduleOf, libraryManager)
              + "\t" + loc + "\t" + s.instanceRef().textRepresentation());
        }
      }
      if (!options.noNews) {
        Set<LocatedReferable> closure = subclassClosure(target.referable, graph, options.direction);
        List<NewSite> sorted = new ArrayList<>();
        for (NewSite s : newCollector.sites) {
          if (closure.contains(s.targetClass())) sorted.add(s);
        }
        sorted.sort(Comparator
            .comparing((NewSite s) -> sortKeyPath(s.module(), libraryManager))
            .thenComparingInt(NewSite::line)
            .thenComparingInt(NewSite::column));
        for (NewSite s : sorted) {
          Path file = sourcePathFor(libraryManager.getLibrary(s.module().getLibraryName()), s.module());
          String loc = (file == null ? "<" + s.module().getLibraryName() + ":" + s.module().getModulePath() + ">"
              : PathDisplay.shorten(file, libraryManager)) + ":" + s.line() + ":" + s.column();
          Set<String> transitive = transitiveFields(s.targetClass(), graph);
          Set<String> missing = new LinkedHashSet<>(transitive);
          missing.removeAll(s.implementedFieldNames());
          System.out.println("NEW\t" + qualifiedLabel(s.targetClass(), moduleOf, libraryManager)
              + "\t" + loc + "\timpl=" + setLabel(s.implementedFieldNames())
              + "\tmiss=" + setLabel(missing));
        }
      }
    }
  }

  // ---- shared helpers ----------------------------------------------------

  private static Set<LocatedReferable> subclassClosure(LocatedReferable target,
      Map<LocatedReferable, ClassNode> graph, Direction direction) {
    // Closure includes the target itself so direct \new of target is reported.
    // For UP direction we still want target+supers in scope.
    Set<LocatedReferable> out = new LinkedHashSet<>();
    Deque<LocatedReferable> stack = new ArrayDeque<>();
    stack.push(target);
    while (!stack.isEmpty()) {
      LocatedReferable cur = stack.pop();
      if (!out.add(cur)) continue;
      ClassNode node = graph.get(cur);
      if (node == null) continue;
      if (direction != Direction.UP) {
        for (LocatedReferable c : node.directChildren) stack.push(c);
      }
      if (direction == Direction.UP) {
        for (LocatedReferable p : node.directParents) stack.push(p);
      }
    }
    return out;
  }

  private static Set<String> transitiveFields(LocatedReferable cls,
      Map<LocatedReferable, ClassNode> graph) {
    Set<String> out = new LinkedHashSet<>();
    Set<LocatedReferable> seen = new HashSet<>();
    Deque<LocatedReferable> stack = new ArrayDeque<>();
    stack.push(cls);
    while (!stack.isEmpty()) {
      LocatedReferable cur = stack.pop();
      if (!seen.add(cur)) continue;
      ClassNode node = graph.get(cur);
      if (node == null) continue;
      out.addAll(node.directFieldNames);
      for (LocatedReferable p : node.directParents) stack.push(p);
    }
    return out;
  }

  private static String qualifiedLabel(LocatedReferable ref,
      Map<LocatedReferable, ModuleLocation> moduleOf, LibraryManager libraryManager) {
    ModuleLocation moduleLoc = moduleOf.get(ref);
    if (moduleLoc == null) return "?::" + ref.getRefLongName();
    return moduleLoc.getLibraryName() + "::" + moduleLoc.getModulePath() + ":" + ref.getRefLongName();
  }

  private static String nodeLabel(ClassNode node,
      Map<LocatedReferable, ModuleLocation> moduleOf, LibraryManager libraryManager,
      Options options) {
    StringBuilder sb = new StringBuilder();
    sb.append(node.referable.getRefLongName());
    String pos = positionLabel(node.referable, moduleOf.get(node.referable), libraryManager);
    if (!pos.isEmpty()) sb.append("    (").append(pos).append(")");
    if (options.withFields && !node.directFieldNames.isEmpty()) {
      sb.append("  fields: ").append(setLabel(node.directFieldNames));
    }
    return sb.toString();
  }

  private static String positionLabel(LocatedReferable ref, @Nullable ModuleLocation moduleLoc,
      LibraryManager libraryManager) {
    if (moduleLoc == null) return "";
    Path src = sourcePathFor(libraryManager.getLibrary(moduleLoc.getLibraryName()), moduleLoc);
    String pathLabel = src != null ? PathDisplay.shorten(src, libraryManager)
        : moduleLoc.getLibraryName() + ":" + moduleLoc.getModulePath();
    int[] pos = posOf(ref);
    return pos[0] > 0 ? pathLabel + ":" + pos[0] + ":" + pos[1] : pathLabel;
  }

  private static int[] posOf(LocatedReferable ref) {
    Object data = ref instanceof org.arend.ext.reference.DataContainer dc ? dc.getData() : null;
    if (data instanceof SourcePosition sp) return new int[] { sp.line, sp.column };
    return new int[] { 0, 0 };
  }

  private static @Nullable SourcePosition positionOf(@Nullable Object data) {
    return data instanceof SourcePosition sp ? sp : null;
  }

  /**
   * Sort key for a module location: the absolute source path string when
   * available, otherwise a synthetic {@code <lib:module>} label. Both branches
   * compare lexicographically the way users read paths.
   */
  private static String sortKeyPath(ModuleLocation moduleLoc, LibraryManager libraryManager) {
    Path p = sourcePathFor(libraryManager.getLibrary(moduleLoc.getLibraryName()), moduleLoc);
    return p != null ? p.toString()
        : "<" + moduleLoc.getLibraryName() + ":" + moduleLoc.getModulePath() + ">";
  }

  private static @Nullable Path sourcePathFor(@Nullable SourceLibrary lib, ModuleLocation moduleLoc) {
    if (!(lib instanceof FileSourceLibrary fl)) return null;
    Path src = fl.getSourceBasePath();
    if (src == null) return null;
    try {
      return FileUtils.sourceFile(src, moduleLoc.getModulePath()).toAbsolutePath().normalize();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String setLabel(Set<String> set) {
    if (set.isEmpty()) return "{}";
    return String.join(",", new TreeSet<>(set));
  }

  private static List<SourceLibrary> librariesInScope(
      List<SourceLibrary> requested, LibraryManager manager, Options opts) {
    List<SourceLibrary> all = new ArrayList<>();
    for (String name : manager.getLibraries()) {
      SourceLibrary lib = manager.getLibrary(name);
      if (lib != null) all.add(lib);
    }
    if (opts.onlyLibraries == null) return all;
    Set<String> allow = new HashSet<>();
    for (String s : opts.onlyLibraries) {
      if ("self".equalsIgnoreCase(s)) {
        for (SourceLibrary l : requested) allow.add(l.getLibraryName());
      } else {
        allow.add(s);
      }
    }
    List<SourceLibrary> filtered = new ArrayList<>();
    for (SourceLibrary lib : all) if (allow.contains(lib.getLibraryName())) filtered.add(lib);
    return filtered;
  }
}
