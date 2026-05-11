### Order.Biordered

Sets and lattices equipped with compatible partial and strict order relations.

This module unifies a non-strict order `<=` with a strict order `<` on the same carrier, requiring the two to interact coherently via mixed transitivity laws and an implication from strict to non-strict. Building on this, `BiorderedLattice` adds lattice structure where strict inequalities propagate through meets and joins, giving universal properties and monotonicity for both finite binary operations and indexed `Big` operations over arrays. The design lets proofs freely mix `<` and `<=` steps, which is essential for ordered algebraic structures (ordered rings, fields, etc.) where both relations naturally coexist.

#### Biordered Sets

- **`BiorderedSet`**: Class extending `StrictPoset` and `Poset` on a common carrier `E`, with axioms tying the two orders together.
- **`<∘r`** (`<-transitive-right`): Mixed transitivity: `a1 <= a2 -> a2 < a3 -> a1 < a3`.
- **`<∘l`** (`<-transitive-left`): Mixed transitivity: `a1 < a2 -> a2 <= a3 -> a1 < a3`.
- **`<=-less`**: Strict order implies non-strict: `a1 < a2 -> a1 <= a2`.
- **`op`**: The opposite biordered set, reversing both `<` and `<=`.

#### Biordered Lattices

- **`BiorderedLattice`**: Class extending `BiorderedSet` and `Lattice`, requiring strict inequalities to be compatible with meets and joins.
- **`<_meet-univ`**: Universal property of meet for `<`: `x < y -> x < z -> x < y ∧ z`.
- **`<_join-univ`**: Universal property of join for `<`: `x < z -> y < z -> x ∨ y < z`.
- **`<_meet-monotone`**: Meet is strictly monotone in both arguments: `x < y -> x' < y' -> x ∧ x' < y ∧ y'`.
- **`<_join-monotone`**: Join is strictly monotone in both arguments: `x < y -> x' < y' -> x ∨ x' < y ∨ y'`.
- **`Big_<_meet-univ`**: Indexed meet universal property: if `y < x` and `y < l i` for every index, then `y < Big ∧ x l`.
- **`Big_<_join-univ`**: Indexed join universal property: if `x < y` and `l i < y` for every index, then `Big ∨ x l < y`.
