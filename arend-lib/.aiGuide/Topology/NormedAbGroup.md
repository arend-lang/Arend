### Topology.NormedAbGroup

Normed abelian groups: topological abelian groups equipped with a norm function compatible with a (pseudo)metric structure, including extended-real-valued, bounded, complete, and uniform variants.

#### Extended Pseudo-Normed Groups

- **`ExPseudoNormedAbGroup`**: Extends `ExPseudoMetricSpace` and `TopAbGroup`. An abelian group with a norm `norm : E -> ExUpperReal` taking values in extended upper reals, satisfying `norm zro = 0`, `norm (negative x) = norm x`, the triangle inequality `norm (x + y) <= norm x + norm y`, and compatibility `dist x y = norm (x - y)`. Provides automatic continuity of `+` and `negative`, and derives the uniform/topological structure from the norm.
- **`norm>=0`**: Norms are non-negative: `0 <= norm x`.
- **`norm_dist`**: `norm x = dist 0 x`.
- **`norm_-`**: Symmetry of norm of differences: `norm (x - y) = norm (y - x)`.
- **`norm_dist-left`**: `norm x <= dist x y + norm y`.
- **`norm_dist-right`**: `norm x <= norm y + dist x y`.

#### Extended Normed Groups

- **`ExNormedAbGroup`**: Extends `ExPseudoNormedAbGroup` and `ExMetricSpace`. Adds the separation axiom `norm-ext`: `norm x = 0` implies `x = 0`. Equivalent to combining the pseudo-normed structure with a Hausdorff metric.

#### Bounded and Real-Valued Normed Groups

- **`BoundedExPseudoNormedAbGroup`**: Extends `ExPseudoNormedAbGroup` with `norm-bounded`: every norm `norm x` is a bounded extended upper real (i.e., finite).
- **`bnorm`**: Promotes the extended norm to an `UpperReal` (a finite upper real) when the group is bounded.
- **`PseudoNormedAbGroup`**: Extends `BoundedExPseudoNormedAbGroup` and `PseudoMetricSpace`. Overrides `norm` to take values in `Real`, giving an ordinary real-valued (pseudo-)norm.
- **`lnorm`**: Real-valued norm function `lnorm x : Real` for a `PseudoNormedAbGroup`.
- **`lnorm>=0`**: `0 <= lnorm x`.
- **`lnorm_zro`**: `lnorm 0 = 0`.
- **`lnorm_+`**: Triangle inequality for `lnorm`: `lnorm (x + y) <= lnorm x + lnorm y`.
- **`lnorm-ldist`**: `ldist x y = lnorm (x - y)` (real-valued distance from norm).
- **`lnorm_BigSum`**: Triangle inequality for finite sums: `lnorm (BigSum l) <= sum of lnorm (l j)`.
- **`lnorm_-`**: Symmetry: `lnorm (x - y) = lnorm (y - x)`.
- **`lnorm_-left`**: `lnorm x - lnorm y <= lnorm (x - y)`.
- **`lnorm_-right`**: `lnorm y - lnorm x <= lnorm (x - y)`.
- **`lnorm_-_abs`**: Reverse triangle inequality: `|lnorm x - lnorm y| <= lnorm (x - y)`.
- **`NormedAbGroup`**: Extends `PseudoNormedAbGroup` and `ExNormedAbGroup`. Real-valued normed abelian group with the separation axiom — the standard notion of a normed group.

#### Morphisms of Normed Groups

- **`UniformNormedAbGroupMap`**: Extends `UniformMetricMap` and `TopAbGroupMap`. Uniformly continuous group homomorphism between extended pseudo-normed groups, characterized by `func-norm-uniform`: for every `eps > 0` there exists `delta > 0` such that `norm x < delta` implies `norm (func x) < eps`.
- **`NormedAbGroupMap`**: Extends `UniformNormedAbGroupMap` and `MetricMap`. Norm-decreasing (short/non-expansive) group homomorphism: `norm (func x) <= norm x`. This automatically yields uniform continuity and metric non-expansiveness.
- **`NormedIsometricMap`**: Extends `NormedAbGroupMap` and `IsometricMap`. Group homomorphism preserving the norm exactly: `norm (func x) = norm x`, equivalently a metric isometry.

#### Complete Normed Groups

- **`CompleteExNormedAbGroup`**: Extends `ExNormedAbGroup`, `CompleteExMetricSpace`, and `CompleteTopAbGroup`. Cauchy-complete extended normed abelian group.
- **`CompleteNormedAbGroup`**: Extends `CompleteExNormedAbGroup`, `NormedAbGroup`, and `CompleteMetricSpace`. The standard notion of a Banach-style complete real-normed abelian group.

#### Bilinear Maps

- **`bilinear-locally-uniform`**: Given a bilinear map `f : A -> B -> C` between (extended pseudo-)normed groups (with `A`, `B` bounded) that is additive in each argument and satisfies the submultiplicative norm bound `norm (f x y) <= norm x * norm y`, produces a `LocallyUniformMap` from the product `A ⨯ B` to `C`. Used to lift bilinear operations (e.g., multiplication) to locally uniformly continuous maps.
