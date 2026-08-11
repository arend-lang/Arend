package org.arend.proof;

import org.arend.ext.util.Pair;
import org.arend.util.Range;

import java.util.*;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProofSearchQuery {
    public final List<ProofSearchJointPattern> parameters;
    public final ProofSearchJointPattern codomain;

    public ProofSearchQuery(List<ProofSearchJointPattern> parameters, ProofSearchJointPattern codomain) {
        this.parameters = parameters;
        this.codomain = codomain;
    }

    public static class ProofSearchJointPattern {
        public final List<PatternTree> patterns;

        public ProofSearchJointPattern(List<PatternTree> patterns) {
            this.patterns = patterns;
        }

        public List<String> getAllIdentifiers() {
            List<String> result = new ArrayList<>();
            for (PatternTree pattern : patterns) {
                result.addAll(pattern.getAllIdentifiers());
            }
            return result;
        }

        @Override
        public String toString() {
            List<String> result = new ArrayList<>();
            for (PatternTree pattern : patterns) {
                result.add(pattern.toString());
            }
            return "<" + String.join("  \\and  ", result) + ">";
        }
    }

    public static class Token {
        public final String repr;
        public final Range<Integer> range;

        public Token(String repr, Range<Integer> range) {
            this.repr = repr;
            this.range = range;
        }
    }

    public interface ParsingResult<T> {
        class OK<T> implements ParsingResult<T> {
            public final T value;
            public OK(T value) { this.value = value; }
        }
        class Error<T> implements ParsingResult<T> {
            public final String message;
            public final Range<Integer> range;
            public Error(String message, Range<Integer> range) { this.message = message; this.range = range; }
            @SuppressWarnings("unchecked")
            public <R> Error<R> cast() { return (Error<R>) this; }
        }

        default <U> ParsingResult<U> bind(Function<T, ParsingResult<U>> f) {
            if (this instanceof OK<T> ok) {
                return f.apply(ok.value);
            } else {
                return ((Error<T>) this).cast();
            }
        }

        default <U> ParsingResult<U> map(Function<T, U> f) {
            return bind(value -> new OK<>(f.apply(value)));
        }
    }

    public static ParsingResult<ProofSearchQuery> fromString(String pattern) {
        Pattern patternString = Pattern.compile("[^(){}\\s]+|\\(|\\)|\\{|}");
        Matcher matcher = patternString.matcher(pattern);
        List<MatchResult> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(matcher.toMatchResult());
        }
        List<Token> tokens = matches.stream().map(match -> new Token(match.group(), new Range<>(match.start(), match.end()))).toList();
        return parseTokens(tokens);
    }

    private static ParsingResult<ProofSearchQuery> parseTokens(List<Token> tokens) {
        ParsingResult<int[]> braceMatchingResult = computeBraceMatching(tokens);
        if (braceMatchingResult instanceof ParsingResult.Error) return ((ParsingResult.Error<int[]>) braceMatchingResult).cast();
        int[] braceMatcher = ((ParsingResult.OK<int[]>) braceMatchingResult).value;

        List<Pair<Integer, Integer>> disjunctionBoundaries = splitTokens(tokens, "->");

        List<ParsingResult<ProofSearchJointPattern>> result = new ArrayList<>();
        for (Pair<Integer, Integer> disjunctionBoundary : disjunctionBoundaries) {
            Integer start = disjunctionBoundary.proj1;
            Integer end = disjunctionBoundary.proj2;
            List<Token> subTokens = tokens.subList(start, end);
            List<Pair<Integer, Integer>> disjunctionSubTokens = splitTokens(subTokens, "\\and");
            result.add(swap(disjunctionSubTokens.stream().map(pair ->
                    doParseTokens(pair.proj1 + start, pair.proj2 + start, tokens, braceMatcher)
            ).toList()).map(ProofSearchJointPattern::new));
        }
        ProofSearchQuery.ParsingResult<List<ProofSearchQuery.ProofSearchJointPattern>> rawPatterns = swap(result);
        return rawPatterns.map(proofSearchJointPatterns ->
                new ProofSearchQuery(proofSearchJointPatterns.subList(0, proofSearchJointPatterns.size() - 1), proofSearchJointPatterns.getLast()));
    }

    private static List<Pair<Integer, Integer>> splitTokens(List<Token> tokens, String pattern) {
        List<Pair<Integer, Integer>> newList = new ArrayList<>();
        int firstIndex = 0;
        for (int idx = 0; idx < tokens.size(); idx++) {
            if (tokens.get(idx).repr.equals(pattern)) {
                newList.add(new Pair<>(firstIndex, idx));
                firstIndex = idx + 1;
            }
        }
        newList.add(new Pair<>(firstIndex, tokens.size()));
        return newList;
    }

    public static <T> ParsingResult<List<T>> swap(List<ParsingResult<T>> list) {
        ParsingResult<List<T>> result = new ParsingResult.OK<>(new ArrayList<>());
        for (ParsingResult<T> t : list) {
            result = result.bind(collectedList ->
                    t.map(e -> {
                        collectedList.add(e);
                        return collectedList;
                    })
            );
        }
        return result;
    }

    static ParsingResult<PatternTree> doParseTokens(int position, int limit, List<Token> tokens, int[] braceMatcher) {
        if (position == limit) {
            Range range;
            if (tokens.isEmpty()) {
                range = new Range<>(0, 0);
            } else if (limit == tokens.size()) {
                range = tokens.get(position - 1).range;
            } else {
                range = tokens.get(position).range;
            }
            return new ParsingResult.Error<>("Unexpected end of input", range);
        }

        List<Pair<PatternTree, PatternTree.Implicitness>> nodes = new ArrayList<>();
        int currentPosition = position;

        while (currentPosition != limit) {
            String tokenRepr = tokens.get(currentPosition).repr;

            switch (tokenRepr) {
                case "_":
                    nodes.add(new Pair<>(PatternTree.Wildcard.INSTANCE, PatternTree.Implicitness.EXPLICIT));
                    break;

                case "(": {
                    int closingBrace = braceMatcher[currentPosition];
                    ParsingResult<PatternTree> result = doParseTokens(currentPosition + 1, closingBrace, tokens, braceMatcher);

                    if (result instanceof ParsingResult.OK) {
                        PatternTree value = ((ParsingResult.OK<PatternTree>) result).value;
                        nodes.add(new Pair<>(value, PatternTree.Implicitness.EXPLICIT));
                        currentPosition = closingBrace;
                    } else {
                        return result;
                    }
                    break;
                }

                case ")":
                    return new ParsingResult.Error<>("Unexpected ')'", tokens.get(currentPosition).range);

                case "{": {
                    int closingBrace = braceMatcher[currentPosition];
                    ParsingResult<PatternTree> result = doParseTokens(currentPosition + 1, closingBrace, tokens, braceMatcher);

                    if (result instanceof ParsingResult.OK) {
                        PatternTree value = ((ParsingResult.OK<PatternTree>) result).value;
                        nodes.add(new Pair<>(value, PatternTree.Implicitness.IMPLICIT));
                        currentPosition = closingBrace;
                    } else {
                        return result;
                    }
                    break;
                }

                case "}":
                    return new ParsingResult.Error<>("Unexpected '}'", tokens.get(currentPosition).range);

                default:
                    nodes.add(new Pair<>(
                            new PatternTree.LeafNode(Arrays.asList(tokenRepr.split("\\."))),
                            PatternTree.Implicitness.EXPLICIT
                    ));
                    break;
            }
            currentPosition += 1;
        }

        if (nodes.size() == 1 && nodes.getFirst().proj2 != PatternTree.Implicitness.IMPLICIT) {
            return new ParsingResult.OK<>(nodes.getFirst().proj1);
        } else {
            return new ParsingResult.OK<>(new PatternTree.BranchingNode(nodes));
        }
    }

    private static ParsingResult<int[]> computeBraceMatching(List<Token> tokens) {
        int[] array = new int[tokens.size()];
        List<Pair<Character, Integer>> stack = new ArrayList<>();

        for (int idx = 0; idx < tokens.size(); idx++) {
            String repr = tokens.get(idx).repr;
            switch (repr) {
                case "->":
                    if (!stack.isEmpty()) {
                        return new ParsingResult.Error<>("'->' is allowed only on top level", tokens.get(idx).range);
                    }
                    break;
                case "\\and":
                    if (stack.size() > 1) {
                        return new ParsingResult.Error<>("'\\and' is allowed only in clauses", tokens.get(idx).range);
                    }
                    break;
                case "(":
                    stack.add(new Pair<>(')', idx));
                    break;
                case "{":
                    stack.add(new Pair<>('}', idx));
                    break;
                case ")": {
                    if (stack.isEmpty()) {
                        return new ParsingResult.Error<>("Unexpected ')'", tokens.get(idx).range);
                    }

                    Pair<Character, Integer> top = stack.removeLast();
                    char topBrace = top.proj1;
                    int topIndex = top.proj2;

                    if (topBrace != ')') {
                        return new ParsingResult.Error<>("Unexpected ')'", tokens.get(idx).range);
                    }
                    array[topIndex] = idx;
                    break;
                }

                case "}": {
                    if (stack.isEmpty()) {
                        return new ParsingResult.Error<>("Unexpected '}'", tokens.get(idx).range);
                    }

                    Pair<Character, Integer> top = stack.removeLast();
                    char topBrace = top.proj1;
                    int topIndex = top.proj2;

                    if (topBrace != '}') {
                        return new ParsingResult.Error<>("Unexpected '}'", tokens.get(idx).range);
                    }

                    array[topIndex] = idx;
                    break;
                }
            }
        }

        if (!stack.isEmpty()) {
            Pair<Character, Integer> first = stack.getFirst();
            return new ParsingResult.Error<>("Could not find a matching brace", tokens.get(first.proj2).range);
        } else {
            return new ParsingResult.OK<>(array);
        }
    }

    public List<String> getAllIdentifiers() {
        List<String> result = new ArrayList<>();
        for (ProofSearchJointPattern p : parameters) result.addAll(p.getAllIdentifiers());
        result.addAll(codomain.getAllIdentifiers());
        return result;
    }

    @Override
    public String toString() {
        return String.join(" -> ", parameters.stream().map(ProofSearchJointPattern::toString).toList()) +
                (parameters.isEmpty() ? "" : " --> ") + codomain.toString();
    }
}
