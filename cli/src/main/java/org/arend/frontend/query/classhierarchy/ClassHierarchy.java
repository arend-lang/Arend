package org.arend.frontend.query.classhierarchy;

import org.arend.error.SourcePosition;
import org.arend.ext.concrete.definition.FunctionKind;
import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.query.SourcePositionUtils;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.term.concrete.BaseConcreteExpressionVisitor;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * The class/record inheritance graph over a set of modules, together with the
 * {@code \new} and {@code \instance} sites found in them. Built once by
 * {@link #build} and queried by the {@code -ch} printers.
 */
final class ClassHierarchy {
  final Map<LocatedReferable, ClassNode> graph;
  private final Map<LocatedReferable, ModuleLocation> moduleOf;
  final List<NewSite> newSites;
  final List<InstanceSite> instanceSites;

  private ClassHierarchy(Map<LocatedReferable, ClassNode> graph, Map<LocatedReferable, ModuleLocation> moduleOf,
                         List<NewSite> newSites, List<InstanceSite> instanceSites) {
    this.graph = graph;
    this.moduleOf = moduleOf;
    this.newSites = newSites;
    this.instanceSites = instanceSites;
  }

  /**
   * Resolves every module in {@code sources} and walks the resulting definitions to
   * build the parent/child graph plus the {@code \new} / {@code \instance} site lists.
   */
  static ClassHierarchy build(List<ModuleLocation> sources, ArendServer server) {
    if (!sources.isEmpty()) {
      server.getCheckerFor(sources).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
    }
    Map<LocatedReferable, ClassNode> graph = new HashMap<>();
    Map<LocatedReferable, ModuleLocation> moduleOf = new HashMap<>();
    NewSiteCollector newCollector = new NewSiteCollector();
    List<InstanceSite> instanceSites = new ArrayList<>();

    for (ModuleLocation moduleLoc : sources) {
      for (DefinitionData data : server.getResolvedDefinitions(moduleLoc)) {
        Concrete.ResolvableDefinition def = data.definition();
        if (def == null) continue;
        if (def instanceof Concrete.ClassDefinition cdef) {
          LocatedReferable ref = cdef.getData();
          ClassNode node = graph.computeIfAbsent(ref, k -> new ClassNode());
          node.referable = ref;
          node.isRecord = cdef.isRecord();
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
          newCollector.module = moduleLoc;
          def.accept(newCollector, null);
        } catch (RuntimeException ignored) {}

        // Detect \instance declarations.
        if (def instanceof Concrete.FunctionDefinition functionDefinition
            && functionDefinition.getKind() == FunctionKind.INSTANCE) {
          LocatedReferable cls = peelClassFromExpr(functionDefinition.getResultType());
          LocatedReferable instanceRef = functionDefinition.getData();
          SourcePosition pos = SourcePositionUtils.of(instanceRef);
          if (cls != null && pos != null) {
            instanceSites.add(new InstanceSite(cls, instanceRef, moduleLoc, pos.line, pos.column));
          }
        }
      }
    }
    return new ClassHierarchy(graph, moduleOf, newCollector.sites, instanceSites);
  }

  /** The node for {@code ref}, or null if it isn't a class/record in scope. */
  @Nullable ClassNode node(LocatedReferable ref) {
    return graph.get(ref);
  }

  /** The module {@code ref} was declared in, or null if unknown. */
  @Nullable ModuleLocation moduleOf(LocatedReferable ref) {
    return moduleOf.get(ref);
  }

  /**
   * The target's sub/superclass closure (per direction), including the target itself
   * so a {@code \new} / {@code \instance} of the target is reported.
   */
  Set<LocatedReferable> closure(LocatedReferable target, ClassHierarchyTool.Direction direction) {
    Set<LocatedReferable> out = new LinkedHashSet<>();
    Deque<LocatedReferable> stack = new ArrayDeque<>();
    stack.push(target);
    while (!stack.isEmpty()) {
      LocatedReferable cur = stack.pop();
      if (!out.add(cur)) continue;
      ClassNode node = graph.get(cur);
      if (node == null) continue;
      if (direction == ClassHierarchyTool.Direction.UP) {
        for (LocatedReferable p : node.directParents) stack.push(p);
      } else {
        for (LocatedReferable c : node.directChildren) stack.push(c);
      }
    }
    return out;
  }

  /** The site records whose target class lies within the target's {@link #closure}. */
  <T> List<T> sitesInClosure(List<T> sites, Function<T, LocatedReferable> targetClassOf,
                             LocatedReferable target, ClassHierarchyTool.Direction direction) {
    Set<LocatedReferable> cl = closure(target, direction);
    List<T> out = new ArrayList<>();
    for (T s : sites) {
      if (cl.contains(targetClassOf.apply(s))) out.add(s);
    }
    return out;
  }

  /** Every field name declared by {@code cls} or any of its transitive superclasses. */
  Set<String> transitiveFields(LocatedReferable cls) {
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

  /** The fields required across {@code site}'s class hierarchy but not implemented at the site. */
  Set<String> missingFields(NewSite site) {
    Set<String> missing = new LinkedHashSet<>(transitiveFields(site.targetClass()));
    missing.removeAll(site.implementedFieldNames());
    return missing;
  }

  /** A copy of {@code refs} ordered by long name — the canonical child ordering. */
  static List<LocatedReferable> sortedByName(List<LocatedReferable> refs) {
    List<LocatedReferable> out = new ArrayList<>(refs);
    out.sort(Comparator.comparing(r -> r.getRefLongName().toString()));
    return out;
  }

  // ---- graph nodes + site records ----------------------------------------

  static final class ClassNode {
    LocatedReferable referable;
    boolean isRecord;
    final List<LocatedReferable> directParents = new ArrayList<>();
    final List<LocatedReferable> directChildren = new ArrayList<>();
    final Set<String> directFieldNames = new LinkedHashSet<>();
  }

  /** Common shape of a {@code \new} / {@code \instance} site: its target class and source position. */
  interface Site {
    LocatedReferable targetClass();
    ModuleLocation module();
    int line();
    int column();
  }

  record NewSite(LocatedReferable targetClass, ModuleLocation module,
                 int line, int column, Set<String> implementedFieldNames) implements Site {}

  record InstanceSite(LocatedReferable targetClass, LocatedReferable instanceRef,
                      ModuleLocation module, int line, int column) implements Site {}

  // ---- collectors (build only) -------------------------------------------

  private static final class NewSiteCollector extends BaseConcreteExpressionVisitor<Void> {
    final List<NewSite> sites = new ArrayList<>();
    ModuleLocation module;

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
        SourcePosition pos = SourcePositionUtils.of(expr.getData());
        if (pos != null) {
          sites.add(new NewSite(cls, module, pos.line, pos.column, implemented));
        }
      }
      return super.visitNew(expr, params);
    }
  }

  /** The class a {@code \new} / {@code \instance} expression constructs, or null if it isn't a class reference. */
  private static @Nullable LocatedReferable peelClassFromExpr(@Nullable Concrete.Expression expr) {
    while (expr != null) {
      switch (expr) {
        case Concrete.ReferenceExpression refE -> {
          return refE.getReferent() instanceof LocatedReferable lr ? lr : null;
        }
        case Concrete.AppExpression app -> expr = app.getFunction();
        case Concrete.ClassExtExpression ext -> expr = ext.getBaseClassExpression();
        case Concrete.BinOpSequenceExpression seq when seq.getSequence().size() == 1 ->
            expr = seq.getSequence().getFirst().getComponent();
        default -> {
          return null;
        }
      }
    }
    return null;
  }
}
