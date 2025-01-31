package org.arend.typechecking.instance.provider;

import org.arend.naming.reference.*;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.NamespaceCommandNamespace;
import org.arend.naming.scope.Scope;
import org.arend.term.NamespaceCommand;
import org.arend.term.group.Group;
import org.arend.term.group.Statement;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

// TODO[server2]: Rework/delete this.
public class InstanceProviderSet {
  private final Map<TCDefReferable, InstanceProvider> myProviders = new HashMap<>();
  private final Set<Group> myCollected = new HashSet<>();

  public void put(TCDefReferable referable, InstanceProvider provider) {
    myProviders.put(referable, provider);
  }

  public InstanceProvider get(TCReferable referable) {
    return referable instanceof TCDefReferable ? myProviders.get(referable) : null;
  }

  public InstanceProvider computeIfAbsent(TCDefReferable referable, Function<? super TCDefReferable, ? extends InstanceProvider> fun) {
    return myProviders.computeIfAbsent(referable, fun);
  }

  private class MyPredicate implements Predicate<Referable> {
    private SimpleInstanceProvider instanceProvider;
    private boolean used = false;

    private MyPredicate() {
      this.instanceProvider = new SimpleInstanceProvider();
    }

    public LocatedReferable recordInstances(LocatedReferable ref) {
      if (instanceProvider.isEmpty()) return ref;
      if (ref instanceof TCDefReferable tcRef) {
        SimpleInstanceProvider instanceProvider = this.instanceProvider;
        if (tcRef.getKind() == GlobalReferable.Kind.INSTANCE) {
          instanceProvider = new SimpleInstanceProvider(instanceProvider);
          instanceProvider.remove(tcRef);
        }
        myProviders.put(tcRef, instanceProvider);
      }
      return ref;
    }

    void test(int index, Referable ref) {
      if (ref instanceof TCDefReferable instance && instance.getKind() == GlobalReferable.Kind.INSTANCE) {
        if (used) {
          instanceProvider = new SimpleInstanceProvider(instanceProvider);
          used = false;
        }
        instanceProvider.add(index, instance);
      }
    }

    @Override
    public boolean test(Referable ref) {
      test(-1, ref);
      return false;
    }
  }

  public boolean collectInstances(Group group, Scope parentScope, LocatedReferable referable) {
    if (!myCollected.add(group)) {
      return false;
    }

    var predicate = new MyPredicate();
    parentScope.find(predicate);
    predicate.instanceProvider.reverseFrom(0);
    processGroup(group, parentScope, predicate);
    predicate.recordInstances(referable);
    return true;
  }

  public boolean collectInstances(Group group, Scope parentScope) {
    return collectInstances(group, parentScope, group.getReferable());
  }

  private void processGroup(Group group, Scope parentScope, MyPredicate predicate) {
    Collection<? extends Statement> statements = group.getStatements();
    if (statements.isEmpty()) {
      return;
    }

    parentScope = CachingScope.make(LexicalScope.insideOf(group, parentScope, false));
    List<Group> subgroups = new ArrayList<>();
    for (Statement statement : statements) {
      NamespaceCommand command = statement.getNamespaceCommand();
      if (command != null) {
        int size = predicate.instanceProvider.getInstances().size();
        NamespaceCommandNamespace.resolveNamespace(command.getKind() == NamespaceCommand.Kind.IMPORT ? parentScope.getImportedSubscope() : parentScope, command).find(predicate);
        predicate.instanceProvider.reverseFrom(size);
      }
      Group subgroup = statement.getGroup();
      if (subgroup != null) {
        subgroups.add(subgroup);
      }
    }
    processSubgroups(parentScope, predicate, subgroups);
  }

  private void processSubgroups(Scope parentScope, MyPredicate predicate, Collection<? extends Group> subgroups) {
    int size = predicate.instanceProvider.getInstances().size();
    for (Group subgroup : subgroups) {
      LocatedReferable groupRef = subgroup.getReferable();
      if (groupRef.getKind() == GlobalReferable.Kind.COCLAUSE_FUNCTION) continue;
      predicate.used = true;
      SimpleInstanceProvider instanceProvider = predicate.instanceProvider;
      processGroup(subgroup, parentScope, predicate);

      if (!predicate.instanceProvider.isEmpty()) {
        for (Statement statement : subgroup.getStatements()) {
          Group subSubgroup = statement.getGroup();
          if (subSubgroup != null) {
            processCoclauseFunction(subSubgroup, predicate);
          }
        }
        for (Group dynamicSubgroup : subgroup.getDynamicSubgroups()) {
          processCoclauseFunction(dynamicSubgroup, predicate);
        }
      }

      LocatedReferable ref = predicate.recordInstances(groupRef);
      processSubgroups(parentScope, predicate, subgroup.getDynamicSubgroups());
      predicate.used = true;
      predicate.instanceProvider = instanceProvider;
      predicate.test(size, ref);
    }
  }

  private void processCoclauseFunction(Group subgroup, MyPredicate predicate) {
    LocatedReferable subRef = subgroup.getReferable();
    if (subRef instanceof TCDefReferable && subRef.getKind() == GlobalReferable.Kind.COCLAUSE_FUNCTION) {
      myProviders.put((TCDefReferable) subRef, predicate.instanceProvider);
    }
  }
}
