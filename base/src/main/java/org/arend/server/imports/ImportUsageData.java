package org.arend.server.imports;

import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * Records which namespace commands were consulted while a module was resolved.
 *
 * <p>A command is charged for every name that it, and no earlier entry of the scope, brought into
 * scope. Charges are attributed to a {@link Part} rather than to the command as a whole, so that an
 * individual {@code (foo \as bar)} of an otherwise used command can be reported on its own.
 *
 * <p>Resolving the module path of a command is not a use of the names that path goes through: an
 * unused {@code \open A.B} must not keep the {@code \import A} that makes {@code A} visible alive.
 * Such charges are collected separately as dependencies of the command, and are turned into uses
 * only once the command itself turns out to be used; see {@link #getUnused}.
 */
public final class ImportUsageData {
  private final ConcreteGroup myGroup;

  /**
   * A part of a namespace command that a name can come from. It is also the unit {@link #getUnusedParts}
   * reports: a part with no renaming then stands for the whole command.
   *
   * @param command    the command that brought the name into scope.
   * @param renaming   the renaming that binds the name, or {@code null} when the name comes from
   *                   the implicit {@code &#92;using} part of the command.
   */
  public record Part(@NotNull ConcreteNamespaceCommand command, @Nullable ConcreteNamespaceCommand.NameRenaming renaming) {
    public enum Kind { IMPORT, OPEN, NAME, ALIAS }

    public @NotNull Kind kind() {
      if (renaming == null) return command.isImport() ? Kind.IMPORT : Kind.OPEN;
      return renaming.newName() == null ? Kind.NAME : Kind.ALIAS;
    }

    /**
     * @return the module path of the command, or the name the renaming binds.
     */
    public @NotNull String name() {
      if (renaming == null) return String.join(".", command.module().getPath());
      String newName = renaming.newName();
      return newName != null ? newName : renaming.reference().getRefName();
    }

    /**
     * @return the source element of the command, or of the renaming.
     */
    public @Nullable Object data() {
      return renaming == null ? command.data() : renaming.data();
    }

    @Override
    public @NotNull String toString() {
      if (renaming == null) return command.toString();
      String newName = renaming.newName();
      return (command.isImport() ? "\\import " : "\\open ") + command.module().textRepresentation()
        + " (" + renaming.reference().getRefName() + (newName == null ? "" : " \\as " + newName) + ")";
    }
  }

  private final Set<Part> myHits = new LinkedHashSet<>();
  private final Map<ConcreteNamespaceCommand, Set<Part>> myPathDependencies = new LinkedHashMap<>();
  private final List<ConcreteNamespaceCommand> mySinks = new ArrayList<>();

  ImportUsageData(@NotNull ConcreteGroup group) {
    myGroup = group;
  }

  /**
   * Runs {@param supplier} with every charge redirected to the dependencies of {@param command}
   * instead of being counted as a use.
   */
  <T> T underPathOf(@NotNull ConcreteNamespaceCommand command, @NotNull Supplier<T> supplier) {
    mySinks.add(command);
    try {
      return supplier.get();
    } finally {
      mySinks.removeLast();
    }
  }

  /**
   * @return the part of {@param command} that binds {@param name}.
   */
  static @NotNull Part partOf(@NotNull ConcreteNamespaceCommand command, @NotNull String name) {
    return new Part(command, findRenaming(command, name));
  }

  void charge(@NotNull Part part) {
    if (mySinks.isEmpty()) {
      myHits.add(part);
    } else {
      ConcreteNamespaceCommand sink = mySinks.getLast();
      // a command whose path goes through itself is not a dependency of itself
      if (sink != part.command()) myPathDependencies.computeIfAbsent(sink, k -> new LinkedHashSet<>()).add(part);
    }
  }

  /**
   * Mirrors the way {@link org.arend.naming.scope.NamespaceCommandNamespace} matches a name against
   * the renamings of a command: a renaming answers under its new name, or, when it renames nothing,
   * under the name it refers to.
   */
  private static @Nullable ConcreteNamespaceCommand.NameRenaming findRenaming(ConcreteNamespaceCommand command, String name) {
    for (ConcreteNamespaceCommand.NameRenaming renaming : command.renamings()) {
      String newName = renaming.newName();
      if (newName != null ? name.equals(newName) : name.equals(renaming.reference().getRefName())) {
        return renaming;
      }
    }
    return null;
  }

  /**
   * @return the parts of {@param commands} that nothing in the module needed, either directly or to
   *         reach the module of a command that is itself needed. A command nothing needs at all is
   *         reported as a single part with no renaming, rather than once per renaming, so that a
   *         caller can drop the whole statement without reading the rest of the result.
   */
  public @NotNull Set<Part> getUnused(@NotNull Collection<? extends ConcreteNamespaceCommand> commands) {
    Set<Part> used = new LinkedHashSet<>(myHits);
    Deque<ConcreteNamespaceCommand> toVisit = new ArrayDeque<>();
    for (Part part : used) toVisit.add(part.command());
    Set<ConcreteNamespaceCommand> visited = new HashSet<>();
    while (!toVisit.isEmpty()) {
      ConcreteNamespaceCommand command = toVisit.removeLast();
      if (!visited.add(command)) continue;
      for (Part dependency : myPathDependencies.getOrDefault(command, Collections.emptySet())) {
        if (used.add(dependency)) toVisit.add(dependency.command());
      }
    }

    Set<Part> result = new LinkedHashSet<>();
    for (ConcreteNamespaceCommand command : commands) {
      List<Part> unusedRenamings = new ArrayList<>();
      boolean anyRenamingUsed = false;
      for (ConcreteNamespaceCommand.NameRenaming renaming : command.renamings()) {
        Part part = new Part(command, renaming);
        if (used.contains(part)) anyRenamingUsed = true;
        else unusedRenamings.add(part);
      }
      // the part with no renaming is the rest of the module, which only a using-command brings in
      if (!anyRenamingUsed && !used.contains(new Part(command, null))) {
        result.add(new Part(command, null));
      } else {
        result.addAll(unusedRenamings);
      }
    }
    return result;
  }

  @Override
  public @NotNull String toString() {
    StringBuilder builder = new StringBuilder();
    for (Part part : myHits) {
      builder.append(part).append('\n');
    }
    for (Map.Entry<ConcreteNamespaceCommand, Set<Part>> entry : myPathDependencies.entrySet()) {
      builder.append(entry.getKey()).append(" needs ").append(entry.getValue()).append('\n');
    }
    return builder.toString();
  }

  /**
   * @return the parts of the commands of the module that nothing needs, in source order.
   */
  public @NotNull List<Part> getUnusedParts() {
    return new ArrayList<>(getUnused(ImportUsageTracer.collectCommands(myGroup)));
  }
}
