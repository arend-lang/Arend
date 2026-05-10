### Topology.TopRing.Real

The real numbers as a topological near-field, combining the field structure with the metric topology.

This module provides the `NearField` instance for `Real`, instantiating the topological ring/near-field interface from `Topology.TopRing` with the real-number field structure (`RealField`) and the canonical metric/normed topology on `Real` (`RealNormed`). It witnesses that multiplication is continuous and that invertible elements are dense, making `Real` a topological near-field suitable for use in analysis and topology developments downstream.

#### Instances

- **`RealNearField`**: `NearField Real` instance, extending `CRing` via `RealField` and `TopAbGroup` via `RealNormed`. Supplies the continuity of multiplication (`*-cont`) and density of invertible elements (`inv-dense`), packaging `Real` as a topological near-field.
