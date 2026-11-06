package org.arend.frontend.query.guide;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.query.JsonUtils;
import org.arend.frontend.query.guide.GuideChecker.Candidate;
import org.arend.frontend.query.guide.GuideChecker.Finding;
import org.arend.frontend.query.guide.GuideChecker.Problem;
import org.arend.frontend.query.guide.ModuleDefinitions.Def;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/**
 * Turns a check into work: one packet per page, holding every task on that page and what is
 * needed to do it without looking anything up -- the entry, the statement as it was and as it is,
 * where the definition is, what a vanished name may now be. A page is the unit of work because it
 * is the unit of everything else: one module, one markdown file, one fingerprint file, so packets
 * can be worked on in parallel and a page stamped as a whole when its packet is done.
 *
 * <p>Tasks:
 * <ul>
 *   <li>{@code VERIFY} -- the entry may be out of date (its statement or body changed, something it
 *       mentions changed, or it was moved here and never confirmed): keep it if it is still true,
 *       rewrite it if not;</li>
 *   <li>{@code VERIFY-OVERVIEW} -- the same for the page's prose;</li>
 *   <li>{@code CONFIRM-RENAME} -- the name is gone, and the candidates are what it may now be:
 *       rename the entry, move it, or delete it;</li>
 *   <li>{@code FIX-NAME} -- the name is ambiguous, or gone where {@code -gc fix} could not remove it;</li>
 *   <li>{@code WRITE} -- a definition the page does not describe yet;</li>
 *   <li>{@code FIX-PAGE} -- the page's module is gone and {@code -gc fix} found nowhere to put it.</li>
 * </ul>
 */
final class GuideTasks {
  private GuideTasks() {}

  /** One page's work. {@code newPage}: the page does not exist yet. */
  record Packet(String page, String module, @Nullable Path source, boolean newPage, List<Task> tasks, List<String> style) {
    Map<String, Integer> counts() {
      Map<String, Integer> c = new TreeMap<>();
      for (Task t : tasks) c.merge(t.action(), 1, Integer::sum);
      return c;
    }
  }

  /** One task. Fields that do not apply are null or empty. */
  record Task(String action, List<String> why, String name, @Nullable String kind, int line, int at,
              @Nullable String entry, @Nullable String was, @Nullable String now, String detail,
              List<Candidate> candidates, List<Candidate> mentionsChanged) {}

  /**
   * @param report a check made with undocumented definitions listed
   * @param source the source file of a module, when there is one
   */
  static List<Packet> build(GuideChecker checker, GuideChecker.Report report, Function<ModulePath, Path> source) {
    Map<String, GuidePage> pages = new HashMap<>();
    for (GuidePage p : checker.pages()) pages.put(p.relativePath, p);

    // Findings by page, in report order; NO-PAGE findings make packets of their own.
    Map<String, List<Finding>> byPage = new LinkedHashMap<>();
    List<Finding> noPage = new ArrayList<>();
    for (Finding f : report.findings()) {
      if (f.problem() == Problem.NO_PAGE) noPage.add(f);
      else if (f.page() != null) byPage.computeIfAbsent(f.page(), k -> new ArrayList<>()).add(f);
    }

    List<Packet> packets = new ArrayList<>();
    for (Map.Entry<String, List<Finding>> e : byPage.entrySet()) {
      GuidePage page = pages.get(e.getKey());
      // One VERIFY per entry name, however many reasons there are to look at it.
      Map<String, Task> verify = new LinkedHashMap<>();
      List<Task> tasks = new ArrayList<>();
      for (Finding f : e.getValue()) {
        switch (f.problem()) {
          case SIGNATURE, DEFINITION, REVIEW, UNSTAMPED -> {
            boolean overview = f.name().equals(FingerprintFile.OVERVIEW);
            String key = f.name();
            Task old = verify.get(key);
            List<String> why = new ArrayList<>(old == null ? List.of() : old.why());
            why.add(f.problem().label());
            Task t = new Task(overview ? "VERIFY-OVERVIEW" : "VERIFY", why, f.name(), f.kind(), f.line(), f.defLine(),
                f.entry(), f.previous() != null ? f.previous() : old == null ? null : old.was(), f.signature(),
                old == null ? f.detail() : old.detail() + "; " + f.detail(), List.of(),
                f.changedMentions().isEmpty() && old != null ? old.mentionsChanged() : f.changedMentions());
            verify.put(key, t);
          }
          case GONE -> tasks.add(new Task(f.candidates().isEmpty() ? "FIX-NAME" : "CONFIRM-RENAME", List.of(f.problem().label()),
              f.name(), f.kind(), f.line(), 0, f.entry(), f.previous(), null, f.detail(), f.candidates(), List.of()));
          case AMBIGUOUS -> tasks.add(new Task("FIX-NAME", List.of(f.problem().label()), f.name(), null, f.line(), 0,
              f.entry(), null, null, f.detail(), f.candidates(), List.of()));
          case UNDOCUMENTED -> tasks.add(new Task("WRITE", List.of(f.problem().label()), f.name(), f.kind(), 0, f.defLine(),
              null, null, f.signature(), f.detail(), List.of(), List.of()));
          case PAGE_GONE -> {
            // The module is gone and -gc fix could not settle these entries: hand over each one
            // with what its names may now be.
            if (page == null) break;
            Map<String, FingerprintFile.Row> rows = readRows(checker, page);
            for (GuidePage.Entry entry : page.entries) {
              List<Candidate> cands = new ArrayList<>();
              FingerprintFile.Row row = null;
              for (String name : entry.names()) {
                GuideChecker.Trace t = checker.trace(page.modulePath, name, rows);
                if (row == null) row = t.row();
                for (GuideChecker.Located l : t.where()) cands.add(l.candidate());
              }
              tasks.add(new Task("FIX-PAGE", List.of(f.problem().label()), String.join(", ", entry.names()),
                  row == null ? null : row.kind(), entry.line(), 0, entry.text(), row == null ? null : row.signature(), null,
                  f.detail() + "; move this entry to the page of the module its definition is in now, or delete it",
                  cands, List.of()));
            }
          }
          default -> {}
        }
      }
      List<Task> all = new ArrayList<>(verify.values());
      all.addAll(tasks);
      if (all.isEmpty()) continue;
      packets.add(new Packet(e.getKey(), page == null ? f0(e.getValue()).module() : page.modulePath.toString(),
          page == null ? null : source.apply(page.modulePath), false, all, style(page)));
    }

    for (Finding f : noPage) {
      ModulePath mp = ModulePath.fromString(f.module());
      ModuleDefinitions md = checker.modules().get(mp);
      if (md == null) continue;
      List<Task> tasks = new ArrayList<>();
      for (Def def : md.defs) {
        if (!checker.worthDocumenting(def)) continue;
        tasks.add(new Task("WRITE", List.of(Problem.NO_PAGE.label()), def.longName(), def.kindLabel(), 0, def.line(),
            null, null, def.signature(), "", List.of(), List.of()));
      }
      packets.add(new Packet(GuidePage.relativePathOf(mp), mp.toString(), source.apply(mp), true, tasks, List.of()));
    }
    return packets;
  }

  private static Map<String, FingerprintFile.Row> readRows(GuideChecker checker, GuidePage page) {
    try {
      return FingerprintFile.read(FingerprintFile.pathFor(checker.guideRoot(), page));
    } catch (IOException e) {
      return Map.of();
    }
  }

  private static Finding f0(List<Finding> findings) {
    return findings.getFirst();
  }

  /** A few entries of the page as written, so that new ones can match them. */
  private static List<String> style(@Nullable GuidePage page) {
    if (page == null) return List.of();
    List<String> out = new ArrayList<>();
    for (GuidePage.Entry e : page.entries) {
      if (out.size() == 3) break;
      out.add(String.join("\n", page.lines.subList(e.line() - 1, e.endLine())));
    }
    return out;
  }

  // ---- JSON -----------------------------------------------------------------

  static String json(Packet p, Path guideRoot) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"page\":").append(JsonUtils.str(p.page()))
        .append(",\"file\":").append(JsonUtils.str(guideRoot.resolve(p.page()).toString()))
        .append(",\"module\":").append(JsonUtils.str(p.module()));
    if (p.source() != null) sb.append(",\"source\":").append(JsonUtils.str(p.source().toString()));
    sb.append(",\"newPage\":").append(p.newPage()).append(",\"counts\":{");
    boolean first = true;
    for (Map.Entry<String, Integer> c : p.counts().entrySet()) {
      if (!first) sb.append(',');
      sb.append(JsonUtils.str(c.getKey())).append(':').append(c.getValue());
      first = false;
    }
    sb.append("},\"tasks\":[");
    first = true;
    for (Task t : p.tasks()) {
      if (!first) sb.append(',');
      first = false;
      sb.append("{\"do\":").append(JsonUtils.str(t.action()))
          .append(",\"why\":").append(JsonUtils.strArray(t.why()))
          .append(",\"name\":").append(JsonUtils.str(t.name()));
      if (t.kind() != null) sb.append(",\"kind\":").append(JsonUtils.str(t.kind()));
      if (t.line() > 0) sb.append(",\"line\":").append(t.line());
      if (t.at() > 0) sb.append(",\"at\":").append(t.at());
      if (t.entry() != null) sb.append(",\"entry\":").append(JsonUtils.str(t.entry()));
      if (t.was() != null) sb.append(",\"was\":").append(JsonUtils.str(t.was()));
      if (t.now() != null && !t.now().isEmpty()) sb.append(",\"now\":").append(JsonUtils.str(t.now()));
      if (!t.detail().isEmpty()) sb.append(",\"detail\":").append(JsonUtils.str(t.detail()));
      if (!t.candidates().isEmpty()) sb.append(",\"candidates\":").append(candidates(t.candidates()));
      if (!t.mentionsChanged().isEmpty()) sb.append(",\"mentionsChanged\":").append(candidates(t.mentionsChanged()));
      sb.append('}');
    }
    sb.append("],\"style\":").append(JsonUtils.strArray(p.style())).append('}');
    return sb.toString();
  }

  private static String candidates(List<Candidate> cs) {
    StringBuilder sb = new StringBuilder("[");
    boolean first = true;
    for (Candidate c : cs) {
      if (!first) sb.append(',');
      first = false;
      sb.append("{\"module\":").append(JsonUtils.str(c.module())).append(",\"name\":").append(JsonUtils.str(c.name()));
      if (c.signature() != null) sb.append(",\"signature\":").append(JsonUtils.str(c.signature()));
      sb.append('}');
    }
    return sb.append(']').toString();
  }

  /**
   * Writes each packet to {@code <dir>/<page path>.json} and an {@code index.json} listing them
   * with their counts. The packets an earlier run listed in its index are removed first -- only
   * those, whatever else the directory holds -- so the directory describes the work that is left.
   */
  static void writeAll(List<Packet> packets, Path guideRoot, Path dir) throws IOException {
    Path oldIndex = dir.resolve("index.json");
    if (Files.isRegularFile(oldIndex)) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"packet\":\"([^\"]+)\"")
          .matcher(Files.readString(oldIndex, StandardCharsets.UTF_8));
      while (m.find()) {
        Path old = dir.resolve(m.group(1)).normalize();
        if (old.startsWith(dir.normalize())) Files.deleteIfExists(old);
      }
    }
    StringBuilder index = new StringBuilder("{\"guide\":").append(JsonUtils.str(guideRoot.toString())).append(",\"packets\":[");
    boolean first = true;
    for (Packet p : packets) {
      String rel = p.page().substring(0, p.page().length() - ".md".length()) + ".json";
      Path file = dir.resolve(rel);
      Files.createDirectories(file.getParent());
      Files.writeString(file, json(p, guideRoot) + "\n", StandardCharsets.UTF_8);
      if (!first) index.append(',');
      first = false;
      index.append("{\"page\":").append(JsonUtils.str(p.page())).append(",\"packet\":").append(JsonUtils.str(rel))
          .append(",\"newPage\":").append(p.newPage()).append(",\"tasks\":").append(p.tasks().size()).append('}');
    }
    index.append("]}\n");
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("index.json"), index.toString(), StandardCharsets.UTF_8);
  }
}
