### Arith.Real.UpperRealLattice

Complete lattice structure on extended upper reals, with compatibility lemmas between lattice operations and arithmetic.

This module equips `ExUpperReal` with a `CompleteLattice` instance by defining infinitary meets and joins via the upper-set presentation: a meet's upper set collects rationals above *some* member, while a join's upper set requires being strictly above a rational that bounds *every* member (the strict-above buffer ensures roundedness). The accompanying lemmas establish that scalar multiplication, addition, and squaring distribute over joins — these are the key compatibilities needed when reasoning about suprema of upper-real-valued expressions, e.g. when bounding limits or interpreting algebraic operations on the lattice.

#### Complete Lattice Instance

- **`ExUpperRealLattice`**: `CompleteLattice` instance on `ExUpperReal`, extending the `ExUpperRealAbMonoid` lattice structure with infinitary `Meet` (upper set `{q | ∃ j, (g j).U q}`: above some member) and `Join` (upper set `{q | ∃ r < q, ∀ j, (g j).U r}`: strictly above a rational bounding every member, ensuring `U-rounded`).

#### Arithmetic-Join Compatibility

- **`*n_join`**: Binary version: `n *n (x ∨ y) = n *n x ∨ n *n y`.
- **`+_join`**: Binary self-addition over binary join: `(x ∨ y) + (x ∨ y) = (x + x) ∨ (y + y)`.
- **`join-square`**: Binary version: `(x ∨ y) * (x ∨ y) = x * x ∨ y * y`.
