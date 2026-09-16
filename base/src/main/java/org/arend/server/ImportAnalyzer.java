package org.arend.server;

import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.naming.reference.FieldReferable;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.InternalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.RedirectingReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.resolving.typing.DynamicScopeProvider;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.scope.*;
import org.arend.prelude.Prelude;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ImportAnalyzer {
  private final ArendServer myServer;

  public ImportAnalyzer(@NotNull ArendServer server) {
    myServer = server;
  }

  public @NotNull List<ImportFinding> analyze(@NotNull ModuleLocation module) {
    List<ImportFinding> findings = findUnused(module, false);
    return findings == null ? Collections.emptyList() : findings;
  }

  private Analysis preanalyse(@NotNull ModuleLocation module, boolean ignoreErrors) {
    ConcreteGroup group = myServer.getRawGroup(module);
    if (group == null) return null;

    ScopeUsages usages = myServer.getScopeUsages();
    if (!usages.isCollected(module)) return null;

    boolean hasErrors = hasErrors(module);
    if (!ignoreErrors && hasErrors) return null;

    Analysis analysis = new Analysis(module, usages, myServer.getTypingInfo(), instancesTrusted(module) || (ignoreErrors && hasErrors), ignoreErrors && hasErrors);
    analysis.collectGroups(group, myServer.getModuleScopeProvider(module.getLibraryName(), module.getLocationKind() == ModuleLocation.LocationKind.TEST), null);
    return analysis;
  }

  public @Nullable List<ImportFinding> findUnused(@NotNull ModuleLocation module, boolean ignoreErrors) {
    Analysis analysis = preanalyse(module, ignoreErrors);
    return analysis == null ? null : analysis.run();
  }

  public @Nullable ImportStructure optimize(@NotNull ModuleLocation module, boolean soft, boolean ignoreErrors) {
    Analysis analysis = preanalyse(module, ignoreErrors);
    return analysis == null ? null : analysis.buildStructure(soft);
  }

  private boolean hasErrors(@NotNull ModuleLocation module) {
    for (GeneralError error : errorsOf(module)) {
      if (error.level == GeneralError.Level.ERROR && error.getStage() != GeneralError.Stage.TYPECHECKER) return true;
    }
    return false;
  }

  public boolean instancesTrusted(@NotNull ModuleLocation module) {
    for (GeneralError error : errorsOf(module)) {
      if (error.level == GeneralError.Level.ERROR) return false;
    }
    return true;
  }

  private @NotNull List<GeneralError> errorsOf(@NotNull ModuleLocation module) {
    List<GeneralError> errors = myServer.getErrorMap().get(module);
    return errors == null ? Collections.emptyList() : errors;
  }

  private static class CommandInfo {
    final ConcreteNamespaceCommand command;
    final GroupInfo group;
    final Set<String> usedNames = new HashSet<>();
    Set<TCDefReferable> instances = Collections.emptySet();
    Scope namespace;
    boolean grounded;
    boolean opaque;
    String headName;
    Referable headReferable;

    final List<CommandInfo> headProviders = new ArrayList<>();

    CommandInfo(ConcreteNamespaceCommand command, GroupInfo group) {
      this.command = command;
      this.group = group;
    }

    void keepWhole() {
      grounded = true;
      opaque = true;
    }
  }

  private static class GroupInfo {
    final ConcreteGroup group;
    final GroupInfo parent;
    final Scope namespaceScope;
    final Scope enclosingDynamicScope;
    final List<CommandInfo> commands = new ArrayList<>();
    final List<GroupInfo> children = new ArrayList<>();

    GroupInfo(ConcreteGroup group, GroupInfo parent, Scope namespaceScope, Scope enclosingDynamicScope) {
      this.group = group;
      this.parent = parent;
      this.namespaceScope = namespaceScope;
      this.enclosingDynamicScope = enclosingDynamicScope;
    }
  }

  private static class Analysis {
    private final ModuleLocation myModule;
    private final ScopeUsages myUsages;
    private final TypingInfo myTypingInfo;
    private final boolean myInstancesTrusted;
    private final boolean myInstancesComplete;
    private final Map<LongName, GroupInfo> myGroups = new HashMap<>();
    private final Map<ConcreteNamespaceCommand, CommandInfo> myCommands = new IdentityHashMap<>();
    private final List<CommandInfo> myAllCommands = new ArrayList<>();
    private GroupInfo myRoot;

    Analysis(ModuleLocation module, ScopeUsages usages, TypingInfo typingInfo, boolean instancesTrusted, boolean instancesComplete) {
      myModule = module;
      myUsages = usages;
      myTypingInfo = typingInfo;
      myInstancesTrusted = instancesTrusted;
      myInstancesComplete = instancesComplete;
      myInstancesKnown = instancesTrusted;
    }

    void collectGroups(ConcreteGroup group, ModuleScopeProvider moduleScopeProvider, GroupInfo parent) {
      collectGroups(group, ScopeFactory.parentScopeForGroup(group, moduleScopeProvider, true), parent, null);
    }

    private void collectGroups(ConcreteGroup group, Scope parentScope, GroupInfo parent, Scope enclosingDynamicScope) {
      GroupInfo info = new GroupInfo(group, parent, CachingScope.make(new LexicalScope(parentScope, group, null, true, false)), enclosingDynamicScope);
      myGroups.put(group.referable().getRefLongName(), info);
      if (parent == null) myRoot = info; else parent.children.add(info);

      Scope staticScope = null;
      boolean isTopLevel = group.isTopLevel();
      for (ConcreteStatement statement : group.statements()) {
        ConcreteNamespaceCommand command = statement.command();
        if (command != null) {
          if (!command.isImport() || isTopLevel) addCommand(command, info);
        }
        ConcreteGroup subgroup = statement.group();
        if (subgroup != null) {
          if (staticScope == null) staticScope = CachingScope.make(LexicalScope.insideOf(group, parentScope, false));
          collectGroups(subgroup, staticScope, info, enclosingDynamicScope);
        }
      }

      if (!group.dynamicGroups().isEmpty()) {
        Scope dynamicScope = CachingScope.make(LexicalScope.insideOf(group, parentScope, true));
        Scope ownScope = enclosingDynamicScope;
        DynamicScopeProvider provider = myTypingInfo.getDynamicScopeProvider(group.referable());
        if (provider != null) {
          Scope classScope = new DynamicScope(provider, myTypingInfo, DynamicScope.Extent.WITH_SUPER_DYNAMIC);
          dynamicScope = new MergeScope(dynamicScope, classScope);
          ownScope = ownScope == null ? classScope : new MergeScope(ownScope, classScope);
        }
        for (ConcreteGroup subgroup : group.dynamicGroups()) {
          collectGroups(subgroup, dynamicScope, info, ownScope);
        }
      }
    }

    private void addCommand(ConcreteNamespaceCommand command, GroupInfo group) {
      CommandInfo info = new CommandInfo(command, group);
      myCommands.put(command, info);
      myAllCommands.add(info);
      group.commands.add(info);

      for (ConcreteNamespaceCommand.NameRenaming renaming : command.renamings()) {
        if (!renaming.isStatic()) info.keepWhole();
      }

      Scope namespaceScope = group.namespaceScope;
      Scope importedScope = command.isImport() ? namespaceScope.getImportedSubscope() : namespaceScope;
      info.namespace = importedScope == null ? null : command.module().copy().resolveNamespace(importedScope);
      if (info.namespace == null) {
        info.keepWhole();
        return;
      }
      info.instances = collectInstances(command, info.namespace);
    }

    private static Set<TCDefReferable> collectInstances(ConcreteNamespaceCommand command, Scope namespace) {
      Set<TCDefReferable> result = null;
      loop:
      for (Referable element : namespace.getElements()) {
        if (!(element instanceof TCDefReferable defRef && defRef.getKind() == GlobalReferable.Kind.INSTANCE)) continue;
        for (ConcreteNamespaceCommand.NameHiding hiding : command.hidings()) {
          if (hiding.isStatic() && hiding.reference().getRefName().equals(defRef.getRefName())) continue loop;
        }
        boolean ok = command.isUsing();
        if (!ok) {
          for (ConcreteNamespaceCommand.NameRenaming renaming : command.renamings()) {
            if (renaming.isStatic() && renaming.reference().getRefName().equals(defRef.getRefName())) {
              ok = true;
              break;
            }
          }
        }
        if (ok) {
          if (result == null) result = new HashSet<>();
          result.add(defRef);
        }
      }
      return result == null ? Collections.emptySet() : result;
    }

    @NotNull List<ImportFinding> run() {
      if (!attributeUsages()) return Collections.emptyList();
      findHeads();
      Set<CommandInfo> live = computeLive();
      return report(live);
    }

    private boolean attributeUsages() {
      Set<LongName> recorded = myUsages.getGroupNames(myModule);
      if (!recorded.equals(myGroups.keySet())) return false;

      for (LongName longName : recorded) {
        GroupInfo group = myGroups.get(longName);
        ScopeUsage usage = myUsages.getUsage(myModule, longName);

        for (ScopeUsage.UsedName usedName : usage.names()) {
          if (usedName.command() == null) continue;
          CommandInfo info = myCommands.get(usedName.command());
          if (info == null) return false;
          if (resolvesWithoutCommand(group, usedName, info)) continue;
          info.usedNames.add(usedName.name());
          info.grounded = true;
        }

        for (String name : usage.unknownNames()) {
          keepProvidersOf(group, name);
        }

        boolean guessesAreUses = usage.inferenceFields() == null || !myInstancesComplete && !myInstancesTrusted;
        for (ScopeUsage.UsedName guessed : usage.guessedNames()) {
          if (guessed.command() == null) continue;
          if (!guessesAreUses && guessed.referable().isClassField() && !usage.inferenceFields().contains(guessed.referable())) continue;
          CommandInfo info = myCommands.get(guessed.command());
          if (info == null) return false;
          info.usedNames.add(guessed.name());
          info.grounded = true;
        }

        keepInstanceProviders(group, usage);
      }
      return true;
    }

    private void keepInstanceProviders(GroupInfo group, ScopeUsage usage) {
      Set<TCDefReferable> instances = usage.instances();
      boolean unknown = !myInstancesComplete && (instances == null || !myInstancesTrusted) && group.group.definition() instanceof Concrete.ResolvableDefinition;
      if (instances == null && !unknown) return;
      if (!myInstancesTrusted) instances = null;

      for (GroupInfo cur = group; cur != null; cur = cur.parent) {
        for (CommandInfo info : cur.commands) {
          if (unknown ? !info.instances.isEmpty() : !Collections.disjoint(info.instances, instances)) info.keepWhole();
        }
      }
    }

    private boolean resolvesWithoutCommand(GroupInfo group, ScopeUsage.UsedName usedName, CommandInfo info) {
      if (group.enclosingDynamicScope == null || info.namespace == null) return false;
      Referable referable = group.enclosingDynamicScope.resolveName(usedName.name(), Scope.ScopeContext.STATIC);
      if (referable == null || RedirectingReferable.getOriginalReferable(referable) != usedName.referable()) return false;
      return info.namespace.resolveNamespace(usedName.name()) == null || group.enclosingDynamicScope.resolveNamespace(usedName.name()) != null;
    }

    private void keepProvidersOf(GroupInfo group, String name) {
      for (GroupInfo cur = group; cur != null; cur = cur.parent) {
        for (CommandInfo info : cur.commands) {
          if (provides(info, name)) info.keepWhole();
        }
      }
    }

    private static boolean provides(CommandInfo info, String name) {
      if (info.command.isImport()) {
        List<String> path = info.command.module().getPath();
        if (!path.isEmpty() && path.getFirst().equals(name)) return true;
      }
      if (info.namespace == null) return false;
      return info.namespace.resolveName(name, null) != null || info.namespace.resolveNamespace(name) != null;
    }

    private void findHeads() {
      for (CommandInfo info : myAllCommands) {
        if (info.command.isImport()) continue;
        List<String> path = info.command.module().getPath();
        if (path.isEmpty()) continue;
        String head = path.getFirst();

        NamespaceCommandSink sink = new NamespaceCommandSink();
        Referable headReferable = info.group.namespaceScope.resolveName(head, Scope.ScopeContext.STATIC, sink);
        if (headReferable == null) continue;
        info.headName = head;
        info.headReferable = headReferable;
        if (sink.isKnown()) {
          CommandInfo provider = sink.getCommand() == null ? null : myCommands.get(sink.getCommand());
          if (provider != null) info.headProviders.add(provider);
        } else {
          for (GroupInfo cur = info.group; cur != null; cur = cur.parent) {
            for (CommandInfo other : cur.commands) {
              if (other != info && provides(other, head)) info.headProviders.add(other);
            }
          }
        }
      }
    }

    private Set<CommandInfo> computeLive() {
      Set<CommandInfo> live = Collections.newSetFromMap(new IdentityHashMap<>());
      live.addAll(myAllCommands);

      while (true) {
        Set<CommandInfo> next = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CommandInfo info : myAllCommands) {
          if (info.grounded) next.add(info);
        }
        for (CommandInfo info : live) {
          next.addAll(info.headProviders);
        }
        if (next.size() == live.size()) return live;
        live = next;
      }
    }

    private static class Frame {
      final String name;
      final GroupInfo group;
      final List<Frame> subgroups = new ArrayList<>();
      final Set<String> definitions = new HashSet<>();
      final Map<ImportedName, ModulePath> usages = new LinkedHashMap<>();
      final Set<FullName> usedInstances = new LinkedHashSet<>();

      Frame(String name, GroupInfo group) {
        this.name = name;
        this.group = group;
      }
    }

    private final Map<ModulePath, Set<ImportedName>> myFileImports = new LinkedHashMap<>();
    private final Map<GroupInfo, List<CommandInfo>> myHeadUsages = new HashMap<>();
    private boolean myInstancesKnown;

    @Nullable ImportStructure buildStructure(boolean soft) {
      if (soft) {
        if (!attributeUsages()) return null;
        findHeads();
        for (CommandInfo command : computeLive()) {
          if (command.headReferable != null) myHeadUsages.computeIfAbsent(command.group, k -> new ArrayList<>()).add(command);
        }
      }

      Frame root = buildFrame(myRoot, Collections.emptyList());
      if (root == null) return null;

      Set<ImportedName> fileImportNames = new HashSet<>();
      for (Set<ImportedName> names : myFileImports.values()) fileImportNames.addAll(names);
      merge(myFileImports, contract(root, fileImportNames, soft));

      ModulePath own = myModule.getModulePath();
      myFileImports.keySet().removeIf(path -> path.equals(own) || path.equals(Prelude.MODULE_PATH));
      return new ImportStructure(myFileImports, toStructure(root), myInstancesKnown);
    }

    private @Nullable Frame buildFrame(GroupInfo info, List<String> groupStack) {
      ConcreteGroup group = info.group;
      Frame frame = new Frame(group.isTopLevel() ? "" : group.referable().getRefName(), info);
      for (ConcreteGroup subgroup : subgroupsOf(group)) {
        frame.definitions.add(subgroup.referable().getRefName());
        for (InternalReferable field : subgroup.getFields()) {
          if (!ownerIsSkipped(field)) continue;
          frame.definitions.add(field.getRefName());
        }
      }

      List<String> innerStack = new ArrayList<>(groupStack);
      if (!frame.name.isEmpty()) innerStack.add(frame.name);

      ScopeUsage usage = myUsages.getUsage(myModule, group.referable().getRefLongName());
      for (ScopeUsage.UsedName used : usage.names()) {
        if (!addUsage(frame, innerStack, used.name(), used.referable())) return null;
      }
      for (CommandInfo command : myHeadUsages.getOrDefault(info, Collections.emptyList())) {
        if (!addUsage(frame, innerStack, command.headName, command.headReferable)) return null;
      }
      if (!usage.areInstancesKnown() && group.definition() != null) {
        myInstancesKnown = false;
      }
      if (usage.instances() != null) {
        for (TCDefReferable instance : usage.instances()) {
          FullName fullName = instance.getRefFullName();
          if (fullName.module == null) return null;
          frame.usedInstances.add(fullName);
        }
      }

      for (GroupInfo child : info.children) {
        Frame subframe = buildFrame(child, innerStack);
        if (subframe == null) return null;
        frame.subgroups.add(subframe);
      }
      return frame;
    }

    private static List<ConcreteGroup> subgroupsOf(ConcreteGroup group) {
      List<ConcreteGroup> result = new ArrayList<>();
      for (ConcreteStatement statement : group.statements()) {
        if (statement.group() != null) result.add(statement.group());
      }
      result.addAll(group.dynamicGroups());
      return result;
    }

    private boolean addUsage(Frame frame, List<String> groupStack, String writtenName, Referable referable) {
      if (!(referable instanceof LocatedReferable located)) return true;
      FullName fullName = located.getRefFullName();
      if (fullName.module == null) return false;
      List<String> longName = fullName.longName.toList();
      if (longName.isEmpty()) return false;

      String declared = longName.getLast();
      List<String> qualifier = longName.subList(0, Math.max(longName.size() - (ownerIsSkipped(referable) ? 2 : 1), 0));

      String alias = located.getAliasName();
      String renamed = writtenName.equals(declared) || writtenName.equals(alias) ? null : writtenName;
      String characteristic = renamed == null ? writtenName : declared;

      String fromFile = qualifier.isEmpty() ? characteristic : qualifier.getFirst();
      myFileImports.computeIfAbsent(fullName.module.getModulePath(), k -> new LinkedHashSet<>())
        .add(new ImportedName(fromFile, fromFile.equals(characteristic) ? renamed : null));
      if (!shorten(qualifier, groupStack).isEmpty() || renamed != null) {
        frame.usages.put(new ImportedName(characteristic, renamed), new ModulePath(qualifier));
      }
      return true;
    }

    private static boolean ownerIsSkipped(Referable referable) {
      if (!(referable instanceof GlobalReferable globalRef)) return false;
      if (globalRef.getKind().isConstructor()) return true;
      return globalRef.getKind() == GlobalReferable.Kind.FIELD
        && !(referable instanceof FieldReferable field && field.isParameterField());
    }

    private static List<String> shorten(List<String> path, List<String> currentPath) {
      for (int i = 0; i < currentPath.size(); i++) {
        if (i > path.size() - 1) return Collections.emptyList();
        if (!path.get(i).equals(currentPath.get(i))) return path.subList(i, path.size());
      }
      return path.subList(Math.min(currentPath.size(), path.size()), path.size());
    }

    private Map<ModulePath, Set<ImportedName>> contract(Frame frame, Set<ImportedName> fileImportNames, boolean soft) {
      Map<ModulePath, Set<ImportedName>> additional = new LinkedHashMap<>();
      for (Frame subgroup : frame.subgroups) merge(additional, contract(subgroup, fileImportNames, soft));

      Set<ImportedName> allInner = new LinkedHashSet<>();
      for (Frame subgroup : frame.subgroups) allInner.addAll(subgroup.usages.keySet());

      for (ImportedName identifier : allInner) {
        if (frame.usages.containsKey(identifier) && !soft) {
          for (Frame subgroup : frame.subgroups) subgroup.usages.remove(identifier);
        }
        if (frame.definitions.contains(identifier.visibleName()) || (frame.name.isEmpty() && fileImportNames.contains(identifier))) {
          if (!soft) {
            for (Frame subgroup : frame.subgroups) {
              if (Objects.equals(subgroup.usages.get(identifier), frame.usages.get(identifier))) subgroup.usages.remove(identifier);
            }
          }
          continue;
        }

        Set<ModulePath> paths = new LinkedHashSet<>();
        for (Frame subgroup : frame.subgroups) {
          ModulePath path = subgroup.usages.get(identifier);
          if (path != null) paths.add(path);
        }
        ModulePath here = frame.usages.get(identifier);
        if (here != null) paths.add(here);
        if (paths.size() != 1) continue;

        frame.usages.put(identifier, paths.iterator().next());
        if (!soft) {
          for (Frame subgroup : frame.subgroups) subgroup.usages.remove(identifier);
        }
      }

      for (Frame subgroup : frame.subgroups) frame.usedInstances.addAll(subgroup.usedInstances);
      for (FullName instance : myInstancesKnown ? frame.usedInstances : syntacticInstances(frame)) {
        List<String> longName = instance.longName.toList();
        if (longName.isEmpty()) continue;
        String refName = longName.getLast();
        List<String> qualifier = longName.subList(0, longName.size() - 1);
        ModulePath module = instance.module == null ? null : instance.module.getModulePath();
        if (module == null) continue;
        additional.computeIfAbsent(module, k -> new LinkedHashSet<>())
          .add(new ImportedName(qualifier.isEmpty() ? refName : qualifier.getFirst(), null));
        frame.usages.put(new ImportedName(refName, null),
          frame.name.isEmpty() || !qualifier.isEmpty() ? new ModulePath(qualifier) : module);
      }

      frame.subgroups.removeIf(subgroup -> subgroup.usages.isEmpty() && subgroup.subgroups.isEmpty());
      return additional;
    }

    private Set<FullName> syntacticInstances(Frame frame) {
      Set<FullName> result = new LinkedHashSet<>();
      for (CommandInfo command : frame.group.commands) {
        for (TCDefReferable instance : command.instances) result.add(instance.getRefFullName());
      }
      return result;
    }

    private static void merge(Map<ModulePath, Set<ImportedName>> target, Map<ModulePath, Set<ImportedName>> source) {
      for (Map.Entry<ModulePath, Set<ImportedName>> entry : source.entrySet()) {
        target.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>()).addAll(entry.getValue());
      }
    }

    private static ImportStructure.Group toStructure(Frame frame) {
      Map<ModulePath, Set<ImportedName>> byModule = new LinkedHashMap<>();
      for (Map.Entry<ImportedName, ModulePath> entry : frame.usages.entrySet()) {
        byModule.computeIfAbsent(entry.getValue(), k -> new LinkedHashSet<>()).add(entry.getKey());
      }
      List<ImportStructure.Group> subgroups = new ArrayList<>(frame.subgroups.size());
      for (Frame subgroup : frame.subgroups) subgroups.add(toStructure(subgroup));
      return new ImportStructure.Group(frame.name, subgroups, byModule);
    }

    private List<ImportFinding> report(Set<CommandInfo> live) {
      for (CommandInfo info : live) {
        for (CommandInfo provider : info.headProviders) provider.usedNames.add(info.headName);
      }

      List<ImportFinding> result = new ArrayList<>();
      for (CommandInfo info : myAllCommands) {
        if (!live.contains(info)) {
          result.add(ImportFinding.ofCommand(info.command));
          continue;
        }
        if (info.opaque || info.command.isUsing()) continue;
        for (ConcreteNamespaceCommand.NameRenaming renaming : info.command.renamings()) {
          String newName = renaming.newName();
          if (!info.usedNames.contains(newName != null ? newName : renaming.reference().getRefName())) {
            result.add(ImportFinding.ofName(info.command, renaming));
          }
        }
      }
      return result;
    }
  }
}
