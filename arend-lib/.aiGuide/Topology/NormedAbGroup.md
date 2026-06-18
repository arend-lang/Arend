### Topology.NormedAbGroup

Normed abelian groups built on top of topological abelian groups and (extended) (pseudo)metric spaces.

This module assembles the hierarchy of normed structures by combining a norm `norm : E -> ExUpperReal` with the additive group structure: every distance is recovered as `dist x y = norm (x - y)`, so the metric, uniform, and topological structure are all derived from the norm. The hierarchy is parameterized along three orthogonal axes — extended vs. real-valued (`Ex…` vs. plain), pseudo vs. genuine (whether `norm x = 0` forces `x = 0`), and bounded vs. unbounded — with separate classes for completeness. Maps between normed groups specialize the metric/topological group homomorphisms by stating their continuity directly in terms of the norm rather than the distance.

#### Base Class: ExPseudoNormedAbGroup

- **`ExPseudoNormedAbGroup`**: Extended pseudo-normed abelian group; extends `ExPseudoMetricSpace` and `TopAbGroup`. Carries `norm : E -> ExUpperReal` satisfying `norm zro = 0`, `norm (negative x) = norm x`, and the triangle inequality `norm (x + y) <= norm x + norm y`, with `dist x y = norm (x - y)`. Derives addition/negation continuity, the uniform structure, and the topology from the norm via default implementations.
- **`norm`**, **`norm_zro`**, **`norm_negative`**, **`norm_+`**: The norm and its three structural laws.
- **`norm-dist`**: Coherence law tying distance to the norm of the difference.
- **`IsUnbounded`**: Predicate that `norm` takes values exceeding every natural number.
- **`norm_*n_<=_*n`**, **`norm_*n_<=`**: Bounds on the norm of an `n`-fold sum: `norm (n *n a) <= n *n norm a` (and the multiplicative form).
- **`norm_BigSum`**: Triangle inequality for finite sums: `norm (BigSum l) <= BigSum (\lam j => norm (l j))`.

#### Norm/Distance Lemmas

- **`norm>=0`**: Norm is nonnegative.
- **`norm_dist`**: `norm x = dist 0 x`.
- **`norm_-`**: `norm (x - y) = norm (y - x)`.
- **`norm_dist-left`**, **`norm_dist-right`**: One-sided triangle bounds: `norm x <= dist x y + norm y` and `norm x <= norm y + dist x y`.

#### Variants of the Class

- **`ExNormedAbGroup`**: Extends `ExPseudoNormedAbGroup` and `ExMetricSpace` with `norm-ext`: `norm x = 0 -> x = 0`. The axiom is shown equivalent to `dist-ext`.
- **`BoundedExPseudoNormedAbGroup`**: Adds `norm-bounded` requiring every `norm x` to be a bounded upper real (i.e., finite). Provides `bnorm` returning the value as `UpperReal`.
- **`PseudoNormedAbGroup`**: Bounded version with real-valued norm; extends `BoundedExPseudoNormedAbGroup` and `PseudoMetricSpace`. Overrides `norm : E -> Real`.
- **`NormedAbGroup`**: Full normed abelian group; extends `PseudoNormedAbGroup` and `ExNormedAbGroup`.

#### Real-Valued Norm Helpers

- **`lnorm`**: Real-valued norm `lnorm x = X.norm x` for a `PseudoNormedAbGroup`.
- **`lnorm>=0`**, **`lnorm_zro`**: Nonnegativity and `lnorm 0 = 0`.
- **`lnorm_+`**: Triangle inequality in `Real`.
- **`lnorm-ldist`**: `ldist x y = lnorm (x - y)` linking the real-valued distance and norm.
- **`lnorm_BigSum`**: Triangle inequality for finite sums in `Real`.
- **`lnorm_-`**: Symmetry `lnorm (x - y) = lnorm (y - x)`.
- **`lnorm_-left`**, **`lnorm_-right`**: Reverse triangle inequalities `lnorm x - lnorm y <= lnorm (x - y)` (and the `y - x` form).
- **`lnorm_-_abs`**: Combined absolute reverse triangle inequality `|lnorm x - lnorm y| <= lnorm (x - y)`.

#### Maps Between Normed Groups

- **`UniformNormedAbGroupMap`**: Extends `UniformMetricMap` and `TopAbGroupMap`. Characterizes uniform continuity directly via the norm: `func-norm-uniform` says small-norm inputs give small-norm outputs. Equivalence with `func-dist-uniform` is provided.
- **`NormedAbGroupMap`**: Extends `UniformNormedAbGroupMap` and `MetricMap`; non-expansive maps satisfying `norm (func x) <= norm x`.
- **`NormedIsometricMap`**: Extends `NormedAbGroupMap` and `IsometricMap`; norm-preserving maps with `norm (func x) = norm x`.

#### Completeness

- **`CompleteExNormedAbGroup`**: Extends `ExNormedAbGroup`, `CompleteExMetricSpace`, and `CompleteTopAbGroup`.
- **`CompleteNormedAbGroup`**: Extends `CompleteExNormedAbGroup`, `NormedAbGroup`, and `CompleteMetricSpace`.

#### Bilinear Operations

- **`bilinear-locally-uniform`**: Given biadditive `f : A -> B -> C` (subtractive in each argument) on bounded extended pseudo-normed groups together with the submultiplicative bound `norm (f x y) <= norm x * norm y`, produces a `LocallyUniformMap (A ⨯ B) C` for the uncurried map. Foundational for showing multiplication-like operations are locally uniformly continuous.
