### Topology.MetricSpace.UpperReal

Equips the (extended) upper reals with their canonical complete extended-metric space structure, where distance measures the gap between one-sided approximations.

#### Metric Instances

- **`ExUpperRealMetric`**: `CompleteExMetricSpace` instance on `ExUpperReal`, defining `dist` together with reflexivity, symmetry, triangle inequality, extensionality, and completeness. The distance is induced by the upper-real arithmetic on `ExUpperReal`.
- **`UpperRealMetric`**: `CompleteExMetricSpace` instance on `UpperReal`, with `dist` inherited by restriction from `ExUpperRealMetric`.

#### Distance Bounds

- **`ExUpperRealMetric.dist-left`**: If `(dist x y).U a` holds for a rational `a`, then `x <= y + a`.
- **`ExUpperRealMetric.dist-right`**: Symmetric counterpart: if `(dist x y).U a`, then `y <= x + a`.

#### Completion

- **`ExUpperRealMetric.cfPoint`**: Builds an `ExUpperReal` from a Cauchy filter `F : Set ExUpperReal -> \Prop`, with upper set `U q := ∃ (a : < q) (U : F) ∀ {x : U} (x < a)`. Used to witness completeness of the metric.

#### Embeddings

- **`upper-ex-isometry`**: The inclusion `UpperReal -> ExUpperReal` (as the identity function on underlying data) is an `IsometricMap` from `UpperRealMetric` into `ExUpperRealMetric`, so the upper-real metric is the restriction of the extended one.
