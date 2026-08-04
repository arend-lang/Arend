package org.arend.frontend.query;

import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.DocStringBuilder;
import org.arend.ext.prettyprinting.doc.LineDoc;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.LocatedReferable;
import org.arend.server.ArendServer;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Writes signature-only views of Arend modules to a parallel {@code .sig/} tree
 * at the library root. Each output file mirrors the source tree, but every
 * function/lemma/instance/meta body and every class-field implementation is
 * replaced with the goal expression {@code {?}}. Class field declarations,
 * data constructors, namespace commands, and {@code \where} structure are
 * preserved.
 *
 * <p>The output is valid Arend syntax (the goals parse), so the agent / reader
 * tools can {@code grep} or {@code Read} these files just like normal sources
 * but pay only the token cost of signatures.
 */
public final class SignatureFileWriter {

  private SignatureFileWriter() {}

  public record Result(int written, int skipped, int skippedDefinitions, @NotNull List<String> errors) {}

  /**
   * Emit signature files for every source module in {@code library}. Returns
   * a summary; errors are non-fatal (one failed module does not stop the rest).
   */
  public static @NotNull Result writeAll(@NotNull ArendServer server, @NotNull SourceLibrary library) {
    return writeFiltered(server, library, null, Collections.emptySet());
  }

  /**
   * Like {@link #writeAll}, but only emits files for modules in {@code only}.
   * Pass {@code null} to mean "every module". Top-level (or nested) definitions
   * whose referable appears in {@code failedDefinitions} are replaced with a
   * one-line {@code -- skipped:} placeholder so the .sig file stays a parseable
   * snapshot of just the verified declarations.
   */
  public static @NotNull Result writeFiltered(@NotNull ArendServer server,
                                              @NotNull SourceLibrary library,
                                              @Nullable Set<ModulePath> only,
                                              @NotNull Set<? extends LocatedReferable> failedDefinitions) {
    List<String> errors = new ArrayList<>();
    if (!(library instanceof FileSourceLibrary fl)) {
      errors.add("[WARN] .sig skipped library '" + library.getLibraryName() + "': not a file-backed source library");
      return new Result(0, 0, 0, errors);
    }
    Path sigRoot = sigRootFor(fl);
    if (sigRoot == null) {
      errors.add("[WARN] .sig skipped library '" + library.getLibraryName() + "': cannot determine output directory");
      return new Result(0, 0, 0, errors);
    }

    int written = 0;
    int skipped = 0;
    int skippedDefs = 0;
    for (ModulePath mp : library.findModules(false)) {
      if (only != null && !only.contains(mp)) {
        skipped++;
        continue;
      }
      ModuleLocation loc = new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp);
      ConcreteGroup group = server.getRawGroup(loc);
      if (group == null) {
        skipped++;
        continue;
      }
      try {
        Path outPath = FileUtils.sourceFile(sigRoot, mp);
        Path parent = outPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        RenderResult rr = renderModule(group, failedDefinitions);
        Files.writeString(outPath, rr.text, StandardCharsets.UTF_8);
        written++;
        skippedDefs += rr.skippedDefinitions;
      } catch (IOException | RuntimeException e) {
        errors.add("[ERROR] .sig failed for " + mp + ": " + e.getMessage());
      }
    }
    return new Result(written, skipped, skippedDefs, errors);
  }

  private static @org.jetbrains.annotations.Nullable Path sigRootFor(FileSourceLibrary fl) {
    Path basePath = fl.getBasePath();
    if (basePath != null) return basePath.resolve(".sig");
    Path src = fl.getSourceBasePath();
    if (src == null) return null;
    Path parent = src.getParent();
    return parent != null ? parent.resolve(".sig") : src.resolve(".sig");
  }

  private record RenderResult(@NotNull String text, int skippedDefinitions) {}

  private static @NotNull RenderResult renderModule(@NotNull ConcreteGroup group,
                                                    @NotNull Set<? extends LocatedReferable> failedDefinitions) {
    StringBuilder sb = new StringBuilder();
    BodyStripPrettyPrintVisitor v = new BodyStripPrettyPrintVisitor(sb, 0, true, failedDefinitions);
    // The top-level group represents a file. printGroup would wrap it in
    // `\module <name> \where { ... }`, which is correct for nested groups but
    // not for a source file. Emit the file's statements directly so the
    // result is shaped like a real .ard file.
    v.printStatements(group.statements());
    if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
    return new RenderResult(sb.toString(), v.skippedCount);
  }

  /**
   * Pretty-printer that suppresses every function-style body and class-field
   * implementation, replacing them with the goal expression {@code {?}}. Class
   * field declarations, data constructors, level parameters, namespace
   * commands, and {@code \where} blocks are printed identically to the base
   * visitor.
   *
   * <p>If a definition's referable appears in {@code failedDefinitions} (because
   * typechecking or name-resolution flagged it), it is replaced with a
   * {@code -- skipped: <name> (typecheck errors)} comment so the .sig file
   * only contains verified declarations.
   *
   * <p>{@link #copy} is overridden so that nested {@code BinOpLayout} layouts
   * propagate the body-stripping mode.
   */
  private static final class BodyStripPrettyPrintVisitor extends PrettyPrintVisitor {
    private static final String GOAL = "{?}";
    private final Set<? extends LocatedReferable> failed;
    int skippedCount = 0;

    BodyStripPrettyPrintVisitor(StringBuilder builder, int indent, boolean doIndent,
                                Set<? extends LocatedReferable> failed) {
      super(builder, indent, doIndent);
      this.failed = failed;
    }

    @Override
    protected PrettyPrintVisitor copy(StringBuilder builder, int indent, boolean doIndent) {
      return new BodyStripPrettyPrintVisitor(builder, indent, doIndent, failed);
    }

    @Override
    public void printStatements(List<? extends ConcreteStatement> statements) {
      // Replace failed-definition statements with a one-line skipped-comment
      // in place, preserving file order. Each kept statement is printed via
      // super.printStatements on a single-element list (which produces no
      // leading separator), and the inter-statement spacing matches the base
      // visitor's rule (newline between two adjacent namespace commands,
      // blank line otherwise).
      ConcreteStatement prev = null;
      for (ConcreteStatement st : statements) {
        if (prev != null) {
          myBuilder.append(prev.command() != null && st.command() != null ? "\n" : "\n\n");
        }
        ConcreteGroup g = st.group();
        if (g != null && failed.contains(g.referable())) {
          printDocComment(g);
          printIndent();
          myBuilder.append("-- skipped: ").append(g.referable().getRefName()).append(" (typecheck errors)");
          skippedCount++;
        } else {
          super.printStatements(Collections.singletonList(st));
        }
        prev = st;
      }
    }

    private void printDocComment(ConcreteGroup group) {
      List<LineDoc> lines = group.description().linearize(0, true);
      if (lines.isEmpty()) return;
      if (lines.size() == 1) {
        printIndent();
        myBuilder.append("-- | ");
        DocStringBuilder.buildDocComment(myBuilder, lines.getFirst(), true);
        myBuilder.append('\n');
      } else {
        printIndent();
        myBuilder.append("{- |\n");
        myIndent += INDENT;
        for (LineDoc line : lines) {
          printIndent();
          DocStringBuilder.buildDocComment(myBuilder, line, true);
          myBuilder.append('\n');
        }
        myIndent -= INDENT;
        printIndent();
        myBuilder.append("-}\n");
      }
    }

    @Override
    public void prettyPrintBody(Concrete.FunctionBody body, boolean isFunction) {
      myBuilder.append("=> ").append(GOAL);
    }

    @Override
    public void prettyPrintClassFieldImpl(Concrete.ClassFieldImpl classFieldImpl) {
      String name = classFieldImpl.getImplementedField() == null
          ? "_" : classFieldImpl.getImplementedField().textRepresentation();
      myBuilder.append(name).append(" => ").append(GOAL);
    }

    @Override
    public Void visitMeta(Concrete.MetaDefinition def, Void params) {
      myBuilder.append("\\meta ");
      // Keep the same name+precedence path as the parent class for fixity etc.
      // We cannot reach prettyPrintNameWithPrecedence (private), so just print
      // the textual name; precedence on metas is rare and the signature view
      // is already approximate.
      myBuilder.append(def.getData().textRepresentation());
      myBuilder.append(" ");
      prettyPrintParameters(def.getParameters());
      if (def.body != null) {
        myBuilder.append(" => ").append(GOAL);
      }
      return null;
    }
  }
}
