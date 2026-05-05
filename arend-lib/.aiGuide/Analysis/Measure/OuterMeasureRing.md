### Analysis.Measure.OuterMeasureRing

Outer measure rings as Boolean pseudo-rings equipped with a subadditive norm valued in the lower extended reals, plus a framework for extending premeasures to measurable elements via filter limits. **Note: the entire module is currently commented out (TODO: delete or rewrite for server2).**

#### Normed Group Structures

- **`LowerPseudoNormedAbGroup`**: Class extending `ValuedPseudoNormedAbGroup` whose norm valuation is fixed to `ExtendedPseudoMetricSpace.LowerRealMetricValueOrder`, i.e. distances/norms valued in lower reals.
- **`PseudoOuterPremeasureRing`**: Class extending `BooleanPseudoRing` and `ValuedPseudoNormedAbGroup` modeling a Boolean ring with a lower-real valued outer-measure-style norm. Carries:
  - **`norm-outer-measure`**: Subadditivity on joins: `norm (x ∨ y) <= norm x + norm y`.
  - **`norm-outer-mono`**: Monotonicity: `x <= y -> norm x <= norm y`.
  - Derives `norm_negative` (negation invariance) and `norm_+` (subadditivity for `+`) from the join versions via `+_diff`.

#### Measurable Completion

- **`ExtendedMeasure`**: A `\noclassifying` class parametrized by a `LowerPseudoNormedAbGroup R`, a `PremeasurePseudoRing M`, an additive group homomorphism `inc : M -> R`, and a compatibility witness `comp : norm a <= norm (inc a)`. Provides the framework to extend the premeasure on `M` to elements of `R` reachable as filter limits.
  - **`IsMeasurable`**: Predicate on `a : R` asserting existence of a proper filter `F` on `M` whose `inc`-image converges to `a`. The intended notion of measurability via Cauchy/limit completion.
  - **`measurable-cauchy`**: If the `inc`-image of a proper filter `F` is Cauchy in `R`, then the `norm`-image of `F` is Cauchy in `InfReal`. Used to build the canonical extended-real-valued measure.
  - **`measure-pair`**: From `IsMeasurable a`, produces a pair `(v : InfReal, F : ProperFilter M)` with `inc(F) -> a` in `R` and `norm(F) -> v` in `InfReal`. Establishes uniqueness of the limit norm `v` (the measure) via a delicate `\level`-proof using filter-meet, ball neighborhoods, and the triangle inequality.

#### Commented-Out Stubs

- **`measurable-closed`**, **`measurable-closed'`**: Skeleton lemmas (with `{?}` holes) intended to show that measurable elements are closed under binary operations `op : M -> M -> M` (resp. `op : R -> R -> R`) via product filters. Left as future work.
