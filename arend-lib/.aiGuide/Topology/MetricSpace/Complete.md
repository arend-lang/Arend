### Topology.MetricSpace.Complete

Completion of pseudometric spaces into complete metric spaces, lifting metric maps along weakly dense uniform embeddings.

This module connects the abstract uniform-space completion machinery to the concrete metric setting. The completion of a pseudometric space `X` is built on top of the strong uniform completion (so its underlying point set is the space of strongly regular Cauchy filters), with the distance function obtained by lifting the original distance along the dense embedding into the product completion via `weaklyDense-lift`. This gives a universal complete metric space receiving a metric map from `X`, into which any metric map to a complete target factors uniquely.

#### Universal Lifting

- **`dense-metric-lift`**: Given an isometric map `f : X -> Y` that is a weakly dense uniform embedding and a metric map `g : X -> Z` into a complete metric space `Z`, produces the unique metric map `Y -> Z` extending `g`. Combines `weaklyDense-uniform-lift` with a proof that the distance is preserved.

#### Metric Completion

- **`MetricCompletion`**: Instance constructing a `CompleteMetricSpace` from any `PseudoMetricSpace X`. Its underlying uniform structure is `UniformStrongCompletion X` (strongly regular Cauchy filters), and its distance is obtained by lifting `ldist : X × X -> R` along the product dense embedding `strongCompletion × strongCompletion`.
  - **`dist-cover`**: The lifted distance as a cover map `StrongCompletion X ⨯ StrongCompletion X -> RealNormed`, defined via `weaklyDense-lift` of the rational left-distance uniform map `ldist-uniform-map` over the product strongly regular cover space.
  - **`dist-char`**: Characterization on point filters: `dist-cover (pointSCF x, pointSCF y) = ldist x y`, showing the lifted distance restricts to the original distance on `X`.
  - **`dist-refl-lem`**: Reflexivity of the lifted distance: `dist-cover (F, F) = 0` for any strongly regular Cauchy filter `F`.
  - **`filter-dist-triang`**: Triangle inequality for the lifted distance on Cauchy filters: `dist-cover (x, z) <= dist-cover (x, y) + dist-cover (y, z)`.
  - **`dist-neighborhood`**: Locality lemma — if filters `F` and `G` both contain an open ball of radius `eps/4` around a common point `x`, then `dist-cover (F, G)` lies in the upper-real neighborhood `U eps`. Used to bound the completion distance from concrete filter membership data.
