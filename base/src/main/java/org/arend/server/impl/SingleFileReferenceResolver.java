package org.arend.server.impl;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
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
import org.arend.term.group.AccessModifier;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class SingleFileReferenceResolver {
    ArendServer myServer;
    @NotNull ErrorReporter myErrorReporter;
    @Nullable ConcreteGroup myCurrentFile;
    HashMap<ConcreteNamespaceCommand, @Nullable HashSet<String>> itemsToAdd = new HashMap<>();
    HashMap<ModuleLocation, @Nullable HashSet<String>> importsToAdd = new HashMap<>();

    public SingleFileReferenceResolver(@NotNull ArendServer server,
                                       @NotNull ErrorReporter errorReporter,
                                       @Nullable ModuleLocation currentFile) {
        myServer = server;
        myErrorReporter = errorReporter;
        myCurrentFile = (currentFile != null) ? server.getRawGroup(currentFile) : null;

    }

    public SingleFileReferenceResolver(@NotNull ArendServer server,
                                       @NotNull ErrorReporter errorReporter,
                                       @Nullable ConcreteGroup currentFile) {
        myServer = server;
        myErrorReporter = errorReporter;
        myCurrentFile = currentFile;
    }

    public @Nullable ModuleLocation getModuleLocation() {
        if (myCurrentFile == null) return null;
        return myCurrentFile.referable().getLocation();

    }

    public @Nullable ConcreteGroup getCurrentFile() {
        return myCurrentFile;
    }


    /**
     * Calculates the long name of the given referable in the context of the provided anchor.
     * This method modifies the state of the {@code MultipleReferenceResolver}.
     *
     * @param referable the referable whose long name is to be calculated; must not be null.
     * @param anchor the context in which the referable's long name is calculated; must not be null.
     * @return the calculated long name as a {@code LongName} object, or null if the calculation fails.
     */
    public @Nullable LongName calculateLongName(@NotNull LocatedReferable referable, @NotNull RawAnchor anchor) {
        Pair<@Nullable ModulePath, List<Pair<String, Referable>>> result = makeTargetAvailable(referable, anchor);
        List<String> longName = new ArrayList<>();
        if (result == null) return null;
        if (result.proj1 != null) longName.addAll(result.proj1.toList());
        longName.addAll(result.proj2.stream().map(pair -> pair.proj1).toList());
        return new LongName(longName);
    }

    public @Nullable Pair<@Nullable ModulePath, List<Pair<String, Referable>>> makeTargetAvailable(@NotNull LocatedReferable targetReferable, @NotNull RawAnchor anchor) {
        // Check that referables are located in available modules and collect them in refMap
        ModuleLocation anchorModuleLocation = anchor.parent().getLocation();
        ModuleLocation currentFileLocation = (myCurrentFile != null) ? myCurrentFile.referable().getLocation(): null;

        if (anchorModuleLocation != null && currentFileLocation != null && !anchorModuleLocation.equals(currentFileLocation))
            throw new IllegalStateException();

        if (anchorModuleLocation == null) {
            myErrorReporter.report(LocationError.definition(anchor.parent(), null));
            return null;
        }

        ConcreteGroup anchorModuleGroup = myServer.getRawGroup(anchorModuleLocation);

        Map<ModulePath, List<LocatedReferable>> refMap = new HashMap<>();

        ModuleLocation module = targetReferable.getLocation();
        if (module == null) {
            myErrorReporter.report(LocationError.definition(targetReferable, null));
            return null;
        }

        ModuleLocation found = myServer.findModule(module.getModulePath(), anchorModuleLocation.getLibraryName(), anchorModuleLocation.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
        if (!module.equals(found)) {
            myErrorReporter.report(LocationError.definition(null, module.getModulePath()));
        }

        refMap.computeIfAbsent(module.getModulePath(), m -> new ArrayList<>()).add(targetReferable);

        // Calculate the set of dynamic referables of the ambient classes
        List<Referable> additionalDynamicReferables = new ArrayList<>();
        @Nullable LocatedReferable parent = anchor.parent();
        while (parent != null && !(parent instanceof ModuleReferable)) {
            if (parent.getKind() == GlobalReferable.Kind.CLASS) {
                AtomicReference<ConcreteGroup> classGroupReference = new AtomicReference<>();
                final LocatedReferable classReferable = parent;
                if (anchorModuleGroup != null) anchorModuleGroup.traverseGroup(concreteGroup -> {
                    if (concreteGroup.referable() == classReferable)
                        classGroupReference.set(concreteGroup);
                });
                @Nullable ConcreteGroup classGroup = classGroupReference.get();
                if (classGroup != null)
                    additionalDynamicReferables.addAll(classGroup.dynamicGroups().stream().map(ConcreteGroup::referable).toList());
            }
            additionalDynamicReferables.add(parent);
            parent = parent.getLocatedReferableParent();
        }

        Scope referableScope = myServer.getReferableScope(anchor.parent());
        Scope currentScope = referableScope == null ? new ListScope(additionalDynamicReferables) : new MergeScope(new ListScope(additionalDynamicReferables), referableScope);
        Set<? extends Referable> currentScopeElements = new HashSet<>(currentScope.getElements());
        HashMap<Referable, String> currentScopeMap = new HashMap<>();
        for (Referable currentScopeElement : currentScopeElements) {
            if (currentScopeElement instanceof RedirectingReferable)
                currentScopeMap.put(((RedirectingReferable) currentScopeElement).getOriginalReferable(), currentScopeElement.getRefName());
            currentScopeMap.put(currentScopeElement, currentScopeElement.getRefName());
        }

        ModuleLocation targetModuleLocation = targetReferable.getLocation();
        ConcreteGroup targetModuleFile = targetModuleLocation != null ? myServer.getRawGroup(targetModuleLocation) : null;
        List<Referable> targetModuleDefinitions = new LinkedList<>();
        if (targetModuleFile != null) for (ConcreteStatement statement : targetModuleFile.statements()) {
            ConcreteGroup group = statement.group();
            if (group != null) targetModuleDefinitions.add(group.referable());
        }

        AtomicReference<ConcreteNamespaceCommand> namespaceCommand = getConcreteNamespaceCommandAtomicReference(myCurrentFile, targetReferable, anchorModuleLocation);
        ArrayList<LocatedReferable> targetAncestors = new ArrayList<>();
        LocatedReferable.Helper.getAncestors(targetReferable, targetAncestors);
        Boolean someAncestorIsVisible = targetAncestors.stream().anyMatch(currentScopeElements::contains);

        boolean nonEmptyScopeIntersection = (!Prelude.MODULE_LOCATION.equals(targetModuleLocation) &&
                targetModuleDefinitions.stream().anyMatch(stat -> currentScope.resolveName(stat.getRefName()) != null));

        List<Pair<String, Referable>> calculatedName = new ArrayList<>();
        @Nullable ModulePath modulePrefix = null;
        @Nullable List<Pair<String, Referable>> alternativeName = (targetReferable instanceof InternalReferable) ? new ArrayList<>() : null; // Name which uses the name of parent class/datatype

        LocatedReferable currReferable;
        @Nullable LocatedReferable alternativeReferable = null;
        parent = targetReferable;

        if (namespaceCommand.get() != null || someAncestorIsVisible ||
                anchorModuleLocation.equals(targetModuleLocation)) {
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
                        calculatedName.addFirst(new Pair<>(contextName, resolveResult));
                        foundNameInScope = true;
                        break;
                    } else if (currReferable.getAliasName() != null && currentScope.resolveName(currReferable.getAliasName()) == currReferable) {
                        calculatedName.addFirst(new Pair<>(currReferable.getAliasName(), currReferable));
                        foundNameInScope = true;
                        break;
                    }
                }

                if (currReferable instanceof InternalReferable &&
                        !(currReferable instanceof FieldReferable fieldReferable && fieldReferable.isParameterField()) &&
                        parent != null && !(parent instanceof ModuleReferable)) {
                    parent = parent.getLocatedReferableParent();
                }

                if (currReferable.hasAlias())
                    calculatedName.addFirst(new Pair<>(currReferable.getAliasName(), currReferable)); else
                        calculatedName.addFirst(new Pair<>(currReferable.getRefName(), currReferable));
            } while (parent != null && !(parent instanceof ModuleReferable));

            final LocatedReferable topLevelReferable = currReferable;
            boolean topLevelReferableIsProtected = topLevelReferable.getAccessModifier() == AccessModifier.PROTECTED;

            if (contextName == null && !calculatedName.isEmpty()) contextName = calculatedName.getFirst().proj1;

            boolean scopeObstructed = !foundNameInScope && contextName != null && currentScope.resolveName(contextName) != null;

            if (scopeObstructed && targetModuleLocation != null) {
                modulePrefix = targetModuleLocation.getModulePath();
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
            do {
                currReferable = parent;

                if (currReferable.getAliasName() != null) {
                    calculatedName.addFirst(new Pair<>(currReferable.getAliasName(), currReferable));
                    if (alternativeName != null) alternativeName.addFirst(new Pair<>(currReferable.getAliasName(), currReferable));
                } else {
                    calculatedName.addFirst(new Pair<>(currReferable.getRefName(), currReferable));
                    if (alternativeName != null) alternativeName.addFirst(new Pair<>(currReferable.getRefName(), currReferable));
                }

                parent = currReferable.getLocatedReferableParent();

                if (currReferable instanceof InternalReferable &&
                        !(currReferable instanceof FieldReferable fieldReferable && fieldReferable.isParameterField()) &&
                        parent != null && !(parent instanceof ModuleReferable)) {
                    if (alternativeName != null) {
                        alternativeName.addFirst(new Pair<>(parent.getRefName(), parent));
                        alternativeReferable = parent;
                    }
                    parent = parent.getLocatedReferableParent();
                }
            } while (parent != null && !(parent instanceof ModuleReferable));

            Referable referableInScope = currentScope.resolveName(currReferable.getRefName());
            @Nullable Referable alternativeReferableInScope = (alternativeReferable != null) ? currentScope.resolveName(alternativeReferable.getRefName()) : null;

            String topLevelName = calculatedName.getFirst().proj1;
            boolean topLevelReferableIsProtected = currReferable.getAccessModifier() == AccessModifier.PROTECTED;
            boolean scopeObstructed = false;

            if (referableInScope != null && referableInScope != currReferable) {
                scopeObstructed = alternativeReferableInScope == null || alternativeReferableInScope != alternativeReferable;
                if (alternativeName != null) calculatedName = alternativeName;
            }

            if (scopeObstructed && targetModuleLocation != null) {
                modulePrefix = targetModuleLocation.getModulePath();
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

        return new Pair<>(modulePrefix, calculatedName);
    }

    public RawModifier getModifier() {
        ModuleLocation anchorModuleLocation = myCurrentFile.referable().getLocation();

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
