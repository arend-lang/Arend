### Topology.MetricSpace.Nat

The natural numbers as a metric space, with a metric making `atTop` (the eventual filter) a Cauchy filter.

The metric is defined via the embedding `n ↦ 1/(n+1)` into the rationals, so `dist n m = |1/(n+1) - 1/(m+1)|`. Under this metric, large naturals become close to each other (and to a hypothetical "point at infinity"), which makes `Nat` totally bounded and characterizes its uniform/Cauchy covers in a finite combinatorial way: any cover must contain a tail `(N <=)` together with singletons covering each smaller index. This setup lets `atTop` be realized as a genuine Cauchy filter on `Nat` and supports completion-style arguments for sequences indexed by `Nat`.

#### Metric Structure

- **`NatMetricSpace`**: `MetricSpace` instance on `Nat` with `dist n m = |1/(n+1) - 1/(m+1)|` (using `finv` in `RatField` and lattice absolute value). Provides reflexivity, symmetry, triangle inequality, and separation (`dist-ext`).

#### Cover Characterization

- **`Cover`**: Predicate on a set of subsets `C : Set (Set Nat)` stating that some `U ∈ C` contains a tail `{n | N <= n}` for some `N`, and each index `j < N` is contained in some member of `C`. This is the finite combinatorial shape of any cover compatible with the metric.
- **`uniform-char`**: `NatMetricSpace.isUniform C <-> Cover C` — uniform covers are exactly those of the `Cover` shape.
- **`cauchy-char`**: `NatMetricSpace.isCauchy C <-> Cover C` — Cauchy covers coincide with uniform covers here.
- **`totallyBounded`**: `IsTotallyBounded NatMetricSpace` — `Nat` is totally bounded under this metric.

#### Cover Constructors

- **`makeUniform`**: For any `N : Nat`, the family consisting of the tail `(N <=)` together with the singletons `{n}` for `n < N` is uniform.
- **`makeCover`**: Same family is Cauchy. Use these to produce concrete witnesses when reasoning about uniform/Cauchy properties at a chosen threshold `N`.

#### Eventual Filter

- **`atTop`**: `CauchyFilter NatMetricSpace` whose sets are those `U` containing all sufficiently large naturals (`∃ N, ∀ n ≥ N, U n`). Provides monotonicity, top, finite meets, properness, and the Cauchy condition, making it the canonical "limit at infinity" filter on `Nat`.
