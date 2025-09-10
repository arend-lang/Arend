package org.arend.frontend.repl.action;

import org.arend.ext.error.GeneralError;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.repl.CommonCliRepl;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.Scope;
import org.arend.typechecking.error.local.GoalError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

import static org.arend.frontend.repl.action.LoadModuleCommand.ALL_MODULES;

public final class ListLoadedModulesAction implements CliReplCommand {
  public static final @NotNull ListLoadedModulesAction INSTANCE = new ListLoadedModulesAction();

  private ListLoadedModulesAction() {
  }

  @Override
  public void invoke(@NotNull String line, @NotNull CommonCliRepl api, @NotNull Supplier<@NotNull String> scanner) {
    Node root = new Node("", null);
    if (line.equals(ALL_MODULES)) {
      api.getAllModules().forEach(mP -> insert(mP.toArray(), root));
    } else if (line.isEmpty()) {
      api.getLoadedModules().forEach(mP -> insert(mP.toArray(), root));
    }
    Set<ModulePath> loadedModules = api.getLoadedModules();
    if (root.children.isEmpty()) api.println("[INFO] No modules loaded."); else print(api, root, "", true, root, loadedModules);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String description() {
    return "List all loaded modules or all modules (:modules all)";
  }

  private static void insert(String[] longName, Node root) {
    Node currentNode = root;
    for (String name : longName) {
      currentNode.children.putIfAbsent(name, new Node(name, currentNode));
      currentNode = currentNode.children.get(name);
    }
    currentNode.fileExists = true;
  }

  private static void print(@NotNull CommonCliRepl api, Node node, String prefix, boolean isTail, Node root, Set<ModulePath> loadedModules) {
    String folderName = node.fileExists ? "" : (node == root ? "(root)" : "(not a module)");
    StringBuilder moduleErrors = new StringBuilder();

    boolean hasFailedDefs = false;
    ModulePath modulePath = new ModulePath(node.getPath(null));
    if (node.fileExists && loadedModules.contains(modulePath)) {
      Scope scope = api.getAvailableModuleScopeProvider().forModule(modulePath);

      if (scope != null) {
        for (Referable referable : scope.getElements()) {
          if (referable instanceof TCDefReferable referable1) {
            if (referable1.getTypechecked() == null && referable1.getKind().isTypecheckable()) {
              hasFailedDefs = true;
              break;
            }
          }
        }
      }

      if (scope instanceof CachingScope cachingScope && cachingScope.getInternalScope() instanceof LexicalScope lexicalScope
        && lexicalScope.getGroup() != null && lexicalScope.getGroup().referable() instanceof FullModuleReferable moduleReferable) {
        List<GeneralError> errorList = api.getErrorList(moduleReferable.getLocation());

        if (errorList != null && !errorList.isEmpty()) {
          if (errorList.stream().anyMatch(generalError -> generalError instanceof GoalError)) {
            moduleErrors.append(" ○");
          } else {
            moduleErrors.append(" ❌");
          }
        }
      }
    }

    api.print(prefix + (isTail ? "└── " : "├── ") + node.value + " " + folderName);
    if (!loadedModules.contains(modulePath)) {
      api.println();
    } else if (!moduleErrors.isEmpty()) {
      api.eprintln(moduleErrors.toString().trim());
    } else if (hasFailedDefs) {
      api.println();
    } else if (node.fileExists) {
      api.println("✅");
    } else {
      api.println();
    }

    List<Node> children = new ArrayList<>(node.children.values());
    children.sort(Comparator.comparing(o -> o.value));
    for (int i = 0; i < children.size() - 1; i++) {
      print(api, children.get(i), prefix + (isTail ? "    " : "│   "), false, root, loadedModules);
    }

    if (!children.isEmpty()) {
      print(api, children.getLast(), prefix + (isTail ? "    " : "│   "), true, root, loadedModules);
    }
  }

  private static class Node {
    String value;
    Boolean fileExists;
    Map<String, Node> children;

    Node myParent;

    Node(String value, Node parent) {
      this.value = value;
      this.children = new LinkedHashMap<>();
      this.fileExists = false;
      this.myParent = parent;
    }

    List<String> getPath(Node root) {
      if (this.myParent == root || this.myParent == null) {
        return new ArrayList<>();
      }
      List<String> result = this.myParent.getPath(root);
      result.add(value);
      return result;
    }
  }
}
