package org.arend.frontend.query.findusages;

import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.query.PathDisplayUtils;
import org.arend.frontend.query.ResultJson;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON: one entry per usage (never grouped by row); {@code count} is the full total before truncation.
 */
final class JsonFindUsagesPrinter implements FindUsagesTool.FindUsagesPrinter {
    @Override
    public void print(PrintStream out, FindUsagesTool.Target target, List<UsageFinder.UsageHit> hits, FindUsagesTool.Options options, LibraryManager lm) {
        int shown = options.capped(hits.size());
        List<ResultJson.Row> rows = new ArrayList<>(shown);
        for (int i = 0; i < shown; i++) {
            UsageFinder.UsageHit h = hits.get(i);
            // A file path only for filesystem-backed SOURCE modules; null for zip / test
            // modules (no on-disk path) — the module path still identifies the location.
            String file = PathDisplayUtils.shortenedSourcePath(h.module(), lm);
            rows.add(new ResultJson.Row(target.showLibrary() ? h.module().getLibraryName() : null,
                    h.module().getModulePath().toString(), h.ambientName(), h.ambientKind(),
                    null, null, file, h.line(), h.column()));
        }
        ResultJson.write(out, rows, hits.size());
    }
}
