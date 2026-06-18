### Arith.Real.UpperRealLattice

Complete lattice structure on extended upper reals, with compatibility lemmas between lattice operations and arithmetic.

This module equips `ExUpperReal` with a `CompleteLattice` instance by defining infinitary meets and joins via the upper-set presentation: a meet's upper set collects rationals above *some* member, while a join's upper set requires being strictly above a rational that bounds *every* member (the strict-above buffer ensures roundedness). The accompanying lemmas establish that scalar multiplication, addition, and squaring distribute over joins — these are the key compatibilities needed when reasoning about suprema of upper-real-valued expressions, e.g. when bounding limits or interpreting algebraic operations on the lattice.

#### Complete Lattice Instance

- **`ExUpperRealLattice`**: `CompleteLattice` instance on `ExUpperReal`, extending the `ExUpperRealAbMonoid` lattice structure with infinitary `Meet` and `Join`.
- **`Meet`**: Infimum of a family `g : J -> ExUpperReal`. Its upper set is `{q | ∃ j, (g j).U q}` — a rational is above the meet iff it is above some member.
- **`Join`**: Supremum of a family `g : J -> ExUpperReal`. Its upper set is `{q | ∃ r < q, ∀ j, (g j).U r}` — a rational is above the join iff some strictly smaller rational bounds every member, ensuring `U-rounded`.

#### Arithmetic-Join Compatibility

- **`*n_Join`**: For `n ≠ 0`, scalar multiplication distributes over joins: `n *n Join g = Join (λj. n *n g j)`.
- **`*n_Join'`**: Same distributivity assuming the index set is inhabited (avoiding the `n ≠ 0` hypothesis).
- **`+_Join`**: Self-addition distributes over a join: `Join g + Join g = Join (λj. g j + g j)`.
- **`*n_join`**: Binary version: `n *n (x ∨ y) = n *n x ∨ n *n y`.
- **`+_join`**: Binary self-addition over binary join: `(x ∨ y) + (x ∨ y) = (x + x) ∨ (y + y)`.
- **`Join-square`**: With inhabited index, squaring distributes over a join: `Join g * Join g = Join (λj. g j * g j)`.
- **`join-square`**: Binary version: `(x ∨ y) * (x ∨ y) = x * x ∨ y * y`.
