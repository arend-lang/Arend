### Topology.TopRing.Real

Equips the real numbers with the structure of a topological near-field, combining the field structure with the metric topology.

#### Instances

- **`RealNearField`**: `NearField` instance on `Real`, extending `CRing` via `RealField` and `TopAbGroup` via `RealNormed`. Provides continuity of multiplication (`*-cont`) and density of invertible elements (`inv-dense`), making `Real` a topological near-field.
