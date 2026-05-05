### Topology.TopRing

Topological algebraic structures combining ring/monoid operations with continuity, including near-fields characterized by a dense set of invertible elements.

#### Topological Algebraic Structures

- **`TopSemigroup`**: Extends `TopSpace` and `Semigroup` with continuity of multiplication via `*-cont : ContMap (ProductTopSpace \this \this) \this (\lam s => s.1 * s.2)`.
- **`TopMonoid`**: Extends `TopSemigroup` and `Monoid`; a monoid whose multiplication is jointly continuous.
- **`TopRing`**: Extends `TopAbGroup`, `TopMonoid`, and `Ring`; a ring with continuous addition, negation, and multiplication.

#### Near-Fields

- **`NearSkewField`**: Extends `TopRing` with `inv-dense : IsDenseSet (\lam (x : E) => Monoid.Inv x)`, asserting that the set of invertible elements is topologically dense.
- **`NearField`**: Extends `NearSkewField` and `CRing`; a commutative near-skew-field.

#### Uniqueness Lemmas for Maps into Hausdorff Modules

- **`nearField-map-unique`**: For `R : NearSkewField`, `Y : HausdorffTopLModule R`, and continuous maps `f g : ContMap (TopSub U) Y` on an open subset `U ⊆ R`, if `h *c f (h, Uh) = h *c g (h, Uh)` for all `h ∈ U`, then `f (h, Uh) = g (h, Uh)`. Uses density of invertibles to cancel the scalar.
- **`nearField-tmap-unique`**: Global version: for continuous `f g : ContMap R Y` with `h *c f h = h *c g h` for all `h`, conclude `f h = g h`. Useful when a map into a Hausdorff topological module is determined by its scalar-multiplied values.
