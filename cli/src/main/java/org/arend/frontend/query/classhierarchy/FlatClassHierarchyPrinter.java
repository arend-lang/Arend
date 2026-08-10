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

/** Renders the class hierarchy as one tagged, tab-separated relation per line ({@code format=flat}), for grep / agentic loops. */
final class FlatClassHierarchyPrinter implements ClassHierarchyPrinter {
  @Override
  public void print(PrintStream out, ResolvedTarget target, ClassHierarchy hierarchy, Options options, LibraryManager libraryManager) {
    ClassNode root = hierarchy.node(target.referable());
    String tgtLocFull = positionLabel(target.referable(), target.module(), libraryManager);
    out.println("TARGET\t" + target.fullLabel() + "\t" + target.kind() + "\t" + tgtLocFull);

    if (options.direction != Direction.DOWN && root != null) {
      flatRelations(out, "EXTENDS", target.referable(), hierarchy, n -> n.directParents, target.showLibrary());
    }
    if (options.direction != Direction.UP && root != null) {
      flatRelations(out, "EXTENDED-BY", target.referable(), hierarchy, n -> n.directChildren, target.showLibrary());
    }
    if (!options.noInstances) {
      for (InstanceSite s : sortedSites(hierarchy, hierarchy.instanceSites, target.referable(), options.direction, libraryManager)) {
        out.println("INSTANCE\t" + qualifiedLabel(s.targetClass(), hierarchy, target.showLibrary())
            + "\t" + siteLocation(s, libraryManager) + "\t" + s.instanceRef().textRepresentation());
      }
    }
    if (!options.noNews) {
      for (NewSite s : sortedSites(hierarchy, hierarchy.newSites, target.referable(), options.direction, libraryManager)) {
        Set<String> missing = hierarchy.missingFields(s);
        out.println("NEW\t" + qualifiedLabel(s.targetClass(), hierarchy, target.showLibrary())
            + "\t" + siteLocation(s, libraryManager) + "\timpl=" + setLabel(s.implementedFieldNames())
            + "\tmiss=" + setLabel(missing));
      }
    }
  }

  /** Emits one {@code <tag>\t<from>\t<to>} line per edge reachable from {@code root} in {@code nextOf}'s direction. */
  private static void flatRelations(PrintStream out, String tag, LocatedReferable root, ClassHierarchy hierarchy,
                                    Function<ClassNode, List<LocatedReferable>> nextOf, boolean showLibrary) {
    Set<LocatedReferable> visited = new HashSet<>();
    Deque<LocatedReferable> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
      LocatedReferable cur = stack.pop();
      if (!visited.add(cur)) continue;
      ClassNode node = hierarchy.node(cur);
      if (node == null) continue;
      for (LocatedReferable rel : nextOf.apply(node)) {
        out.println(tag + "\t" + qualifiedLabel(cur, hierarchy, showLibrary)
            + "\t" + qualifiedLabel(rel, hierarchy, showLibrary));
        stack.push(rel);
      }
    }
  }
}
