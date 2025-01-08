package org.arend.term.group;

import java.util.List;

public class GroupPath {
  public record Element(boolean isDynamic, int index) {}

  private final List<Element> myPath;

  public GroupPath(List<Element> path) {
    myPath = path;
  }

  public List<Element> getList() {
    return myPath;
  }
}
