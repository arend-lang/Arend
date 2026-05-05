### Topology.MetricSpace

Metric and pseudometric spaces built on extended upper reals, with their associated uniform structures, open balls, continuity characterizations, completeness, and structure-preserving maps.

#### Pseudometric Space Classes

- **`ExPseudoMetricSpace`**: Extended pseudometric space extending `UniformSpace`. Carries a distance `dist : E -> E -> ExUpperReal` satisfying reflexivity (`dist x x = 0`), symmetry, triangle inequality, and a uniform-cover characterization via balls of rational radius. The uniform structure is induced by the metric.
- **`ExMetricSpace`**: Extended metric space, extending `ExPseudoMetricSpace` and `SeparatedCoverSpace`. Adds extensionality `dist x y = 0 -> x = y`; equivalent to the cover structure being separated.
- **`PseudoMetricSpace`**: Pseudometric space with real-valued distance, extending `ExPseudoMetricSpace` and `StronglyRegularUniformSpace`. Overrides `dist` to land in `Real` and provides a strongly-star-refining uniformity.
- **`MetricSpace`**: Metric space, extending `PseudoMetricSpace` and `ExMetricSpace`.
- **`CompleteMetricSpace`**: Metric space that is also a `StronglyCompleteUniformSpace`.
- **`CompleteExMetricSpace`**: Extended metric space extending `CompleteUniformSpace`, with completeness characterized by the existence of a center point whose balls eventually lie in any proper Cauchy filter.

#### Distance Lemmas

- **`dist>=0`**: Distance is non-negative: `0 <= dist x y`.
- **`ldist`**: Real-valued distance for `PseudoMetricSpace`: `ldist x y : Real`.
- **`ldist>=0`**, **`ldist-refl`**, **`ldist-symm`**, **`ldist-triang`**: Real-valued analogues of the metric axioms.

#### Open Balls

- **`OBall`**: Open ball `OBall eps x = {y | dist x y < eps}` for rational radius `eps`.
- **`OBall-center`**: An open ball of positive radius contains its center.
- **`OBall-open`**: Open balls are open in the underlying topology.
- **`OBall-center_<=<`**: `single x <=< OBall eps x`: the singleton at the center is way-below the ball.
- **`OBall_<=*`**: A smaller ball star-refines a larger ball: `delta < eps -> OBall delta x <=* OBall eps x`.
- **`OBall_s<=*`**: Strong star-refinement version for `PseudoMetricSpace`.
- **`IsBoundedSet`**: A set `S` is bounded if `∃ B > 0, x ∈ S, ∀ y ∈ S. (dist x y).U B`.

#### Topology and Uniformity Characterizations

- **`cauchy-ball`**: Any Cauchy cover contains a member containing some ball around any given point.
- **`dist_open`**: A set is open iff every point has an open ball contained in it.
- **`<=<-ball`**: If `single x <=< U`, some open ball around `x` lies in `U`.
- **`cauchyFilter-metric-char`**: A filter is Cauchy iff for every `eps > 0` it contains some open ball of radius `eps`.

#### Continuity Characterizations

- **`metric-contAt-char`**: Epsilon-delta characterization of continuity at a point using rational `eps`/`delta` and the `.U` predicate on extended upper reals.
- **`metric-contAt-real`**: Real-valued epsilon-delta characterization of continuity at a point for `PseudoMetricSpace`.
- **`metric-ext`**: Extensionality for `ExMetricSpace`: if `(dist x y).U eps` for all rational `eps > 0`, then `x = y`.

#### Map Classes

- **`LocallyUniformMetricMap`**: Locally uniformly continuous map between extended pseudometric spaces, extending `LocallyUniformMap`. Defined by a metric locally-uniform-continuity condition `func-dist-locally-uniform`.
  - **`fromLocallyUniformMap`**: Promotes any `LocallyUniformMap` to a `LocallyUniformMetricMap`.
  - **`makeLocallyUniformMap2`**: Builds a locally uniform map from a binary function `f : X -> Y -> Z` given a joint epsilon-delta condition.
- **`UniformMetricMap`**: Uniformly continuous map between extended pseudometric spaces, extending `LocallyUniformMetricMap` and `UniformMap`. Defined by `func-dist-uniform`: `∀ eps > 0, ∃ delta > 0, (dist x x').U delta -> (dist (func x) (func x')).U eps`.
  - **`fromUniformMap`**: Promotes any `UniformMap` to a `UniformMetricMap`.
- **`MetricMap`**: Non-expansive (1-Lipschitz) map: `dist (func x) (func y) <= dist x y`.
- **`IsometricMap`**: Isometry: `dist (func x) (func y) = dist x y`, extending `MetricMap`.
- **`func-ldist`**: Real-valued non-expansiveness for a `MetricMap` between `PseudoMetricSpace`s.
- **`func-lisometry`**: Real-valued isometry equation for an `IsometricMap` between `PseudoMetricSpace`s.

#### Transfer Constructions

- **`ExPseudoMetricTransfer`**: Pulls an extended pseudometric structure on `X` along a function `f : X -> Y` by `dist x x' := dist (f x) (f x')`.
- **`ExMetricTransfer`**: Pulls an extended metric structure along an injective function `f : X -> Y`.
- **`PseudoMetricTransfer`**: Real-valued pseudometric pulled back along `f : X -> Y`.
- **`MetricTransfer`**: Metric pulled back along an injective `f : X -> Y`.
