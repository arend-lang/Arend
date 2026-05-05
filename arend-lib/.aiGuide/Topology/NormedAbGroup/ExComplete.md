### Topology.NormedAbGroup.ExComplete

Completion construction for extended pseudo-normed abelian groups, producing complete extended normed abelian groups with universal lifting properties.

#### Universal Lifting

- **`dense-normed-lift`**: Lifts a normed abelian group map `g : X -> Z` along a dense normed isometric embedding `f : X -> Y` to a normed map `Y -> Z`, where `Z` is a complete extended normed abelian group. Combines `dense-metric-lift` and `dense-topAb-lift` while preserving the norm.
- **`dense-normed-lift.char`**: Characterizing equation `dense-normed-lift f fd g (f x) = g x` showing the lift extends `g` along `f`.

#### Completion

- **`ExNormedAbGroupCompletion`**: The completion of an extended pseudo-normed abelian group `X` as a `CompleteExNormedAbGroup`. Combines `ExMetricCompletion` and `TopAbGroupCompletion`, with norm extended to Cauchy filters.
- **`ExNormedAbGroupCompletion.filter-norm_dist`**: Identifies the norm of a regular Cauchy filter `F` with its distance to the point filter at `0`.
- **`ExNormedAbGroupCompletion.norm-cont`**: Continuity of the norm `ExMetricCompletion X -> ExUpperRealMetric` on the completion.
- **`completion-exNormed-isometry`**: The canonical normed isometric embedding `X -> ExNormedAbGroupCompletion X`, extending `completion-ex-isometry` with group structure preservation.

#### Separated Reflection

- **`SeparatedNormedAbGroupReflection`**: The Hausdorff/separated reflection of an extended pseudo-normed abelian group `X`, obtained as the quotient `Quot X` by the equivalence `norm (x - x') = 0`. An `ExNormedAbGroup` instance with induced group operations and norm.
- **`SeparatedNormedAbGroupReflection.Quot`**: The underlying quotient set `Quotient {X} (\lam x x' => X.norm (x - x') = 0)`.
- **`SeparatedNormedAbGroupReflection.inN`**: The canonical projection `X -> Quot X` sending `x` to its equivalence class.
- **`SeparatedNormedAbGroupReflection.~-nequiv`**: If `norm (x - x') = 0` then `inN x = inN x'` in the reflection.
- **`SeparatedNormedAbGroupReflection.inN-isometry`**: The projection `inN` as a normed isometric map `X -> SeparatedNormedAbGroupReflection X`.

#### Separated Completion

- **`separated-completion`**: The induced normed isometric map `SeparatedNormedAbGroupReflection X -> ExNormedAbGroupCompletion X`, factoring the completion through the separated reflection.
- **`separated-completion.isDense`**: The image of the separated reflection is dense in the completion.
