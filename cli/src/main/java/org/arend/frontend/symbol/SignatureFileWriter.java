package org.arend.frontend.symbol;

import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.library.FileSourceLibrary;
import org.arend.frontend.library.SourceLibrary;
import org.arend.server.ArendServer;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.prettyprint.PrettyPrintVisitor;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

  public record Result(int written, int skipped, @NotNull List<String> errors) {}

  /**
   * Emit signature files for every source module in {@code library}. Returns
   * a summary; errors are non-fatal (one failed module does not stop the rest).
   */
  public static @NotNull Result writeAll(@NotNull ArendServer server, @NotNull SourceLibrary library) {
    return writeFiltered(server, library, null);
  }

  /**
   * Like {@link #writeAll}, but only emits files for modules in {@code only}.
   * Pass {@code null} to mean "every module".
   */
  public static @NotNull Result writeFiltered(@NotNull ArendServer server,
                                              @NotNull SourceLibrary library,
                                              java.util.Set<ModulePath> only) {
    List<String> errors = new ArrayList<>();
    if (!(library instanceof FileSourceLibrary fl)) {
      errors.add("[WARN] -sig skipped library '" + library.getLibraryName() + "': not a file-backed source library");
      return new Result(0, 0, errors);
    }
    Path sigRoot = sigRootFor(fl);
    if (sigRoot == null) {
      errors.add("[WARN] -sig skipped library '" + library.getLibraryName() + "': cannot determine output directory");
      return new Result(0, 0, errors);
    }

    int written = 0;
    int skipped = 0;
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
        String text = renderModule(group);
        Files.writeString(outPath, text, StandardCharsets.UTF_8);
        written++;
      } catch (IOException | RuntimeException e) {
        errors.add("[ERROR] -sig failed for " + mp + ": " + e.getMessage());
      }
    }
    return new Result(written, skipped, errors);
  }

  private static @org.jetbrains.annotations.Nullable Path sigRootFor(FileSourceLibrary fl) {
    Path basePath = fl.getBasePath();
    if (basePath != null) return basePath.resolve(".sig");
    Path src = fl.getSourceBasePath();
    if (src == null) return null;
    Path parent = src.getParent();
    return parent != null ? parent.resolve(".sig") : src.resolve(".sig");
  }

  private static @NotNull String renderModule(@NotNull ConcreteGroup group) {
    StringBuilder sb = new StringBuilder();
    BodyStripPrettyPrintVisitor v = new BodyStripPrettyPrintVisitor(sb, 0, true);
    // The top-level group represents a file. printGroup would wrap it in
    // `\module <name> \where { ... }`, which is correct for nested groups but
    // not for a source file. Emit the file's statements directly so the
    // result is shaped like a real .ard file.
    v.printStatements(group.statements());
    if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
    return sb.toString();
  }

  /**
   * Pretty-printer that suppresses every function-style body and class-field
   * implementation, replacing them with the goal expression {@code {?}}. Class
   * field declarations, data constructors, level parameters, namespace
   * commands, and {@code \where} blocks are printed identically to the base
   * visitor.
   *
   * <p>{@link #copy} is overridden so that nested {@code BinOpLayout} layouts
   * propagate the body-stripping mode.
   */
  private static final class BodyStripPrettyPrintVisitor extends PrettyPrintVisitor {
    private static final String GOAL = "{?}";

    BodyStripPrettyPrintVisitor(StringBuilder builder, int indent, boolean doIndent) {
      super(builder, indent, doIndent);
    }

    @Override
    protected PrettyPrintVisitor copy(StringBuilder builder, int indent, boolean doIndent) {
      return new BodyStripPrettyPrintVisitor(builder, indent, doIndent);
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
