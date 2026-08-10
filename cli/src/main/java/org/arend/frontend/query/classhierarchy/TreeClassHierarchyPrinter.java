package org.arend.frontend.query.classhierarchy;

import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.query.*;
import org.arend.frontend.query.classhierarchy.ClassHierarchy.ClassNode;
import org.arend.frontend.query.classhierarchy.ClassHierarchy.InstanceSite;
import org.arend.frontend.query.classhierarchy.ClassHierarchy.NewSite;
import org.arend.naming.reference.LocatedReferable;

import java.io.PrintStream;
import java.util.*;
import java.util.function.Function;

import static org.arend.frontend.query.classhierarchy.ClassHierarchyTool.*;

/** Renders the class hierarchy as an indented pseudographic tree (the default {@code -ch} output). */
final class TreeClassHierarchyPrinter implements ClassHierarchyPrinter {
  private PrintStream out;

  @Override
  public void print(PrintStream out, ResolvedTarget target, ClassHierarchy hierarchy, Options options, LibraryManager libraryManager) {
    this.out = out;
    ClassNode root = hierarchy.node(target.referable());
    out.println("Class " + target.fullLabel() + "  [" + target.kind() + "]");
    String headerPos = positionLabel(target.referable(), hierarchy.moduleOf(target.referable()), libraryManager);
    if (!headerPos.isEmpty()) out.println("  " + headerPos);

    if (options.direction != Direction.DOWN) {
      out.println();
      out.println("Superclasses:");
      if (root == null || root.directParents.isEmpty()) {
        out.println("  (none)");
      } else {
        printTree(root, "", true, true, hierarchy, libraryManager, new HashSet<>(), options,
            n -> n.directParents);
      }
    }

    if (options.direction != Direction.UP) {
      out.println();
      out.println("Subclasses:");
      if (root == null || root.directChildren.isEmpty()) {
        out.println("  (none)");
      } else {
        printTree(root, "", true, true, hierarchy, libraryManager, new HashSet<>(), options,
            n -> ClassHierarchy.sortedByName(n.directChildren));
      }
    }

    if (!options.noInstances) printInstances(target, hierarchy, options, libraryManager);
    if (!options.noNews) printNewSites(target, hierarchy, options, libraryManager);
  }

  /** Prints one subtree; {@code nextOf} chooses the traversal direction (parents vs. children). */
  private void printTree(ClassNode node, String prefix, boolean isRoot, boolean isTail,
                         ClassHierarchy hierarchy,
                         LibraryManager libraryManager,
                         Set<LocatedReferable> seen,
                         Options options,
                         Function<ClassNode, List<LocatedReferable>> nextOf) {
    boolean repeat = !seen.add(node.referable);
    String label = nodeLabel(node, hierarchy, libraryManager, options);
    if (repeat) label += "  …";
    if (isRoot) {
      out.println("└── " + label);
    } else {
      out.println(prefix + (isTail ? "└── " : "├── ") + label);
    }
    if (repeat) return;
    String childPrefix = isRoot ? "    " : prefix + (isTail ? "    " : "│   ");
    List<LocatedReferable> next = nextOf.apply(node);
    for (int i = 0; i < next.size(); i++) {
      ClassNode c = hierarchy.node(next.get(i));
      if (c == null) continue;
      printTree(c, childPrefix, false, i == next.size() - 1, hierarchy, libraryManager, seen, options, nextOf);
    }
  }

  private void printInstances(ResolvedTarget target, ClassHierarchy hierarchy,
                              Options options, LibraryManager libraryManager) {
    List<InstanceSite> filtered = sortedSites(hierarchy, hierarchy.instanceSites, target.referable(), options.direction, libraryManager);
    out.println();
    out.println("Instances (" + filtered.size() + "):");
    if (filtered.isEmpty()) { out.println("  (none)"); return; }
    int printed = 0;
    for (InstanceSite s : filtered) {
      if (limitReached(out, printed, filtered.size(), options.limit)) break;
      out.println("  " + siteLocation(s, libraryManager)
          + "  " + s.instanceRef().textRepresentation() + " : " + s.targetClass().textRepresentation());
      printed++;
    }
  }

  private void printNewSites(ResolvedTarget target, ClassHierarchy hierarchy,
                             Options options, LibraryManager libraryManager) {
    List<NewSite> filtered = sortedSites(hierarchy, hierarchy.newSites, target.referable(), options.direction, libraryManager);
    out.println();
    out.println("\\new sites (" + filtered.size() + "):");
    if (filtered.isEmpty()) { out.println("  (none)"); return; }
    int printed = 0;
    for (NewSite s : filtered) {
      if (limitReached(out, printed, filtered.size(), options.limit)) break;
      Set<String> missing = hierarchy.missingFields(s);
      String suffix;
      if (s.implementedFieldNames().isEmpty()) {
        suffix = "  missing: " + setLabel(missing);
      } else {
        suffix = "  impl: " + setLabel(s.implementedFieldNames())
            + (missing.isEmpty() ? "" : "  missing: " + setLabel(missing));
      }
      out.println("  " + siteLocation(s, libraryManager)
          + "  \\new " + s.targetClass().textRepresentation() + suffix);
      printed++;
    }
  }
}
