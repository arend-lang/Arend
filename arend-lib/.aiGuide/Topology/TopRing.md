### Topology.TopRing

Topological algebraic structures: continuous semigroups, monoids, rings, and near-(skew)fields.

This module layers the standard algebraic hierarchy onto topological spaces by requiring the binary operations to be continuous as maps out of the product topology. The `TopRing` class combines additive (via `TopAbGroup`) and multiplicative (via `TopMonoid`) continuity over a single topological space. `NearSkewField` and `NearField` weaken the field axioms by only requiring the set of multiplicative inverses to be topologically dense, which is enough to obtain rigidity results for continuous module maps — captured by the `nearField-*-unique` lemmas, which show that continuous maps into a Hausdorff topological module are determined by their behavior under scalar multiplication.

#### Topological Algebraic Classes

- **`TopSemigroup`**: Extends `TopSpace` and `Semigroup`. A semigroup whose multiplication `* : E × E → E` is continuous, witnessed by the field `*-cont` as a `ContMap` from the product topology.
- **`TopMonoid`**: Extends `TopSemigroup` and `Monoid`. Adds `pow-cont`, the lemma that the power map `pow __ n : E → E` is continuous for any natural number `n`.
- **`TopRing`**: Extends `TopAbGroup`, `TopMonoid`, and `Ring`. A ring whose addition, negation, and multiplication are all continuous on the underlying topological space.

#### Near-Fields

- **`NearSkewField`**: Extends `TopRing`. A topological ring in which the set of multiplicative units (`Monoid.Inv x`) is dense, expressed via `inv-dense : IsDenseSet`. Captures rings where invertible elements approximate every element, generalizing skew fields in a topological setting.
- **`NearField`**: Extends `NearSkewField` and `CRing`. The commutative version: a topological commutative ring with a dense set of units.

#### Uniqueness Lemmas for Maps into Hausdorff Modules

- **`nearField-map-unique`**: Two continuous maps `f g : ContMap (TopSub U) Y` from an open subset `U` of a `NearSkewField` `R` into a `HausdorffTopLModule R` agree on every point `h ∈ U`, provided they agree after scaling by `h` (i.e., `h *c f (h, Uh) = h *c g (h, Uh)`). Used to extend equalities from invertible elements to all of `U` via density.
- **`nearField-tmap-unique`**: Global version of the above: continuous maps `f g : ContMap R Y` into a Hausdorff topological `R`-module are equal whenever `h *c f h = h *c g h` for all `h : R`. The key rigidity principle for continuous module maps out of a near-skew-field.
