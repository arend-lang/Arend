package org.arend.server.impl;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.naming.reference.*;
import org.arend.naming.scope.EmptyScope;
import org.arend.naming.scope.Scope;
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
import java.util.function.Function;

import static java.util.Collections.singletonList;

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
     * This method modifies the state of the {@code SingleFileReferenceResolver}.
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

        Map<ModulePath, List<LocatedReferable>> refMap = new HashMap<>();

        ModuleLocation module = targetReferable.getLocation();
        if (module == null) {
            myErrorReporter.report(LocationError.definition(targetReferable, null));
            return null;
        }

        ModuleLocation found = myServer.findModule(module.getModulePath(), module.getLibraryName(), anchorModuleLocation.getLocationKind() == ModuleLocation.LocationKind.TEST, true);
        if (!module.equals(found)) {
            myErrorReporter.report(LocationError.definition(null, module.getModulePath()));
        }

        refMap.computeIfAbsent(module.getModulePath(), m -> new ArrayList<>()).add(targetReferable);

        Scope referableScope = myServer.getReferableScope(anchor.parent());
        Scope currentScope = referableScope == null ?
          EmptyScope.INSTANCE :
          referableScope;
        Set<? extends Referable> currentScopeElements = new LinkedHashSet<>(currentScope.getElements());
        Set<RedirectingReferable> redirectingReferables = new LinkedHashSet<>();
        Set<Referable> targetReferables = new LinkedHashSet<>();

        for (Referable currentScopeElement : currentScopeElements) if (currentScopeElement instanceof RedirectingReferable redirectingReferable) {
            redirectingReferables.add(redirectingReferable);
            targetReferables.add(redirectingReferable.getOriginalReferable());
        } else {
            targetReferables.add(currentScopeElement);
        }

        Function<Referable, Set<String>> aliasCalculator = referable -> {
            Set<String> candidates = getPossibleAliasNames(referable, redirectingReferables);

            Set<String> result = new HashSet<>();
            for (String candidate : candidates) {
                Referable resolveTarget = currentScope.resolveName(candidate);
                if (resolveTarget instanceof RedirectingReferable redirecting) resolveTarget = redirecting.getOriginalReferable();
                if (resolveTarget == referable) result.add(candidate);
            }
            return result;
        };


        ModuleLocation targetModuleLocation = targetReferable.getLocation();
        if (targetModuleLocation == null) return null;
        ConcreteGroup targetModuleFile = myServer.getRawGroup(targetModuleLocation);
        List<Referable> targetModuleDefinitions = new LinkedList<>();
        if (targetModuleFile != null) for (ConcreteStatement statement : targetModuleFile.statements()) {
            ConcreteGroup group = statement.group();
            if (group != null) targetModuleDefinitions.add(group.referable());
        }

        AtomicReference<ConcreteNamespaceCommand> namespaceCommand = getConcreteNamespaceCommandAtomicReference(myCurrentFile, targetReferable, anchorModuleLocation);
        ArrayList<LocatedReferable> targetAncestors = new ArrayList<>();
        LocatedReferable.Helper.getAncestors(targetReferable, targetAncestors);
        boolean someAncestorIsVisible = targetAncestors.stream().anyMatch(targetReferables::contains);

        @Nullable ModulePath modulePrefix = null;
        @Nullable List<Pair<String, Referable>> result;

        if (namespaceCommand.get() != null || someAncestorIsVisible ||
                anchorModuleLocation.equals(targetModuleLocation)) {
            List<List<Pair<String, Referable>>> possibleNames = calculatePossibleNames(targetReferable, aliasCalculator);
            List<List<Pair<String, Referable>>> accessibleNames = new ArrayList<>();

            for (List<Pair<String, Referable>> name : possibleNames) {
                Referable resolveResult = Scope.resolveName(currentScope, name.stream().map(p -> p.proj1).toList());
                if (resolveResult instanceof RedirectingReferable redirecting) resolveResult = redirecting.getOriginalReferable();
                if (resolveResult == targetReferable) accessibleNames.add(name);
            }

            List<List<Pair<String, Referable>>> importableNames =
                    possibleNames.stream().filter(name ->
                            currentScope.resolveName(name.getFirst().proj1) == null).toList();
            boolean needsToImport = true;

            if (!importableNames.isEmpty() &&
                    importableNames.getFirst().getFirst().proj2 == targetReferable &&
                    namespaceCommand.get() != null) { // Our opportunity to import the target referable with a short name
                result = importableNames.getFirst();
            } else if (accessibleNames.isEmpty()) { // No parent of targetReferable is visible in current scope
                result = importableNames.isEmpty() ? null : importableNames.getFirst();

                if (result == null) { // scopes are obstructed -- use very long name (canonical name of the definition + module path)
                    modulePrefix = targetModuleLocation.getModulePath();
                    result = possibleNames.getLast();
                }
            } else {
                result = accessibleNames.getFirst();
                needsToImport = false;
            }

            if (needsToImport) {
                final Referable topLevelReferable = result.getFirst().proj2;
                boolean topLevelReferableIsProtected = topLevelReferable instanceof LocatedReferable locatedReferable &&
                        locatedReferable.getAccessModifier() == AccessModifier.PROTECTED;
                String refName = result.getFirst().proj1;

                ConcreteNamespaceCommand cmd = namespaceCommand.get();
                if (cmd != null) {
                    boolean topLevelNameImported = (cmd.isUsing() && !topLevelReferableIsProtected || cmd.renamings().stream().anyMatch(nameRenaming ->
                            nameRenaming.reference().getRefName().equals(topLevelReferable.getRefName()) ||
                                    topLevelReferable instanceof LocatedReferable locatedReferable && nameRenaming.reference().getRefName().equals(locatedReferable.getAliasName())))
                            && cmd.hidings().stream().noneMatch(nameHiding -> nameHiding.reference().getRefName().equals(topLevelReferable.getRefName())
                    );

                    if (topLevelNameImported) { // Something is wrong -- import is needed, but at the same time the ambient definition is already imported; perhaps scopes are obstructed?
                        modulePrefix = targetModuleLocation.getModulePath();
                        result = possibleNames.getLast();
                    } else if (modulePrefix == null) {
                        itemsToAdd.computeIfAbsent(cmd, c -> new HashSet<>()).add(refName);
                    }
                } else if (Prelude.MODULE_LOCATION.equals(targetModuleLocation)) {
                    importsToAdd.put(targetModuleLocation, null);
                }
            }
        } else {
            List<List<Pair<String, Referable>>> possibleNames = calculatePossibleNames(targetReferable, null);
            List<List<Pair<String, Referable>>> importableNames =
              possibleNames.stream().filter(name ->
                currentScope.resolveName(name.getFirst().proj1) == null).toList();
            boolean scopeObstructed = false;

            if (importableNames.isEmpty()) {
              result = possibleNames.getLast(); // canonical name
              scopeObstructed = true;
            } else {
              result = importableNames.getFirst();
            }

            String topLevelName = result.getFirst().proj1;
            boolean topLevelReferableIsProtected = result.getFirst().proj2 instanceof LocatedReferable locatedReferable && locatedReferable.getAccessModifier() == AccessModifier.PROTECTED;

            boolean nonEmptyScopeIntersection = (!Prelude.MODULE_LOCATION.equals(targetModuleLocation) &&
                    targetModuleDefinitions.stream().anyMatch(stat ->
                            currentScope.resolveName(stat.getRefName()) != null
                    ));

            if (scopeObstructed) {
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
                        importsToAdd.put(targetModuleLocation, new HashSet<>(singletonList(topLevelName)));
                } else if (!Prelude.MODULE_LOCATION.equals(targetModuleLocation) || scopeObstructed)
                    importsToAdd.put(targetModuleLocation, null);
            }
        }

        return new Pair<>(modulePrefix, result);
    }

    public @Nullable RawModifier getModifier() {
        if (myCurrentFile == null) return null;
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

    public void reset() {
        itemsToAdd.clear();
        importsToAdd.clear();
    }

    private static @NotNull Set<String> getPossibleAliasNames(Referable referable, Set<RedirectingReferable> redirectingReferables) {
        Set<String> candidates = new HashSet<>();
        for (RedirectingReferable redirectingReferable : redirectingReferables) {
            if (redirectingReferable.getOriginalReferable() == referable) {
                candidates.add(redirectingReferable.getRefName());
            }
        }
        candidates.add(referable.getRefName());
        if (referable instanceof GlobalReferable globalReferable && globalReferable.getAliasName() != null)
            candidates.add(globalReferable.getAliasName());
        return candidates;
    }

    private static List<List<Pair<String, Referable>>> calculatePossibleNames(
      LocatedReferable targetReferable,
      @Nullable Function<Referable, Set<String>> currentScopeMap) {
      List<List<Pair<String, Referable>>> possibleNames = singletonList(new ArrayList<>());

      boolean foundNameInScope = false;
      boolean internalReferableFlag = false;
      @Nullable LocatedReferable parent;
      LocatedReferable currReferable;
      parent = targetReferable;

      do {
        currReferable = parent;
        parent = currReferable.getLocatedReferableParent();
        List<List<Pair<String, Referable>>> possibleNamesPrefix = new ArrayList<>();

        if (internalReferableFlag) {
          internalReferableFlag = false;
          possibleNamesPrefix.add(new ArrayList<>()); // allow referencing to a constructor or a classfield without referring to the parent class/datatype
        }

        @Nullable Set<String> aliases = currentScopeMap != null ? currentScopeMap.apply(currReferable) : null;
        ArrayList<String> currReferableAliases = new ArrayList<>();
        if (aliases != null) currReferableAliases.addAll(aliases);
        currReferableAliases.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));

        if (!currReferableAliases.isEmpty()) { // we are calculating first name in a LongName -- the name may be affected by namespace commands
          final Referable currReferableFinal = currReferable;
          possibleNamesPrefix.addAll(currReferableAliases.stream().map(name -> singletonList(new Pair<>(name, currReferableFinal))).toList());
          foundNameInScope = true;
        } else { // we are calculating middle name in a LongName -- it is either refName or aliasName -- not affected by namespace commands
          if (currReferable.hasAlias())
            possibleNamesPrefix.add(singletonList(new Pair<>(currReferable.getAliasName(), currReferable)));
          possibleNamesPrefix.add(singletonList(new Pair<>(currReferable.getRefName(), currReferable)));
        }

        if (currReferable instanceof InternalReferable &&
          !(currReferable instanceof FieldReferable fieldReferable && fieldReferable.isParameterField()) &&
          parent != null && !(parent instanceof ModuleReferable)) {
          internalReferableFlag = true;
        }

        possibleNames = cartesianConcat(possibleNamesPrefix, possibleNames);
      } while (parent != null && !(parent instanceof ModuleReferable) && !foundNameInScope);

      return possibleNames;
    }


    private static <T> List<List<T>> cartesianConcat(List<List<T>> list1, List<List<T>> list2) {
        List<List<T>> result = new ArrayList<>();
        for (List<T> sub1 : list1) {
            for (List<T> sub2 : list2) {
                List<T> combined = new ArrayList<>(sub1.size() + sub2.size());
                combined.addAll(sub1);
                combined.addAll(sub2);
                result.add(combined);
            }
        }
        return result;
    }
}
