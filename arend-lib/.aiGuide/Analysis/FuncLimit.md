### Analysis.FuncLimit

Convergence of parametrized families of functions and limit operators in cover/uniform/metric/topological-group settings.

This module defines what it means for a family `f : I -> X -> Y` indexed by a directed set `I` to converge as a function, by reducing to continuity of the uncurried map on the product of `I` (viewed as a directed cover space) with `X`. From this single notion `IsFuncConvergent` it derives the pointwise limit, the limiting cover map into a complete codomain, and characterizations specialized to uniform spaces, (extended pseudo)metric spaces, and topological abelian groups via Cauchy-style ε/N conditions. It also formalizes uniform convergence and a more general "limit along a directed set" predicate `IsCoverLimit`, including its instantiation to limits at a point in a topological space via the directed set of open neighborhoods.

#### Core Convergence

- **`IsFuncConvergent`**: A family `f : I -> X -> Y` (with `I` directed, `X Y` cover spaces) is convergent iff its uncurrying is a `CoverMap` from `DirectedCoverSpace I ⨯ X` to `Y`.
- **`funcConv-pointwise`**: Functional convergence implies pointwise convergence: for each `x : X`, `f __ x : I -> Y` is convergent.
- **`funcLimit`**: When `Y` is a `CompleteCoverSpace`, the pointwise limit `\lam x => limit (f __ x) ...` is itself a `CoverMap X Y`.

#### Characterizations of Functional Convergence

- **`funcConvergent-char`**: Convergence of `f : I -> CoverMap X Y` is equivalent to: every cauchy cover `D` of `Y` is refined by a cauchy cover of `X` whose elements `U` admit some tail index `N` and `V ∈ D` with `f n x ∈ V` for all `n ≥ N`, `x ∈ U`. Includes the reverse direction `conv`.
- **`funcConvergent-uniform-char`**: Uniform-space variant: replaces cauchy covers of `Y` with uniform covers `E`, requiring per-point witnesses `W ∈ E` containing `f n x` for `n ≥ N`. Includes `split` (a regular-preuniform refinement form) and `conv`.
- **`funcCovergent-metric-char`**: Metric variant: convergence is equivalent to the Cauchy condition `dist (f n x) (f N x) < eps` holding uniformly on a cauchy cover, for every rational `eps > 0`. Includes `conv`.
- **`funcCovergent-topAb-char`**: Topological abelian group variant: uses neighborhoods `U` of `0` and the difference `f n x - f N x ∈ U`. Includes `conv`.

#### Uniform Convergence

- **`IsUniFuncConvergent`**: Uniform functional convergence: for every uniform cover `D` of `Y` there is an `N : I` such that for all `x` some `V ∈ D` contains `f n x` for all `n ≥ N`.
- **`funcCovergent-uni`**: Uniform convergence of cover maps into a uniform space implies functional convergence.
- **`metric-uni-funcConvergent`**: In an extended pseudometric codomain, `IsUniFuncConvergent f` is equivalent to the standard ε/N Cauchy condition uniformly in `x`.

#### Limits Along Cover Families

- **`IsCoverLimit`**: Predicate that `L : Set.Total T -> Y` is the limit of a partial family `f : Set.Total S -> Y` (with `S ⊆ I × X`) into a topological abelian group: for every neighborhood `V` of `0`, a cauchy cover of `X` witnesses that `L x - f (n, x)` lies in `V` for sufficiently large `n` whenever `(n, x) ∈ S`.
- **`IsCoverLimit.unique`**: In a Hausdorff topological abelian group, two such limits agree on points `x` for which `S` contains arbitrarily-late indices over `x`.

#### Limits at a Point in a Topological Space

- **`PointDirectedSet`**: The directed set of pointed open neighborhoods of `a : X` together with a chosen point in each, ordered by reverse inclusion of the neighborhood; expresses "filter towards `a`" as a directed set.
- **`IsOpenLimit`**: `L` is the limit of `f` as the first coordinate approaches `c : Z`, defined as `IsCoverLimit` over `PointDirectedSet c`.
- **`openLimit-char`**: Equivalent characterization of `IsOpenLimit` directly in terms of open neighborhoods `W` of `c`: for every neighborhood `V` of `0 : Y`, a cauchy cover of `X` witnesses `L x - f (z, x) ∈ V` for all `z ∈ W` with `(z, x) ∈ S`.
- **`openLimit-char.unique`**: Hausdorff uniqueness of the open limit on points where `S` is dense over `c` along open neighborhoods.
