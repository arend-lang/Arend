### Topology.MetricSpace.Nat

Equips the natural numbers with a metric space structure (via the embedding `n ↦ 1/(n+1)` into the reals) and characterizes its uniform/Cauchy structure.

#### Metric Structure

- **`NatMetricSpace`**: `MetricSpace` instance on `Nat` with distance `dist n m = |1/(n+1) - 1/(m+1)|`. Makes `Nat` a metric space whose topology agrees with the discrete-at-infinity behavior of natural numbers.

#### Uniform and Cauchy Cover Characterization

- **`Cover`**: Predicate on a family `C : Set (Set Nat)` stating that some `U ∈ C` contains a tail `[N, ∞)` and every `j < N` is covered by some member of `C`. The combinatorial shape of uniform/Cauchy covers on `Nat`.
- **`uniform-char`**: `NatMetricSpace.isUniform C ↔ Cover C`. Identifies uniform covers with tail-plus-finite-points covers.
- **`cauchy-char`**: `NatMetricSpace.isCauchy C ↔ Cover C`. Same characterization for Cauchy covers.
- **`totallyBounded`**: `IsTotallyBounded NatMetricSpace`. The natural numbers under this metric are totally bounded.
- **`makeUniform`**: For each `N : Nat`, the cover `{[N, ∞)} ∪ {{n} | n < N}` is uniform.
- **`makeCover`**: Same cover is also Cauchy. Useful for building covers indexed by a tail threshold.

#### Filter at Infinity

- **`atTop`**: `CauchyFilter NatMetricSpace` consisting of sets that contain some tail `[N, ∞)`. The canonical Cauchy filter representing "the limit at infinity"; use it to express convergence of sequences `Nat → X` as completion-points of `NatMetricSpace`.
