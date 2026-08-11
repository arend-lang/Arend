package org.arend.proof;

import org.arend.ext.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface PatternTree {
    enum Implicitness {
        IMPLICIT, EXPLICIT;

        public String compactLBrace() {
            return this == IMPLICIT ? "{" : "";
        }

        public String compactRBrace() {
            return this == IMPLICIT ? "}" : "";
        }

        public boolean toBoolean() {
            return this == EXPLICIT;
        }
    }

    List<String> getAllIdentifiers();

    class BranchingNode implements PatternTree {
        public final List<Pair<PatternTree, Implicitness>> subNodes;

        public BranchingNode(List<Pair<PatternTree, Implicitness>> subNodes) {
            this.subNodes = subNodes;
        }

        @Override
        public List<String> getAllIdentifiers() {
            List<String> result = new ArrayList<>();
            for (Pair<PatternTree, Implicitness> node : subNodes) {
                result.addAll(node.proj1.getAllIdentifiers());
            }
            return result;
        }

        @Override
        public String toString() {
            List<String> result = new ArrayList<>();
            for (Pair<PatternTree, Implicitness> node : subNodes) {
                result.add(node.proj2.compactLBrace() + node.proj1.toString() + node.proj2.compactRBrace());
            }
            return "[" + String.join(" ", result) + "]";
        }
    }

    class LeafNode implements PatternTree {
        public final List<String> referenceName;

        public LeafNode(List<String> referenceName) {
            this.referenceName = referenceName;
        }

        @Override
        public List<String> getAllIdentifiers() {
            List<String> result = new ArrayList<>();
            result.add(referenceName.getLast());
            return result;
        }

        @Override
        public String toString() {
            return String.join(".", referenceName);
        }
    }

    final class Wildcard implements PatternTree {

        public static final Wildcard INSTANCE = new Wildcard();

        @Override
        public List<String> getAllIdentifiers() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return "_";
        }
    }
}
