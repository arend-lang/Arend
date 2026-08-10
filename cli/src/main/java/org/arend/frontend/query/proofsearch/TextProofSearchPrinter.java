package org.arend.frontend.query.proofsearch;

import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.query.PathDisplayUtils;
import org.arend.frontend.query.QualifiedName;
import org.arend.frontend.query.SignaturePrintVisitor;
import org.arend.frontend.query.SourcePositionUtils;
import org.arend.frontend.query.SymbolIndex;

import java.io.PrintStream;

/** Text: {@code -ss}-style entries; each match prints its location, qualified name, and matched slice. */
final class TextProofSearchPrinter implements ProofSearchTool.ProofSearchPrinter {
  @Override
  public void print(PrintStream out, ProofSearchEngine.Result found, ProofSearchTool.Options options,
                    LibraryManager lm, boolean omitLibrary) {
    for (ProofSearchEngine.ProofMatch m : found.matches()) {
      int[] lc = SourcePositionUtils.lineColumn(m.referable());
      String path = PathDisplayUtils.shortenedSourcePath(m.module(), lm);
      out.println((path == null || path.isEmpty())
          ? "<" + m.module().getLibraryName() + ":" + m.module().getModulePath() + ">"
          : path + ":" + lc[0] + ":" + lc[1]);
      out.println(QualifiedName.format(!omitLibrary, m.module().getLibraryName(),
          m.module().getModulePath().toString(), m.referable().getRefLongName().toString())
          + "  [" + SymbolIndex.kindOf(m.referable(), m.definition()).name() + "]");
      // print-full shows the definition's signature (same as -ss); otherwise the matched slice.
      String content = options.printFull
          ? SignaturePrintVisitor.render(m.definition())
          : ProofMatchRenderer.highlightedSlice(m.result(), m.codomain());
      out.println("  " + content.replace("\n", "\n  "));
      out.println();
    }
    if (found.total() == 0) {
      out.println("No matches.");
    } else {
      out.println("Found " + found.total() + " match" + (found.total() == 1 ? "" : "es")
          + options.truncationNote(found.matches().size(), found.total()));
    }
  }
}
