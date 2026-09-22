package org.arend.server.imports;

import org.arend.naming.reference.TCDefReferable;
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
public final class NamespaceCommandUsage {
  /**
   * A part of a namespace command that a name can come from.
   *
   * @param command    the command that brought the name into scope.
   * @param renaming   the renaming that binds the name, or {@code null} when the name comes from
   *                   the implicit {@code &#92;using} part of the command.
   */
  public record Part(@NotNull ConcreteNamespaceCommand command, @Nullable ConcreteNamespaceCommand.NameRenaming renaming) {
    @Override
    public @NotNull String toString() {
      if (renaming == null) return command.toString();
      String newName = renaming.newName();
      return (command.isImport() ? "\\import " : "\\open ") + command.module().textRepresentation()
        + " (" + renaming.reference().getRefName() + (newName == null ? "" : " \\as " + newName) + ")";
    }
  }

  private final Map<Part, Integer> myHits = new LinkedHashMap<>();
  private final Map<ConcreteNamespaceCommand, Set<Part>> myPathDependencies = new LinkedHashMap<>();
  private final List<ConcreteNamespaceCommand> mySinks = new ArrayList<>();
  private Set<TCDefReferable> myDefinitionsHidingInstances = Collections.emptySet();

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
      myHits.merge(part, 1, Integer::sum);
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

  void setDefinitionsHidingInstances(@NotNull Set<TCDefReferable> definitions) {
    myDefinitionsHidingInstances = definitions;
  }

  /**
   * The core of a {@code \lemma} or an {@code \axiom} keeps no body, so an instance used only
   * there is charged to nothing and the command that supplied it can look superfluous. While this
   * is not empty, {@link #getUnused} is an over-estimate.
   *
   * @return the definitions of the module whose instances could not be read.
   */
  public @NotNull Set<TCDefReferable> getDefinitionsHidingInstances() {
    return Collections.unmodifiableSet(myDefinitionsHidingInstances);
  }

  public int getHits(@NotNull Part part) {
    return myHits.getOrDefault(part, 0);
  }

  public @NotNull Set<Part> getUsedParts() {
    return Collections.unmodifiableSet(myHits.keySet());
  }

  public @NotNull Set<Part> getPathDependencies(@NotNull ConcreteNamespaceCommand command) {
    return Collections.unmodifiableSet(myPathDependencies.getOrDefault(command, Collections.emptySet()));
  }

  /**
   * @return the parts of {@param commands} that nothing in the module needed, either directly or to
   *         reach the module of a command that is itself needed. A command nothing needs at all is
   *         reported as a single part with no renaming, rather than once per renaming, so that a
   *         caller can drop the whole statement without reading the rest of the result.
   */
  public @NotNull Set<Part> getUnused(@NotNull Collection<? extends ConcreteNamespaceCommand> commands) {
    Set<Part> used = new LinkedHashSet<>(myHits.keySet());
    Deque<ConcreteNamespaceCommand> toVisit = new ArrayDeque<>();
    for (Part part : used) toVisit.add(part.command());
    Set<ConcreteNamespaceCommand> visited = new HashSet<>();
    while (!toVisit.isEmpty()) {
      ConcreteNamespaceCommand command = toVisit.removeLast();
      if (!visited.add(command)) continue;
      for (Part dependency : getPathDependencies(command)) {
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
    for (Map.Entry<Part, Integer> entry : myHits.entrySet()) {
      builder.append(entry.getKey()).append(" -> ").append(entry.getValue()).append('\n');
    }
    for (Map.Entry<ConcreteNamespaceCommand, Set<Part>> entry : myPathDependencies.entrySet()) {
      builder.append(entry.getKey()).append(" needs ").append(entry.getValue()).append('\n');
    }
    return builder.toString();
  }
}
