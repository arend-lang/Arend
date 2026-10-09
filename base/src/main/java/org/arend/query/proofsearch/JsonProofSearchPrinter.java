package org.arend.query.proofsearch;

import org.arend.query.QueryLibraries;
import org.arend.query.PathDisplayUtils;
import org.arend.query.ResultJson;
import org.arend.query.SignaturePrintVisitor;
import org.arend.query.SourcePositionUtils;
import org.arend.query.SymbolIndex;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/** JSON: one object per match; {@code count} is the exact total before {@code limit} truncation. */
final class JsonProofSearchPrinter implements ProofSearchTool.ProofSearchPrinter {
  @Override
  public void print(PrintStream out, ProofSearchEngine.Result found, ProofSearchTool.Options options,
                    QueryLibraries lm, boolean omitLibrary) {
    List<ResultJson.Row> rows = new ArrayList<>(found.matches().size());
    for (ProofSearchEngine.ProofMatch m : found.matches()) {
      int[] lc = SourcePositionUtils.lineColumn(m.referable());
      String file = PathDisplayUtils.shortenedSourcePath(m.module(), lm);
      // print-full -> the -ss-style signature; otherwise -> the matched type slice.
      String sig = options.printFull ? SignaturePrintVisitor.render(m.definition()) : null;
      String expr = options.printFull ? null : ProofMatchRenderer.plainSlice(m.result(), m.codomain());
      rows.add(new ResultJson.Row(
          omitLibrary ? null : m.module().getLibraryName(),
          m.module().getModulePath().toString(),
          m.referable().getRefLongName().toString(),
          SymbolIndex.kindOf(m.referable(), m.definition()).name(),
          sig, expr, file, lc[0], lc[1]));
    }
    ResultJson.write(out, rows, found.total());
  }
}
