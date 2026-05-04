### Data.Array.Perm

This module defines fixed-length permutations (`Perm`) for arrays of the same length, along with sign/inversions and equivalence characterizations.

#### Perm Type

- **`Perm`**: Inductive relation on arrays `l1 l2 : Array A n` with constructors `perm-nil`, `perm-:: (x = y) (Perm l1 l2)`, `perm-swap`, and `perm-trans`.

#### Basic Operations

- **`perm-refl`**: Reflexivity of `Perm`.
- **`perm-sym`**: Symmetry of `Perm`.

#### Inversions and Sign

- **`inversions`**: Counts the number of inversions (transpositions) in a `Perm` proof.
- **`inversions_perm-::`**: `inversions (perm-:: q p) = inversions p`.
- **`inversions_perm-trans`**: `inversions (perm-trans p1 p2) = inversions p1 + inversions p2`.
- **`inversions_perm-refl`**: `inversions perm-refl = 0`.
- **`sign`**: Sign of a permutation: `(-1)^(inversions p)` in a ring `R`.

#### Functorial Operations

- **`perm-map`**: `Perm` is preserved by `map f`.
  - **`conv`**: Converse for injective `f` on `\Set` types.

#### Removal and Fin Permutations

- **`perm-remove`**: `Perm l (l j :: remove1 (l j) l)` for `DecSet` arrays.
- **`perm-fin`**: Any injective `Array (Fin n) n` is a `Perm` of the identity array.
  - **`aux`**: Helper using `fpred` to reduce dimension.
  - **`aux2`**: Helper combining removal with induction.

#### Equivalence Characterization

- **`equiv_perm`**: An `Equiv` on `Fin n` induces `Perm l (l ∘ e)`.
- **`perm_equiv`**: A `Perm l l'` yields an `Equiv` on `Fin n` with `l' j = l (e j)`.
  - **`conv`**: Converse: such an equivalence implies `Perm`.
