package org.arend.server.imports;

import org.arend.ext.ArendExtension;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor;
import org.arend.naming.scope.Scope;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.GroupData;
import org.arend.term.concrete.Concrete;
import org.arend.term.concrete.ReplaceDataVisitor;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.typechecking.instance.ArendInstances;
import org.arend.typechecking.provider.SimpleConcreteProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds out which namespace commands of a module its content actually needs, by resolving the
 * module a second time in scopes that remember which command answered each lookup.
 *
 * <p>The written form of a reference is what decides which command serves it -- {@code b} and
 * {@code A.b} may well denote the same definition while needing different commands -- and it does
 * not survive resolution: {@link org.arend.server.impl.ArendCheckerImpl} resolves a copy of each
 * definition, and resolving replaces the path a reference was written as by the referable it
 * denotes. Reading the answer off the resolved definitions would therefore mean guessing. Resolving
 * afresh instead keeps the question exact, and comes with the whole resolver attached, so metas,
 * numeric literals handled by an extension, patterns and field calls need no special treatment.
 *
 * <p>The module is resolved from a {@link ReplaceDataVisitor} copy of its raw group, exactly as
 * {@code ArendCheckerImpl} does, so that the references of the stored group stay unresolved and
 * this pass leaves no trace on the server.
 */
public final class ImportUsageTracer {
  private ImportUsageTracer() {}

  /**
   * Traces {@param module} and charges the namespace commands that supplied the instances its
   * definitions turned out to use, as well as the ones name resolution needed.
   *
   * @return {@code null} if the module is unknown, has not been resolved, or has a definition
   *         without a usable core -- the state between name resolution and typechecking. Nothing
   *         can be said about instances then, and an answer that ignored them would call an import
   *         superfluous because the only thing needing it is invisible.
   */
  public static @Nullable NamespaceCommandUsage trace(@NotNull ArendServerImpl server, @NotNull ModuleLocation module) {
    GroupData groupData = server.getGroupData(module);
    if (groupData == null || !groupData.isResolved()) return null;
    ConcreteGroup group = groupData.getRawGroup();

    UsedInstances instances = UsedInstances.collect(group);
    if (instances == null) return null;

    Map<TCDefReferable, TracingLexicalScope> frames = new HashMap<>();
    NamespaceCommandUsage usage = traceResolution(server, module, group, frames);

    for (TCDefReferable definition : instances.getDefinitions()) {
      TracingLexicalScope frame = frames.get(definition);
      if (frame == null) continue;
      for (TCDefReferable instance : instances.forDefinition(definition)) {
        frame.chargeInstance(instance);
      }
    }
    usage.setDefinitionsHidingInstances(instances.getDefinitionsHidingInstances());
    return usage;
  }

  /**
   * Traces only what name resolution needs, leaving instances out. Useful on its own to tell the
   * two halves of the answer apart; a caller deciding whether a command may be removed wants
   * {@link #trace}.
   */
  public static @Nullable NamespaceCommandUsage traceResolution(@NotNull ArendServerImpl server, @NotNull ModuleLocation module) {
    GroupData groupData = server.getGroupData(module);
    if (groupData == null || !groupData.isResolved()) return null;
    return traceResolution(server, module, groupData.getRawGroup(), null);
  }

  private static @NotNull NamespaceCommandUsage traceResolution(@NotNull ArendServerImpl server, @NotNull ModuleLocation module, @NotNull ConcreteGroup group, @Nullable Map<TCDefReferable, TracingLexicalScope> frames) {
    Map<GlobalReferable, Concrete.GeneralDefinition> definitions = new HashMap<>();
    group.traverseGroup(subgroup -> {
      Concrete.ResolvableDefinition definition = subgroup.definition();
      if (definition != null) {
        definitions.put(definition.getData(), definition.accept(new ReplaceDataVisitor(true), null));
      }
    });

    NamespaceCommandUsage usage = new NamespaceCommandUsage();
    ModuleScopeProvider provider = server.getModuleScopeProvider(module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST);
    Scope parentScope = TracingImportedScope.forFile(group, provider, usage);
    ArendExtension extension = server.getExtensionProvider().getArendExtension(module.getLibraryName());

    new DefinitionResolveNameVisitor(new SimpleConcreteProvider(definitions), server.getTypingInfo(), DummyErrorReporter.INSTANCE, extension == null ? null : extension.getLiteralTypechecker(), null)
      .withScopeFactory((subgroup, parent, isDynamicContext, withAdditionalContent) -> {
        if (!withAdditionalContent) return new TracingLexicalScope(parent, subgroup, null, isDynamicContext, false, usage);
        TracingLexicalScope scope = TracingLexicalScope.insideOf(subgroup, parent, isDynamicContext, usage);
        // the scope a definition is resolved in is also the one its instance pool is built from
        if (frames != null && !isDynamicContext && subgroup.definition() != null) {
          frames.put(subgroup.definition().getData(), scope);
        }
        return scope;
      })
      .resolveGroup(group, parentScope, new ArendInstances(), null);

    return usage;
  }

  /**
   * @return every namespace command of {@param group} and of its subgroups, in source order.
   */
  public static @NotNull List<ConcreteNamespaceCommand> collectCommands(@NotNull ConcreteGroup group) {
    List<ConcreteNamespaceCommand> result = new ArrayList<>();
    group.traverseGroup(subgroup -> {
      for (ConcreteStatement statement : subgroup.statements()) {
        ConcreteNamespaceCommand command = statement.command();
        if (command != null) result.add(command);
      }
    });
    return result;
  }
}
