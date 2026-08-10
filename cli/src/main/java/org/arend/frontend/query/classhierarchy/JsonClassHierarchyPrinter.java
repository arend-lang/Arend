package org.arend.frontend.query.classhierarchy;

import org.arend.ext.module.ModuleLocation;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.query.*;
import org.arend.frontend.query.classhierarchy.ClassHierarchy.ClassNode;
import org.arend.frontend.query.classhierarchy.ClassHierarchy.InstanceSite;
import org.arend.frontend.query.classhierarchy.ClassHierarchy.NewSite;
import org.arend.naming.reference.LocatedReferable;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.*;

import static org.arend.frontend.query.classhierarchy.ClassHierarchyTool.*;

/** Renders the class hierarchy as a single JSON object (the {@code --json} output). */
final class JsonClassHierarchyPrinter implements ClassHierarchyPrinter {
  /**
   * The JSON shape (all keys always present): {@code target}, then {@code superclasses}/
   * {@code subclasses} as arrays of nodes (recursing through parents / children
   * respectively; {@code []} when {@code up}/{@code down} drops that direction),
   * {@code instances} and {@code newSites} (truncated to {@code limit}; {@code []} when
   * {@code no-instances}/{@code no-news} suppresses them), and {@code counts}
   * ({@code {"instances","newSites"}}; the full totals, or 0 for a suppressed section).
   * A node is {@code {"name","location"[,"record"][,"fields"],"children":[…]}}; a diamond
   * repeat carries {@code "repeat":true} and no children.
   */
  @Override
  public void print(PrintStream out, ResolvedTarget target, ClassHierarchy hierarchy, Options options, LibraryManager lm) {
    boolean showLibrary = target.showLibrary();
    ClassNode root = hierarchy.node(target.referable());
    List<String> parts = new ArrayList<>();

    int[] targetPositions = SourcePositionUtils.lineColumn(target.referable());
    parts.add("\"target\": {\"name\":" + JsonUtils.str(target.fullLabel())
        + ",\"kind\":" + JsonUtils.str(target.kind().name())
        + ",\"location\":" + jsonLocation(target.module(), targetPositions[0], targetPositions[1], lm) + "}");

    // The five section keys are ALWAYS emitted (empty array / zero count when a flag
    // suppresses that section) so the object shape is fixed regardless of up/down/
    // no-instances/no-news -- a consumer can rely on every documented key being present.
    if (options.direction != Direction.DOWN) {
      Set<LocatedReferable> seen = new HashSet<>();
      seen.add(target.referable());
      List<String> trees = new ArrayList<>();
      if (root != null) {
        for (LocatedReferable p : root.directParents) {
          if (hierarchy.node(p) != null) trees.add(buildTree(p, true, hierarchy, seen, options, lm, showLibrary));
        }
      }
      parts.add("\"superclasses\": " + jsonArray(trees));
    } else {
      parts.add("\"superclasses\": []");
    }
    if (options.direction != Direction.UP) {
      Set<LocatedReferable> seen = new HashSet<>();
      seen.add(target.referable());
      List<LocatedReferable> kids = root == null ? List.of() : ClassHierarchy.sortedByName(root.directChildren);
      List<String> trees = new ArrayList<>();
      for (LocatedReferable c : kids) {
        if (hierarchy.node(c) != null) trees.add(buildTree(c, false, hierarchy, seen, options, lm, showLibrary));
      }
      parts.add("\"subclasses\": " + jsonArray(trees));
    } else {
      parts.add("\"subclasses\": []");
    }

    int instanceTotal = 0, newTotal = 0;
    if (!options.noInstances) {
      List<InstanceSite> instanceSites = sortedSites(hierarchy, hierarchy.instanceSites, target.referable(), options.direction, lm);
      List<String> rows = new ArrayList<>();
      for (InstanceSite s : limited(instanceSites, options.limit)) {
        rows.add("{\"instance\":" + JsonUtils.str(s.instanceRef().textRepresentation())
            + ",\"class\":" + JsonUtils.str(qualifiedLabel(s.targetClass(), hierarchy, showLibrary))
            + ",\"location\":" + jsonLocation(s.module(), s.line(), s.column(), lm) + "}");
      }
      parts.add("\"instances\": " + jsonArray(rows));
      instanceTotal = instanceSites.size();
    } else {
      parts.add("\"instances\": []");
    }
    if (!options.noNews) {
      List<NewSite> news = sortedSites(hierarchy, hierarchy.newSites, target.referable(), options.direction, lm);
      List<String> rows = new ArrayList<>();
      for (NewSite s : limited(news, options.limit)) {
        Set<String> missing = hierarchy.missingFields(s);
        rows.add("{\"class\":" + JsonUtils.str(qualifiedLabel(s.targetClass(), hierarchy, showLibrary))
            + ",\"location\":" + jsonLocation(s.module(), s.line(), s.column(), lm)
            + ",\"impl\":" + JsonUtils.strArray(new TreeSet<>(s.implementedFieldNames()))
            + ",\"missing\":" + JsonUtils.strArray(new TreeSet<>(missing)) + "}");
      }
      parts.add("\"newSites\": " + jsonArray(rows));
      newTotal = news.size();
    } else {
      parts.add("\"newSites\": []");
    }
    parts.add("\"counts\": {\"instances\":" + instanceTotal + ",\"newSites\":" + newTotal + "}");

    out.println("{\n  " + String.join(",\n  ", parts) + "\n}");
  }

  private static <T> List<T> limited(List<T> list, int limit) {
    return (limit > 0 && list.size() > limit) ? list.subList(0, limit) : list;
  }

  private static String buildTree(LocatedReferable ref, boolean superDir, ClassHierarchy hierarchy,
      Set<LocatedReferable> seen, Options options, LibraryManager lm, boolean showLibrary) {
    ClassNode node = hierarchy.node(ref);
    boolean repeat = !seen.add(ref);
    StringBuilder sb = new StringBuilder("{");
    sb.append("\"name\":").append(JsonUtils.str(qualifiedLabel(ref, hierarchy, showLibrary)));
    int[] pos = SourcePositionUtils.lineColumn(ref);
    sb.append(",\"location\":").append(jsonLocation(hierarchy.moduleOf(ref), pos[0], pos[1], lm));
    if (node != null && node.isRecord) sb.append(",\"record\":true");
    if (options.withFields && node != null && !node.directFieldNames.isEmpty()) {
      sb.append(",\"fields\":").append(JsonUtils.strArray(new TreeSet<>(node.directFieldNames)));
    }
    if (repeat) return sb.append(",\"repeat\":true,\"children\":[]}").toString();
    List<LocatedReferable> next;
    if (superDir) {
      next = node == null ? List.of() : node.directParents;
    } else {
      next = node == null ? List.of() : ClassHierarchy.sortedByName(node.directChildren);
    }
    sb.append(",\"children\":[");
    boolean first = true;
    for (LocatedReferable child : next) {
      if (hierarchy.node(child) == null) continue;
      if (!first) sb.append(',');
      sb.append(buildTree(child, superDir, hierarchy, seen, options, lm, showLibrary));
      first = false;
    }
    return sb.append("]}").toString();
  }

  private static String jsonLocation(@Nullable ModuleLocation moduleLoc, int line, int col, LibraryManager lm) {
    return JsonUtils.location(moduleLoc == null ? null : PathDisplayUtils.label(moduleLoc, lm), line, col);
  }

  /** Wraps pre-rendered object strings, one per line, at the top-level array indent. */
  private static String jsonArray(List<String> objs) {
    if (objs.isEmpty()) return "[]";
    return "[\n    " + String.join(",\n    ", objs) + "\n  ]";
  }
}
