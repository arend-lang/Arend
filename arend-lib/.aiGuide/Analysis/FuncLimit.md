### Analysis.FuncLimit

Convergence and limits of families of functions indexed by a directed set, with characterizations across cover, uniform, metric, and topological abelian group settings.

#### Function Convergence

- **`IsFuncConvergent`**: A family `f : I -> X -> Y` is convergent iff the uncurried map `DirectedCoverSpace I ⨯ X -> Y` is a cover map.
- **`funcConv-pointwise`**: Function convergence implies pointwise convergence: `IsConvergent (f __ x)` for every `x : X`.
- **`funcLimit`**: Builds the limit cover map `X -> Y` (into a complete cover space) by taking pointwise limits.

#### Characterizations of Convergence

- **`funcConvergent-char`**: Cover-space characterization — convergence is equivalent to a uniform Cauchy condition on covers of `Y`.
- **`funcConvergent-char.conv`**: Forward direction: extracts the Cauchy-cover condition from `IsFuncConvergent`.
- **`funcConvergent-uniform-char`**: Uniform-space characterization in terms of uniform covers `E` of `Y`.
- **`funcConvergent-uniform-char.split`**: Split form of the uniform characterization for regular preuniform spaces, factoring the cover condition through two layers of Cauchy covers.
- **`funcConvergent-uniform-char.conv`**: Forward direction of the uniform characterization.
- **`funcCovergent-metric-char`**: Metric characterization — convergence is equivalent to an `ε`-Cauchy condition `dist (f n x) (f N x) < ε` uniformly on a cover.
- **`funcCovergent-metric-char.conv`**: Forward direction of the metric characterization.
- **`funcCovergent-topAb-char`**: Topological abelian group characterization using neighborhoods of `0` and differences `f n x - f N x`.
- **`funcCovergent-topAb-char.conv`**: Forward direction of the topological abelian group characterization.

#### Uniform Function Convergence

- **`IsUniFuncConvergent`**: Uniform convergence over `X` into a preuniform space `Y`: for every uniform cover `D` of `Y` there is `N` such that for all `x` the tail `f n x` (for `N <= n`) lies in some `V : D`.
- **`funcCovergent-uni`**: Uniform convergence implies (cover-space) function convergence.
- **`metric-uni-funcConvergent`**: Metric reformulation: `IsUniFuncConvergent f` iff for every `ε > 0` there exists `N` such that `dist (f n x) (f N x) < ε` for all `x` and all `n >= N`.

#### Cover Limits with Partial Domains

- **`IsCoverLimit`**: Predicate that `L : Set.Total T -> Y` is the limit of a partial family `f : Set.Total S -> Y` (where `S ⊆ I × X`), expressed via Cauchy covers and neighborhoods of `0` in a topological abelian group `Y`.
- **`IsCoverLimit.unique`**: Uniqueness of cover limits in a Hausdorff topological abelian group, given that the index condition `S (n, x)` is cofinal at `x`.

#### Limits at a Point in a Topological Space

- **`PointDirectedSet`**: Directed set of pointed open neighborhoods of `a : X` together with a chosen point inside, ordered by reverse inclusion; used to take limits "as the neighborhood shrinks to `a`".
- **`IsOpenLimit`**: Limit of `f` as the second component approaches `c : Z`, defined as `IsCoverLimit` over `PointDirectedSet c`.
- **`openLimit-char`**: Reformulates `IsOpenLimit` directly in terms of open neighborhoods `W` of `c` rather than via the directed set.
- **`openLimit-char.unique`**: Uniqueness of open limits in a Hausdorff topological abelian group, assuming every open neighborhood of `c` contains a witness in `S`.
