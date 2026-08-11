package org.arend.frontend.query.findusages;

import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.query.MatchHighlighter;
import org.arend.frontend.query.PathDisplayUtils;
import org.arend.frontend.query.QualifiedName;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Text: {@code -ss}-style entries; usages on the same source row share one highlighted line.
 */
final class TextFindUsagesPrinter implements FindUsagesTool.FindUsagesPrinter {
    @Override
    public void print(PrintStream out, FindUsagesTool.Target target, List<UsageFinder.UsageHit> hits, FindUsagesTool.Options options, LibraryManager lm) {
        out.println("Usages of " + QualifiedName.format(target.showLibrary(),
                target.module().getLibraryName(), target.module().getModulePath().toString(),
                target.referable().getRefLongName().toString())
                + "  [" + target.kind() + "]");
        if (hits.isEmpty()) {
            out.println();
            out.println("No usages.");
            return;
        }

        List<UsageFinder.UsageHit> shownHits = hits.subList(0, options.capped(hits.size()));

        // Usages on the same source row share one highlighted line, so each row-group is
        // rendered once with every column highlighted.
        for (List<UsageFinder.UsageHit> group : groupByRow(shownHits)) {
            UsageFinder.UsageHit first = group.getFirst();
            String loc = PathDisplayUtils.label(first.module(), lm);
            out.println();
            for (UsageFinder.UsageHit h : group) out.println(loc + ":" + h.line() + ":" + h.column());
            out.println(QualifiedName.format(target.showLibrary(), first.module().getLibraryName(),
                    first.module().getModulePath().toString(), first.ambientName()) + "  [" + first.ambientKind() + "]");
            if (options.printLine) {
                String content = first.sourceLine();
                if (content != null) {
                    List<Integer> cols = new ArrayList<>(group.size());
                    for (UsageFinder.UsageHit h : group) cols.add(h.column());
                    out.println("  " + MatchHighlighter.highlightIdentifiersAt(content, cols).strip());
                }
            }
        }
        out.println();
        out.println("Found " + hits.size() + " usage" + (hits.size() == 1 ? "" : "s")
                + options.truncationNote(shownHits.size(), hits.size()));
    }

    /** Splits the (module, line, column)-sorted {@code hits} into runs that share one (module, line). */
    private static List<List<UsageFinder.UsageHit>> groupByRow(List<UsageFinder.UsageHit> hits) {
        List<List<UsageFinder.UsageHit>> groups = new ArrayList<>();
        int gi = 0;
        while (gi < hits.size()) {
            UsageFinder.UsageHit first = hits.get(gi);
            int gj = gi + 1;
            while (gj < hits.size()
                    && hits.get(gj).line() == first.line()
                    && hits.get(gj).module().equals(first.module())) {
                gj++;
            }
            groups.add(hits.subList(gi, gj));
            gi = gj;
        }
        return groups;
    }
}
