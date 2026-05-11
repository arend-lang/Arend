### Analysis.Measure.OuterMeasureRing

Outer (sub-additive) premeasures and the construction of measurable elements as Cauchy-filter limits of a premeasure ring inside a normed completion.

This module is currently entirely commented out (`TODO[server2]: Delete or rewrite this`) but sketches the abstract setup for outer measures: a Boolean pseudo-ring whose norm is sub-additive and monotone (rather than additive) plays the role of an outer premeasure, and an "extended measure" is presented as an inclusion `inc : M -> R` of such a premeasure ring into a lower-real-valued normed group, where measurable elements are precisely those that arise as filter limits from `M`. The Cauchy property of the norm filter is shown to follow from the Cauchy property of the inclusion filter, allowing each measurable element to be paired with a well-defined `InfReal` measure value via completion of `InfReal` as a uniform space.

#### Normed Group Variants

- **`LowerPseudoNormedAbGroup`**: A `ValuedPseudoNormedAbGroup` whose value order is fixed to `ExtendedPseudoMetricSpace.LowerRealMetricValueOrder`, i.e. norms live in the lower reals.

#### Outer Premeasure Rings

- **`PseudoOuterPremeasureRing`**: Extends `BooleanPseudoRing` and `ValuedPseudoNormedAbGroup` with a sub-additive, monotone norm interpreted as an outer premeasure. Provides `norm-outer-measure` (`norm (x ∨ y) <= norm x + norm y`) and `norm-outer-mono` (`x <= y -> norm x <= norm y`), and derives `norm_negative` and `norm_+` automatically.

#### Extended Measures via Filter Completion

- **`ExtendedMeasure`**: A `\noclassifying` class parameterized by a `LowerPseudoNormedAbGroup R`, a `PremeasurePseudoRing M`, an additive group homomorphism `inc : M -> R`, and a comparison `comp : norm a <= norm (inc a)`. Packages the data needed to extend a premeasure on `M` to measurable elements of `R`.
  - **`IsMeasurable`**: Predicate stating that `a : R` is the limit (under `R.IsFilterLimit`) of `SetFilter-map inc F` for some proper filter `F` on `M`; this is the abstract notion of being approximable by elements of the premeasure ring.
  - **`measurable-cauchy`**: Lifts a Cauchy filter on `R` (via `inc`) to a Cauchy filter of norms on `InfRealUniformSpace`, using the bounded subset structure on `M` to control tail behavior.
  - **`measure-pair`**: For each measurable `a`, produces an `InfReal` value `v` together with a proper filter `F` whose `inc`-image converges to `a` and whose norm-image converges to `v`. The proof uses a uniqueness `\level` argument establishing that any two such `InfReal` candidates coincide via antisymmetry, and uses `CompleteCoverSpace.filter-point` on `InfReal` to produce the limit point.

#### Sketched (Commented) Future Extensions

- **`measurable-closed` / `measurable-closed'`** *(commented out)*: Intended lemmas that measurability is closed under binary operations, by constructing a product-style proper filter `H` on `M` from filters on the operands.
