package org.arend.server.impl;

import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.TCDefReferable;
import org.arend.typechecking.order.PartialComparator;

import java.util.*;

class GroupComparator implements PartialComparator<TCDefReferable> {
  private final Map<ModuleLocation, GroupData> myGroups;

  GroupComparator(Map<ModuleLocation, GroupData> groups) {
    myGroups = groups;
  }

  @Override
  public boolean sort(List<TCDefReferable> list) {
    if (list.isEmpty()) return true;
    ModuleLocation module = list.getFirst().getLocation();
    GroupData groupData = module == null ? null : myGroups.get(module);
    if (groupData == null) return false;

    Set<TCDefReferable> result = new LinkedHashSet<>();
    groupData.getRawGroup().traverseGroup(group -> {
      if (group.referable() instanceof TCDefReferable tcRef && list.contains(tcRef)) {
        result.add(tcRef);
      }
    });

    if (result.size() != list.size()) {
      return false;
    }

    list.clear();
    list.addAll(result);
    return true;
  }
}
