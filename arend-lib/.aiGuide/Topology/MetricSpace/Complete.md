### Topology.MetricSpace.Complete

Completion of pseudometric spaces into complete metric spaces, with universal property for extending metric maps.

#### Universal Property

- **`dense-metric-lift`**: Given an isometric map `f : X -> Y` that is a weakly dense uniform embedding and a metric map `g : X -> Z` into a complete metric space `Z`, produces the unique metric map `Y -> Z` extending `g`. Combines `weaklyDense-uniform-lift` with the metric structure on the target.

#### Metric Completion

- **`MetricCompletion`**: The completion of a `PseudoMetricSpace X` as a `CompleteMetricSpace`, built on top of `UniformStrongCompletion X`. The distance is obtained by lifting `ldist : X × X -> R` to the product of strong completions via `weaklyDense-lift`.
  - **`dist-cover`**: The continuous distance map `StrongCompletion X ⨯ StrongCompletion X -> RealNormed` extending `ldist`.
  - **`dist-char`**: On point filters, `dist-cover (pointSCF x, pointSCF y) = ldist x y`, showing the embedding is isometric.
  - **`dist-refl-lem`**: Reflexivity: `dist-cover (F, F) = 0` for any strongly regular Cauchy filter.
  - **`filter-dist-triang`**: Triangle inequality for the lifted distance on Cauchy filters.
  - **`dist-neighborhood`**: If `F` and `G` both contain an open ball `OBall (eps/4) x`, then `dist-cover (F, G) < eps`; used to verify the uniform structure on the completion.
