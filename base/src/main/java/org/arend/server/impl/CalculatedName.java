package org.arend.server.impl;

import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.ModuleReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.scope.EmptyScope;
import org.arend.naming.scope.Scope;
import org.arend.term.group.ConcreteNamespaceCommand;

import java.util.*;
import java.util.stream.Collectors;

public class CalculatedName {
  final LocatedReferable myTarget;
  final boolean myAlias;
  private final List<Pair<String, Referable>> myLongNameWithRefs;
  private final ModuleReferable myContainingFile;
  private final Set<List<String>> myReferenceNames = new HashSet<>();
  private final ArendServerImpl myArendServer;

  public CalculatedName(ArendServerImpl arendServer,
                        LocatedReferable target,
                        boolean skipFirstParent,
                        boolean alias) {
    myArendServer = arendServer;
    LocatedReferable locatedReferable = target;
    boolean skipFlag = skipFirstParent;
    ModuleReferable containingFile = null;
    List<Pair<String, Referable>> longNameWithRefs = new ArrayList<>();

    while (locatedReferable != null) {
      if (!(locatedReferable instanceof ModuleReferable)) {
        String name = (alias ? locatedReferable.getRepresentableName() : locatedReferable.textRepresentation());
        if (!skipFlag || longNameWithRefs.isEmpty()) {
          longNameWithRefs.add(0, new Pair<>(name, locatedReferable));
        } else {
          skipFlag = false;
        }
      } else {
        containingFile = (ModuleReferable) locatedReferable;
      }
      locatedReferable = locatedReferable.getLocatedReferableParent();
    }

    myTarget = target;
    myAlias = alias;
    myLongNameWithRefs = longNameWithRefs;
    myContainingFile = containingFile;
  }

  public List<String> getLongName() {
    List<String> result = new ArrayList<String>();
    for (Pair<String, Referable> pair : myLongNameWithRefs) {
      result.add(pair.proj1);
    }
    return result;
  }

  public Scope getComplementScope() {
    return EmptyScope.INSTANCE;
        /* TODO[server2]
        List<PsiReferable> targetContainers = myLongNameWithRefs.stream()
                .map(Pair::getSecond)
                .collect(Collectors.toList());
        return new ListScope(targetContainers);
        */
  }

  public void processStatCmd(ConcreteNamespaceCommand statCmd, ModuleScopeProvider moduleScopeProvider) {
    myReferenceNames.addAll(calculateShorterNames(statCmd, moduleScopeProvider));
  }

  public void processParentGroup(LocatedReferable group) {
    List<String> remainder = calculateRemainder(group);
    if (remainder != null) {
      myReferenceNames.add(remainder);
    }
  }

  public void checkShortNameInScope(Scope scope) {
    for (Referable element : scope.getElements()) {
      if (element instanceof LocatedReferable) {
        List<String> remainder = calculateRemainder((LocatedReferable) element, true);
        if (remainder != null) {
          myReferenceNames.add(remainder);
        }
      }
    }
  }

  public void addLongNameAsReferenceName() {
    myReferenceNames.add(getLongName());
  }

  public Set<List<String>> getReferenceNames() {
    return myReferenceNames;
  }

  private List<List<String>> calculateShorterNames(ConcreteNamespaceCommand statCmd, ModuleScopeProvider moduleScopeProvider) {
    GlobalReferable openedGroup = moduleScopeProvider.findModule(new ModulePath(statCmd.module().getPath()));

    List<String> remainder = calculateRemainder(openedGroup);
    if (remainder != null && !remainder.isEmpty()) {
      String currName = remainder.getFirst();
      List<String> tail = remainder.subList(1, remainder.size());
      return statCmd.renamings().stream()
        .map(pair -> {
          List<String> list = new ArrayList<>();
          list.add(pair.reference().getRefName());
          list.addAll(tail);
          return list;
        })
        .collect(Collectors.toList());
    }

    return Collections.emptyList();
  }

  private List<String> calculateRemainder(GlobalReferable referable) {
    return calculateRemainder(referable, false);
  }

  private List<String> calculateRemainder(GlobalReferable referable, boolean withFirstName) {
    List<String> result = (referable == myContainingFile) ? new ArrayList<>() : null;
    for (Pair<String, Referable> entry : myLongNameWithRefs) {
      if (!withFirstName && result != null) result.add(entry.proj1);
      if (entry.proj2.equals(referable)) result = new ArrayList<>();
      if (withFirstName && result != null) result.add(entry.proj1);
    }
    return result;
  }

  public ModuleReferable getContainingFile() {
    return myContainingFile;
  }

}
