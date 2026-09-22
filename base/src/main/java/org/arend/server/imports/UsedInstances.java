package org.arend.server.imports;

import org.arend.core.definition.Definition;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The instances each definition of a module turned out to use.
 *
 * <p>An instance is not written anywhere: the typechecker picks it from the pool that the
 * namespace commands in scope contributed, so resolving a module again says nothing about it.
 * Neither does the elaborated term -- an instance the typechecker picked need leave no call
 * behind in it, and on arend-lib three modules lose an import that way. The choice is therefore
 * recorded where it is made, in
 * {@link org.arend.typechecking.instance.pool.GlobalInstancePool}, and kept on the definition;
 * this only reads it back.
 *
 * <p>A definition carrying no record is one that was neither typechecked in this session nor read
 * from a binary that had the set. That is reported by {@link #getDefinitionsHidingInstances()}
 * rather than guessed at, since "picked none" and "never recorded" are different answers.
 */
public final class UsedInstances {
  private final Map<TCDefReferable, Set<TCDefReferable>> myByDefinition;
  private final Set<TCDefReferable> myHidingInstances;

  private UsedInstances(Map<TCDefReferable, Set<TCDefReferable>> byDefinition, Set<TCDefReferable> hidingInstances) {
    myByDefinition = byDefinition;
    myHidingInstances = hidingInstances;
  }

  /**
   * Reads the instances used by every definition of {@param group}.
   *
   * @return {@code null} if some definition of the group has no usable core, which is the state of
   *         a module between name resolution and typechecking, and the state of one that failed to
   *         typecheck. Nothing can be said about instances then, and so nothing can be said about
   *         which namespace commands are superfluous.
   */
  public static @Nullable UsedInstances collect(@NotNull ConcreteGroup group) {
    Map<TCDefReferable, Set<TCDefReferable>> byDefinition = new LinkedHashMap<>();
    Set<TCDefReferable> hidingInstances = new LinkedHashSet<>();
    boolean[] incomplete = new boolean[1];

    group.traverseGroup(subgroup -> {
      Concrete.ResolvableDefinition definition = subgroup.definition();
      if (definition == null) return;
      TCDefReferable referable = definition.getData();
      Definition core = referable.getTypechecked();
      if (core == null || !isUsable(core)) {
        // a meta and the like are never typechecked, and their absence says nothing
        if (referable.getKind().isTypecheckable()) incomplete[0] = true;
        return;
      }
      Set<TCDefReferable> instances = core.getUsedInstances();
      if (instances == null) {
        hidingInstances.add(referable);
      } else if (!instances.isEmpty()) {
        byDefinition.put(referable, instances);
      }
    });

    return incomplete[0] ? null : new UsedInstances(byDefinition, hidingInstances);
  }

  private static boolean isUsable(Definition core) {
    Definition.TypeCheckingStatus status = core.status();
    return !status.needsTypeChecking() && !status.hasErrors();
  }

  /**
   * @return the instances the typechecker picked for {@param definition}.
   */
  public @NotNull Set<TCDefReferable> forDefinition(@NotNull TCDefReferable definition) {
    return myByDefinition.getOrDefault(definition, Collections.emptySet());
  }

  public @NotNull Set<TCDefReferable> getDefinitions() {
    return Collections.unmodifiableSet(myByDefinition.keySet());
  }

  /**
   * @return the definitions that carry no record of the instances they used, so that a caller can
   *         tell an answer that covers the whole module from one that does not.
   */
  public @NotNull Set<TCDefReferable> getDefinitionsHidingInstances() {
    return Collections.unmodifiableSet(myHidingInstances);
  }
}
