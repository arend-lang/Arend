### Algebra.Ring.UnitAlgebra

Adjoins a unit to a (possibly non-unital) pseudo-algebra over a commutative ring, producing a unital algebra via the standard `R ⊕ A` construction.

#### Unitization Construction

- **`UnitAlgebra`**: Instance making `\Sigma R A` into an `AAlgebra R` for a `CRing R` and `PseudoAlgebra R A`. Underlying set is pairs `(r, x)` with multiplication `(a, x) * (b, y) = (a*b, a*c y + b*c x + x*y)`, identity `(1, 0)`, scalar action `a *c (b, x) = (a*b, a*c x)`, and coefficient map `a ↦ (a, 0)`. This is the free way to add a multiplicative identity to a pseudo-algebra.
- **`UnitCAlgebra`**: Instance extending `UnitAlgebra` to a `CAlgebra R` when the input `A` is a `PseudoCAlgebra R` (commutative pseudo-algebra), adding the `*-comm` field.

#### Embedding

- **`unit-algebra`**: The canonical `AddGroupHom A (UnitAlgebra R A)` sending `x ↦ (0, x)`, embedding the original pseudo-algebra into its unitization as the augmentation kernel.

#### Iterated Addition Lemmas

- **`UnitAlgebra.*n-comm`**: `n *n (a, x) = (n *n a, n *n x)` — natural-number iterated addition acts componentwise.
- **`UnitAlgebra.*n-comm1`**: First-component projection of the above: `(n *n (a, x)).1 = n *n a`.
- **`UnitAlgebra.*n-comm2`**: Second-component projection: `(n *n (a, x)).2 = n *n x`.
