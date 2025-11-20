package org.arend.repl.action;

import org.arend.ext.error.GeneralError;
import org.arend.ext.module.ModulePath;
import org.arend.naming.reference.FullModuleReferable;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.LexicalScope;
import org.arend.naming.scope.Scope;
import org.arend.repl.Repl;
import org.arend.typechecking.error.local.GoalError;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

import static org.arend.repl.Repl.REPL_NAME;


public class ListModulesCommand implements ReplCommand {
    public static final @NotNull ListModulesCommand INSTANCE = new ListModulesCommand();
    public static final String ALL_MODULES = "all";

    @Override
    public void invoke(@NotNull String line, @NotNull Repl api, @NotNull Supplier<@NotNull String> scanner) {
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

    private static void print(@NotNull Repl api, Node node, String prefix, boolean isTail, Node root, Set<ModulePath> loadedModules) {
        String folderName = node.fileExists ? "" : (node == root ? "(root)" : "(not a module)");
        StringBuilder moduleErrors = new StringBuilder();

        ModulePath modulePath = new ModulePath(node.getPath(null));
        if (node.fileExists && loadedModules.contains(modulePath)) {
            Scope scope = api.getAvailableModuleScopeProvider().forModule(modulePath);

            if (scope instanceof CachingScope cachingScope && cachingScope.getInternalScope() instanceof LexicalScope lexicalScope
                    && lexicalScope.getGroup() != null && lexicalScope.getGroup().referable() instanceof FullModuleReferable moduleReferable) {
                List<GeneralError> errorList = api.getErrorList(moduleReferable.getLocation());

                if (errorList != null && !errorList.isEmpty()) {
                    if (errorList.stream().anyMatch(generalError -> generalError instanceof GoalError)) {
                        moduleErrors.append(" ○");
                    } else if (!Objects.equals(modulePath.toString(), REPL_NAME)) {
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
