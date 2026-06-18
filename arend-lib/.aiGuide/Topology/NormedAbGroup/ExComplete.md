### Topology.NormedAbGroup.ExComplete

Completion of extended pseudo-normed abelian groups into complete extended normed abelian groups.

This module constructs the universal completion of an `ExPseudoNormedAbGroup` `X` as a `CompleteExNormedAbGroup`, extending the metric-space completion with compatible group structure and norm. The norm of a regular Cauchy filter is defined via its underlying upper real, characterized as the distance to the zero filter. A separation reflection collapses points at zero distance via a quotient, and the composite isometry into the completion is dense, providing the universal property used to lift normed maps from `X` into any complete target.

#### Universal Lifting

- **`dense-normed-lift`**: Given a dense normed isometric embedding `f : X -> Y` and a normed group map `g : X -> Z` into a complete extended normed abelian group `Z`, produces the unique `NormedAbGroupMap Y -> Z` extending `g`. Combines `dense-metric-lift` and `dense-topAb-lift` while preserving norms.
- **`dense-normed-lift.char`**: Computes the lifted map on the image: `dense-normed-lift f fd g (f x) = g x`.

#### Completion as Normed Group

- **`ExNormedAbGroupCompletion`**: The completion of `X : ExPseudoNormedAbGroup` as a `CompleteExNormedAbGroup`. Combines `ExMetricCompletion X` (metric structure) and `TopAbGroupCompletion X` (topological group structure). The norm of a regular Cauchy filter `F` is the upper real whose underlying set of rationals consists of `q` such that some smaller `r < q` and some `U ∈ F` satisfy `(norm x).U r` for all `x ∈ U`.
- **`ExNormedAbGroupCompletion.filter-norm_dist`**: Identifies the filter norm with the metric distance to the zero point filter: `norm F = dist F (pointCF 0)`.
- **`ExNormedAbGroupCompletion.norm-cont`**: The norm map on the completion is continuous into `ExUpperRealMetric`.

#### Canonical Embedding

- **`completion-exNormed-isometry`**: The canonical isometric inclusion `X -> ExNormedAbGroupCompletion X` as a `NormedIsometricMap`, packaging `completion-ex-isometry` with additivity.

#### Separation Reflection

- **`SeparatedNormedAbGroupReflection`**: For `X : ExPseudoNormedAbGroup`, constructs an `ExNormedAbGroup` on the quotient by the equivalence `x ~ x' ⇔ norm (x - x') = 0`, eliminating the failure of separation in the pseudo-normed setting. Defines `zro`, `+`, `negative`, and `norm` on the quotient, with each operation verified to respect the equivalence.
- **`SeparatedNormedAbGroupReflection.Quot`**: The underlying setoid quotient `Quotient {X} (\lam x x' => norm (x - x') = 0)`.
- **`SeparatedNormedAbGroupReflection.inN`**: The canonical projection `X -> Quot X`.
- **`SeparatedNormedAbGroupReflection.~-nequiv`**: If `norm (x - x') = 0` then `inN x = inN x'` in the quotient.
- **`SeparatedNormedAbGroupReflection.inN-isometry`**: Packages `inN` as a `NormedIsometricMap` from `X` to its separated reflection.

#### Reflection-to-Completion Map

- **`separated-completion`**: The canonical normed isometric map `SeparatedNormedAbGroupReflection X -> ExNormedAbGroupCompletion X`, sending `in~ x` to the principal Cauchy filter `pointCF x`. Well-definedness uses that points at zero distance generate the same filter, established via a ball-shrinking argument with the triangle inequality.
- **`separated-completion.isDense`**: The map `separated-completion` is dense in the completion, so the completion is the closure of the separated reflection.
