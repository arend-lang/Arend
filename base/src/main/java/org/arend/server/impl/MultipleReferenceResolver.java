package org.arend.server.impl;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.naming.reference.*;
import org.arend.naming.scope.MergeScope;
import org.arend.naming.scope.Scope;
import org.arend.naming.scope.local.ListScope;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.RawAnchor;
import org.arend.server.modifier.RawImportAdder;
import org.arend.server.modifier.RawImportRemover;
import org.arend.server.modifier.RawModifier;
import org.arend.server.modifier.RawSequenceModifier;
import org.arend.source.error.LocationError;
import org.arend.term.concrete.LocalVariablesCollector;
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class MultipleReferenceResolver {
    ArendServer myServer;
    @NotNull ErrorReporter myErrorReporter;
    @NotNull RawAnchor myAnchor;
    @Nullable ConcreteGroup myCurrentFile;
    HashMap<ConcreteNamespaceCommand, @Nullable HashSet<String>> itemsToAdd = new HashMap<>();
    HashMap<ModuleLocation, @Nullable HashSet<String>> importsToAdd = new HashMap<>();

    public MultipleReferenceResolver(@NotNull ArendServer server,
                                     @NotNull ErrorReporter errorReporter,
                                     @NotNull RawAnchor anchor,
                                     @Nullable ConcreteGroup currentFile) {
        myServer = server;
        myErrorReporter = errorReporter;
        myAnchor = anchor;
        myCurrentFile = currentFile;
    }


    public @Nullable LongName makeTargetAvailable(@NotNull LocatedReferable referable) {
        // Check that referables are located in available modules and collect them in refMap
        ModuleLocation anchorModuleLocation = myAnchor.parent().getLocation();

        if (anchorModuleLocation == null) {
            myErrorReporter.report(LocationError.definition(myAnchor.parent(), null));
            return null;
        }

        Map<ModulePath, List<LocatedReferable>> refMap = new HashMap<>();

        ModuleLocation module = referable.getLocation();
        if (module == null) {
            myErrorReporter.report(LocationError.definition(referable, null));
            return null;
        }

        ModuleLocation found = myServer.findModule(module.getModulePath(), anchorModuleLocation.getLibraryName(), anchorModuleLocation.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
        if (!module.equals(found)) {
            myErrorReporter.report(LocationError.definition(null, module.getModulePath()));
        }

        refMap.computeIfAbsent(module.getModulePath(), m -> new ArrayList<>()).add(referable);


        // Calculate the set of local referables
        List<Referable> localReferables = new ArrayList<>();
        if (myAnchor.data() != null && myAnchor.parent() instanceof TCDefReferable tcRef) {
            DefinitionData definitionData = myServer.getResolvedDefinition(tcRef);
            if (definitionData != null) {
                localReferables = LocalVariablesCollector.getLocalReferables(definitionData.definition(), myAnchor.data());
            }
        }

        Scope referableScope = myServer.getReferableScope(myAnchor.parent());
        Scope currentScope = referableScope == null ? new ListScope(localReferables) : new MergeScope(new ListScope(localReferables), referableScope);
        Collection<? extends Referable> currentScopeElements = currentScope.getElements();
        HashMap<Referable, String> currentScopeMap = new HashMap<>();
        for (Referable currentScopeElement : currentScopeElements) {
            if (currentScopeElement instanceof RedirectingReferable)
                currentScopeMap.put(((RedirectingReferable) currentScopeElement).getOriginalReferable(), currentScopeElement.getRefName());
            currentScopeMap.put(currentScopeElement, currentScopeElement.getRefName());
        }


        ModuleLocation targetModuleLocation = referable.getLocation();
        ConcreteGroup targetModuleFile = targetModuleLocation != null ? myServer.getRawGroup(targetModuleLocation) : null;
        List<Referable> targetModuleDefinitions = new LinkedList<>();
        if (targetModuleFile != null) for (ConcreteStatement statement : targetModuleFile.statements()) {
            ConcreteGroup group = statement.group();
            if (group != null) targetModuleDefinitions.add(group.referable());
        }

        AtomicReference<ConcreteNamespaceCommand> namespaceCommand = getConcreteNamespaceCommandAtomicReference(myCurrentFile, referable, anchorModuleLocation);

        boolean nonEmptyScopeIntersection = (!Prelude.MODULE_LOCATION.equals(targetModuleLocation) &&
                targetModuleDefinitions.stream().anyMatch(stat -> currentScope.resolveName(stat.getRefName()) != null));
        List<String> calculatedName = new ArrayList<>();

        if (namespaceCommand.get() != null || anchorModuleLocation.equals(targetModuleLocation)) {
            LocatedReferable currReferable;
            LocatedReferable parent = referable;
            boolean foundNameInScope = false;
            String contextName;

            do {
                currReferable = parent;
                parent = currReferable.getLocatedReferableParent();

                contextName = currentScopeMap.get(currReferable);
                if (contextName != null) {
                    Referable resolveResult = currentScope.resolveName(contextName);
                    if (resolveResult instanceof RedirectingReferable redirecting) resolveResult = redirecting.getOriginalReferable();

                    if (resolveResult == currReferable) {
                        calculatedName.addFirst(contextName);
                        foundNameInScope = true;
                        break;
                    } else if (currReferable.getAliasName() != null && currentScope.resolveName(currReferable.getAliasName()) == currReferable) {
                        calculatedName.addFirst(currReferable.getAliasName());
                        foundNameInScope = true;
                        break;
                    }
                }

                if (currReferable instanceof InternalReferable &&
                        !(currReferable instanceof FieldReferable fieldReferable && fieldReferable.isParameterField()) &&
                        parent != null && !(parent instanceof ModuleReferable)) {
                    parent = parent.getLocatedReferableParent();
                }

                calculatedName.addFirst(currReferable.getRefName());
            } while (parent != null && !(parent instanceof ModuleReferable));

            final LocatedReferable topLevelReferable = currReferable;
            boolean topLevelReferableIsProtected = topLevelReferable.getAccessModifier() == AccessModifier.PROTECTED;

            if (contextName == null && !calculatedName.isEmpty()) contextName = calculatedName.getFirst();

            boolean scopeObstructed = !foundNameInScope && contextName != null && currentScope.resolveName(contextName) != null;

            if (scopeObstructed && targetModuleLocation != null) {
                calculatedName.addAll(0, targetModuleLocation.getModulePath().toList());
            }

            ConcreteNamespaceCommand cmd = namespaceCommand.get();
            if (cmd != null) {
                boolean topLevelNameImported = (cmd.isUsing() && !topLevelReferableIsProtected || cmd.renamings().stream().anyMatch(nameRenaming ->
                        nameRenaming.reference().getRefName().equals(topLevelReferable.getRefName()) ||
                                nameRenaming.reference().getRefName().equals(topLevelReferable.getAliasName()))) && cmd.hidings().stream().noneMatch(nameHiding ->
                        nameHiding.reference().getRefName().equals(topLevelReferable.getRefName())
                );

                if (!foundNameInScope && !topLevelNameImported && !scopeObstructed && contextName != null) {
                    itemsToAdd.computeIfAbsent(cmd, c -> new HashSet<>()).add(contextName);
                }
            }
        } else {
            LocatedReferable currReferable;
            LocatedReferable parent = referable;
            do {
                currReferable = parent;

                if (currReferable.getAliasName() != null)
                    calculatedName.addFirst(currReferable.getAliasName()); else
                    calculatedName.addFirst(currReferable.getRefName());

                parent = currReferable.getLocatedReferableParent();

                if (currReferable instanceof InternalReferable &&
                        !(currReferable instanceof FieldReferable fieldReferable && fieldReferable.isParameterField()) &&
                        parent != null && !(parent instanceof ModuleReferable)) {
                    parent = parent.getLocatedReferableParent();
                }
            } while (parent != null && !(parent instanceof ModuleReferable));

            Referable referableInScope = currentScope.resolveName(currReferable.getRefName());
            String topLevelName = calculatedName.getFirst();
            boolean topLevelReferableIsProtected = currReferable.getAccessModifier() == AccessModifier.PROTECTED;
            boolean scopeObstructed = referableInScope != null && referableInScope != currReferable;

            if (scopeObstructed && targetModuleLocation != null) {
                calculatedName.addAll(0, targetModuleLocation.getModulePath().toList());
            }
            if (itemsToAdd.containsKey(namespaceCommand.get())) {
                HashSet<String> individualImports = itemsToAdd.get(namespaceCommand.get());
                if (individualImports != null) individualImports.add(topLevelName);
            } else if (importsToAdd.containsKey(targetModuleLocation)) {
                HashSet<String> individualImports = importsToAdd.get(targetModuleLocation);
                if (individualImports != null) individualImports.add(topLevelName);
            } else {
                if (topLevelReferableIsProtected || nonEmptyScopeIntersection) {
                    if (scopeObstructed)
                        importsToAdd.put(targetModuleLocation, new HashSet<>());
                    else
                        importsToAdd.put(targetModuleLocation, new HashSet<>(Collections.singletonList(topLevelName)));
                } else if (!Prelude.MODULE_LOCATION.equals(targetModuleLocation) || scopeObstructed)
                    importsToAdd.put(targetModuleLocation, null);
            }
        }

        return new LongName(calculatedName);
    }

    public RawModifier getModifier() {
        ModuleLocation anchorModuleLocation = myAnchor.parent().getLocation();

        List<RawModifier> result = new ArrayList<>();
        for (Map.Entry<ConcreteNamespaceCommand, HashSet<String>> entry : itemsToAdd.entrySet()) {
            result.add(new RawImportRemover(entry.getKey()));
            HashSet<String> names = entry.getValue();
            ArrayList<ConcreteNamespaceCommand.NameHiding> hidings = new ArrayList<>(entry.getKey().hidings().stream().filter(nameHiding -> {
                String hidingName = nameHiding.reference().getRefName();
                if (names.contains(hidingName)) {
                    names.remove(hidingName);
                    return false;
                }
                return true;
            }).toList());
            ArrayList<ConcreteNamespaceCommand.NameRenaming> renamings = new ArrayList<>(entry.getKey().renamings());
            renamings.addAll(names.stream().map(name ->
                    new ConcreteNamespaceCommand.NameRenaming(null, Scope.ScopeContext.STATIC, new NamedUnresolvedReference(null, name), null, null)).toList());

            renamings.sort(Comparator.comparing(nameRenaming -> nameRenaming.reference().getRefName()));

            ConcreteNamespaceCommand newCommand = new ConcreteNamespaceCommand(null, true, entry.getKey().module(), entry.getKey().isUsing(), renamings, hidings);
            result.add(new RawImportAdder(newCommand));
        }

        for (Map.Entry<ModuleLocation, HashSet<String>> entry : importsToAdd.entrySet()) if (!entry.getKey().equals(anchorModuleLocation)) {
            ArrayList<ConcreteNamespaceCommand.NameRenaming> renamings;
            HashSet<String> individualImports = entry.getValue();
            if (individualImports != null) renamings = new ArrayList<>(individualImports.stream().map(name ->
                    new ConcreteNamespaceCommand.NameRenaming(null, Scope.ScopeContext.STATIC, new NamedUnresolvedReference(null, name), null, null)).toList());
            else renamings = null;

            if (renamings != null) renamings.sort(Comparator.comparing(nameRenaming -> nameRenaming.reference().getRefName()));

            ConcreteNamespaceCommand newCommand = new ConcreteNamespaceCommand(null, true,
                    new LongUnresolvedReference(null, null, entry.getKey().getModulePath().toList()), renamings == null, renamings != null ? renamings : new ArrayList<>(), new ArrayList<>());
            result.add(new RawImportAdder(newCommand));
        }

        return new RawSequenceModifier(result);
    }

    private @NotNull AtomicReference<ConcreteNamespaceCommand> getConcreteNamespaceCommandAtomicReference(@Nullable ConcreteGroup currentFile, LocatedReferable referable, ModuleLocation anchorModule) {
        AtomicReference<ConcreteNamespaceCommand> namespaceCommand = new AtomicReference<>();

        if (currentFile != null) currentFile.traverseGroup(subgroup -> subgroup.statements().forEach(statement -> {
            ConcreteNamespaceCommand command = statement.command();
            if (command != null && command.isImport()) {
                boolean isPrelude = Prelude.MODULE_PATH.toList().equals(command.module().getPath());

                ModuleLocation commandTarget = isPrelude ? Prelude.MODULE_LOCATION : myServer.findModule(new ModulePath(command.module().getPath()),
                        anchorModule.getLibraryName(), anchorModule.getLocationKind() == ModuleLocation.LocationKind.TEST, true);

                if (commandTarget != null && commandTarget.equals(referable.getLocation())) {
                    namespaceCommand.set(command);
                }
            }
        }));

        return namespaceCommand;
    }
}
