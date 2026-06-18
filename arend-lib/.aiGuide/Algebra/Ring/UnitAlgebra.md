### Algebra.Ring.UnitAlgebra

Adjoins a multiplicative identity to a pseudo-algebra over a commutative ring, producing a unital algebra.

A pseudo-algebra is an algebra without a required unit element; this module implements the standard "unitization" construction, which freely adjoins a unit by forming the product `R × A` with twisted multiplication `(a, x) * (b, y) = (a*b, a*y + b*x + x*y)`. The element `(1, 0)` serves as the new unit, while the original pseudo-algebra `A` embeds via `x ↦ (0, x)` as a (non-unital) ideal. This construction is the left adjoint to the forgetful functor from unital algebras to pseudo-algebras, and it specializes to commutative algebras when the input is commutative.

#### Main Constructions

- **`UnitAlgebra`**: Instance making `\Sigma R A` into an `AAlgebra R` for a commutative ring `R` and pseudo-algebra `A`. Defines addition componentwise, multiplication via the unitization formula `(a, x) * (b, y) = (a*b, a*c y + b*c x + x*y)`, scalar action only on the first component's product `a *c (b, x) = (a*b, a *c x)`, unit `(1, 0)`, and `coefMap a = (a, 0)`.
- **`unit-algebra`**: Additive group homomorphism `A → UnitAlgebra R A` given by `x ↦ (0, x)`, embedding the pseudo-algebra into its unitization.
- **`UnitCAlgebra`**: Instance extending `UnitAlgebra R A` to a `CAlgebra R` when the input pseudo-algebra `A` is commutative (`PseudoCAlgebra R`), supplying the commutativity of multiplication.

#### Iterated Addition Lemmas

- **`UnitAlgebra.*n-comm`**: Natural-number scalar multiplication distributes through the pair: `n *n (a, x) = (n *n a, n *n x)`.
- **`UnitAlgebra.*n-comm1`**: First-component projection of the above: `(n *n (a, x)).1 = n *n a`.
- **`UnitAlgebra.*n-comm2`**: Second-component projection: `(n *n (a, x)).2 = n *n x`.
