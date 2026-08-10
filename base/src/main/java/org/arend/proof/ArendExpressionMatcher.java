package org.arend.proof;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.util.Pair;
import org.arend.naming.binOp.ExpressionBinOpEngine;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.scope.CachingScope;
import org.arend.naming.scope.Scope;
import org.arend.term.Fixity;
import org.arend.term.concrete.Concrete;
import org.arend.term.prettyprint.FreeVariableCollectorConcrete;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ArendExpressionMatcher {
    private final ProofSearchQuery query;

    public ArendExpressionMatcher(ProofSearchQuery query) {
        this.query = query;
    }

    public record ProofSearchMatchingResult(List<Pair<Concrete.Expression, List<Concrete.Expression>>> inPattern,
                                            List<Concrete.Expression> inCodomain) { }

    /**
     * @return null if the signature cannot be matched against [query],
     * list of concrete nodes where the matching occurred otherwise
     */
    public ProofSearchMatchingResult match(List<Concrete.Expression> parameters, Concrete.Expression codomain, Scope scope) {
        Scope cachingScope = CachingScope.make(scope);
        Set<Referable> set = new HashSet<>();
        codomain.accept(new FreeVariableCollectorConcrete(set), null);
        Map<String, List<Referable>> qualifiedReferables = set.stream()
                .collect(Collectors.groupingBy(Referable::getRefName));

        List<Concrete.Expression> codomainResult = matchConjunction(query.codomain, codomain, cachingScope, qualifiedReferables);
        if (codomainResult == null) {
            if (!parameters.isEmpty() && query.parameters.isEmpty()) {
                for (Concrete.Expression matchParameter : parameters) {
                    List<Concrete.Expression> match = matchConjunction(query.codomain, matchParameter, cachingScope, qualifiedReferables);
                    if (match != null) {
                        return new ProofSearchMatchingResult(List.of(new Pair<>(matchParameter, match)), new ArrayList<>());
                    }
                }
            }
            return null;
        }

        if (parameters.isEmpty()) {
            if (query.parameters.isEmpty()) {
                return new ProofSearchMatchingResult(new ArrayList<>(), codomainResult);
            }
            return null;
        }

        List<Pair<Concrete.Expression, List<Concrete.Expression>>> parameterResults = new ArrayList<>();
        Set<Concrete.Expression> usedParameters = new HashSet<>();

        loop: for (ProofSearchQuery.ProofSearchJointPattern patternParameter : query.parameters) {
            for (Concrete.Expression matchParameter : parameters) {
                if (usedParameters.contains(matchParameter)) {
                    continue;
                }
                List<Concrete.Expression> match = matchConjunction(patternParameter, matchParameter, cachingScope, qualifiedReferables);
                if (match != null) {
                    usedParameters.add(matchParameter);
                    parameterResults.add(new Pair<>(matchParameter, match));
                    continue loop;
                }
            }
            return null;
        }
        return new ProofSearchMatchingResult(parameterResults, codomainResult);
    }

    /**
     * Matches one joint pattern -- a {@code \and}-conjoined clause -- against a single
     * expression (a parameter or the codomain). Per the documented {@code \and} semantics
     * every conjunct must match the SAME expression, so the clause fails (returns null) as
     * soon as one conjunct cannot be built or does not match; on success the result is the
     * concatenation of every conjunct's matched sub-terms (used only for highlighting). A
     * lone pattern with no {@code \and} is just a one-element conjunction.
     */
    private List<Concrete.Expression> matchConjunction(ProofSearchQuery.ProofSearchJointPattern jointPattern, Concrete.Expression codomain, Scope scope, Map<String, List<Referable>> referables) {
        List<Concrete.Expression> result = new ArrayList<>();
        for (PatternTree patternTree : jointPattern.patterns) {
            Concrete.Expression patternExpr = reassembleConcrete(patternTree, scope, referables);
            if (patternExpr == null) return null;
            List<Concrete.Expression> matched = performMatch(patternExpr, codomain);
            if (matched == null) return null;
            result.addAll(matched);
        }
        return result;
    }

    private List<Concrete.Expression> performMatch(Concrete.Expression pattern, Concrete.Expression matched) {
        if (performTopMatch(pattern, matched)) {
            return List.of(matched);
        }
        if (matched instanceof Concrete.AppExpression appMatched) {
            List<Concrete.Expression> result = performMatch(pattern, appMatched.getFunction());
            if (result != null) return result;
            for (Concrete.Argument argument : appMatched.getArguments()) {
                List<Concrete.Expression> argMatch = performMatch(pattern, argument.expression);
                if (argMatch != null) return argMatch;
            }
        }
        if (matched instanceof Concrete.SigmaExpression sigmaMatched) {
            for (Concrete.TypeParameter projection : sigmaMatched.getParameters()) {
                List<Concrete.Expression> projMatch = performMatch(pattern, projection.type);
                if (projMatch != null) return projMatch;
            }
        }
        if (matched instanceof Concrete.PiExpression piMatched) {
            List<Concrete.Expression> codomainMatch = performMatch(pattern, piMatched.codomain);
            if (codomainMatch != null) return codomainMatch;
            for (Concrete.TypeParameter parameter : piMatched.getParameters()) {
                List<Concrete.Expression> parameterMatch = performMatch(pattern, parameter.type);
                if (parameterMatch != null) return parameterMatch;
            }
            return null;
        }
        if (matched instanceof Concrete.LetExpression letMatched) {
            for (Concrete.LetClause clause : letMatched.getClauses()) {
                List<Concrete.Expression> clauseMatch = performMatch(pattern, clause.term);
                if (clauseMatch != null) return clauseMatch;
            }
            List<Concrete.Expression> patternMatch = performMatch(pattern, letMatched.expression);
            if (patternMatch != null) return patternMatch;
        }
        if (matched instanceof Concrete.LamExpression lamMatched) {
            return performMatch(pattern, lamMatched.body);
        }
        return null;
    }

    private boolean performTopMatch(Concrete.Expression pattern, Concrete.Expression matched) {
        if (pattern instanceof Concrete.HoleExpression) return true;
        if (pattern instanceof Concrete.AppExpression appP && matched instanceof Concrete.AppExpression appM) {
            Iterable<Pair<Concrete.Expression, Concrete.Expression>> mapping = doubleArgumentIterable(appP.getArguments(), appM.getArguments());
            if (mapping == null) return false;
            if (!performTopMatch(appP.getFunction(), appM.getFunction())) return false;
            for (Pair<Concrete.Expression, Concrete.Expression> pair : mapping) {
                if (!performTopMatch(pair.proj1, pair.proj2)) return false;
            }
            return true;
        } else if (pattern instanceof Concrete.ReferenceExpression && matched instanceof Concrete.ReferenceExpression) {
            if (pattern.getUnderlyingReferable() instanceof Referable patternReferable &&
                    matched.getUnderlyingReferable() instanceof Referable matchedReferable) {
                if (patternReferable.equals(matchedReferable)) return true;
                if (patternReferable.getAbstractReferable() == null || matchedReferable.getAbstractReferable() == null) return false;
                return Objects.equals(patternReferable.getAbstractReferable(), matchedReferable.getAbstractReferable());
            }
            return false;
        } else return pattern instanceof Concrete.NumericLiteral patternNumeric && matched instanceof Concrete.NumericLiteral matchedNumeric
                && patternNumeric.getNumber().equals(matchedNumeric.getNumber());
    }

    private Concrete.Expression reassembleConcrete(PatternTree tree, Scope scope, Map<String, List<Referable>> references) {
        if (tree instanceof PatternTree.BranchingNode) {
            List<Pair<PatternTree, PatternTree.Implicitness>> subNodes = ((PatternTree.BranchingNode) tree).subNodes;
            List<Concrete.BinOpSequenceElem<Concrete.Expression>> binOpList = new ArrayList<>(subNodes.size());
            for (int i = 0; i < subNodes.size(); i++) {
                Concrete.Expression expr = reassembleConcrete(subNodes.get(i).proj1, scope, references);
                if (expr == null) break;
                boolean explicitness = subNodes.get(i).proj2.toBoolean();
                Concrete.BinOpSequenceElem<Concrete.Expression> binOp = new Concrete.BinOpSequenceElem<>(expr, (i == 0 || !(expr instanceof Concrete.ReferenceExpression)) ? Fixity.NONFIX : Fixity.UNKNOWN, explicitness);
                binOpList.add(binOp);
            }
            if (binOpList.size() != subNodes.size()) {
                return null;
            } else {
                return ExpressionBinOpEngine.parse(new Concrete.BinOpSequenceExpression(null, binOpList, null), DummyErrorReporter.INSTANCE, TypingInfo.EMPTY);
            }
        } else if (tree instanceof PatternTree.LeafNode) {
            List<String> referenceName = ((PatternTree.LeafNode) tree).referenceName;
            Referable referable = Scope.resolveName(scope, referenceName);
            if (referable == null) referable = disambiguate(references.get(referenceName.getLast()), referenceName);
            if (referable != null) {
                return Concrete.FixityReferenceExpression.make(null, referable, Fixity.UNKNOWN, null);
            } else if (!referenceName.isEmpty()) {
                try {
                    int number = Integer.parseInt(referenceName.getFirst());
                    return new Concrete.NumericLiteral(null, BigInteger.valueOf(number));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        } else if (tree instanceof PatternTree.Wildcard) {
            return new Concrete.HoleExpression(null);
        }
        return null;
    }

    private Iterable<Pair<Concrete.Expression, Concrete.Expression>> doubleArgumentIterable(List<Concrete.Argument> patternArguments, List<Concrete.Argument> matchArguments) {
        int indexInMatch = 0;
        List<Pair<Concrete.Expression, Concrete.Expression>> container = new ArrayList<>();
        for (Concrete.Argument patternArg : patternArguments) {
            if (indexInMatch == matchArguments.size()) {
                return null;
            }
            if (patternArg.isExplicit()) {
                while (!matchArguments.get(indexInMatch).isExplicit()) {
                    indexInMatch += 1;
                    if (indexInMatch == matchArguments.size()) {
                        return null;
                    }
                }
            }
            if (patternArg.isExplicit() != matchArguments.get(indexInMatch).isExplicit()) {
                return null;
            }
            container.add(new Pair<>(patternArg.expression, matchArguments.get(indexInMatch).expression));
            indexInMatch += 1;
        }
        return container;
    }

    private Referable disambiguate(List<Referable> candidates, List<String> path) {
        if (candidates == null) return null;
        Referable result = null;
        for (Referable candidate : candidates) {
            List<String> location = new ArrayList<>();
            if (candidate instanceof LocatedReferable locatedReferable) {
                ModuleLocation moduleLocation  = locatedReferable.getLocation();
                if (moduleLocation != null) {
                    location = moduleLocation.getModulePath().toList();
                }
            }
            List<String> longName = candidate.getRefLongName() != null ? candidate.getRefLongName().toList() : null;
            if (longName == null) continue;
            List<String> actualLongName = Stream.concat(location.stream(), longName.stream()).toList();
            if (!Objects.equals(actualLongName.getLast(), path.getLast())) {
                return null;
            }
            int pathIndex = 0;
            for (String fullPathPart : actualLongName) {
                if (pathIndex >= path.size() - 1) {
                    break;
                }
                if (Objects.equals(fullPathPart, path.get(pathIndex))) {
                    pathIndex += 1;
                }
            }
            if (pathIndex < path.size() - 1) {
                return null;
            } else if (result == null) {
                result = candidate;
            } else {
                // there are two referables with the same suffix, it is ambiguous
                return null;
            }
        }
        return result;
    }
}
