### Topology.MetricSpace.UpperReal

Metric space structure on extended upper reals and upper reals, with completeness.

This module equips `ExUpperReal` and `UpperReal` with a complete extended metric space structure where the distance between two points is itself an upper real. The distance `dist x y` is presented via its filter of rational upper bounds: a rational `q` bounds the distance iff there exists a smaller nonnegative rational `a` such that each point is bounded by the other plus `a`. Completeness is established by constructing limit points directly from Cauchy filters via `cfPoint`, which collects rationals that strictly bound some set in the filter. The inclusion `UpperReal -> ExUpperReal` is shown to be an isometric embedding, transferring the metric structure.

#### Metric Instances

- **`ExUpperRealMetric`**: `CompleteExMetricSpace` instance on `ExUpperReal`. The distance `dist x y` is the upper real whose `q`-membership asserts existence of a nonnegative rational `a < q` with `x <= y + a` and `y <= x + a`. Includes closure and roundedness of the distance filter.
- **`UpperRealMetric`**: `CompleteExMetricSpace` instance on `UpperReal`, inheriting `dist` from `ExUpperRealMetric`.

#### Distance Projections

- **`ExUpperRealMetric.dist-left`**: From `(dist x y).U a` extracts the bound `x <= y + a`.
- **`ExUpperRealMetric.dist-right`**: From `(dist x y).U a` extracts the bound `y <= x + a`.

#### Completeness Construction

- **`ExUpperRealMetric.cfPoint`**: Builds the limit `ExUpperReal` of a Cauchy filter `F : Set ExUpperReal -> \Prop`. A rational `q` is in its upper-set filter iff there exist `a < q` and a set `U \in F` such that every `x \in U` satisfies `x < a`. Used to witness completeness of the metric.

#### Isometric Embedding

- **`upper-ex-isometry`**: The inclusion `UpperReal -> ExUpperReal` as an `IsometricMap` from `UpperRealMetric` to `ExUpperRealMetric`, showing the metric on `UpperReal` is the restriction of the one on `ExUpperReal`.
