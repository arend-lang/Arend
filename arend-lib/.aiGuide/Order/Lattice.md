### Order.Lattice

Order-theoretic lattices: meet/join semilattices, distributive and bounded variants, and complete lattices.

This module builds the lattice hierarchy on top of `Poset`, layering meet and join operations with their universal properties as classes that mirror categorical (co)products. Each semilattice exposes a `\protected \func op` dualizing meet ↔ join, so dual results come for free. Bounded variants add `top`/`bottom` as terminal/initial objects and coerce into commutative (additive) monoids; bounded distributive lattices coerce further to `CSemiring`. `CompleteLattice` provides arbitrary `Join`/`Meet` indexed by `\Set`, with finite operations and limits/pullbacks derived as defaults so the categorical `CompleteCat` structure follows automatically.

#### Meet Semilattice

- **`MeetSemilattice`**: A `Poset` with binary meet `∧` satisfying `meet-left`, `meet-right`, and `meet-univ`. Extends `PrecatWithBprod` so `∧` is the categorical binary product.
- **`Big_∧` / `Big_meet`**: Iterated meet over a non-empty array.
- **`Big_meet-cond`**: `Big_∧ l <= l j` for any index.
- **`meet-idemp`**, **`meet-comm`**, **`meet-assoc`**: Standard algebraic laws for `∧`.
- **`meet-monotone`**: `∧` is monotone in both arguments.
- **`meet_<=`**: `x <= y -> x ∧ y = x` (absorption form).
- **`meet_<='`**: Converse: `x ∧ y = x -> x <= y`.
- **`Big_<=_meet0`**, **`Big_<=_meet1`**: Big-meet bounds: below the seed and below each entry.
- **`Big_meet-univ`**: Universal property for big meets indexed by an array.
- **`op`**: The dual `JoinSemilattice` on the opposite order.
- **`Meets`** (in `\where`): The data of a meet of `x` and `y` as a `\Sigma`-type; **`Meets-isProp`** shows it is a proposition.

#### Join Semilattice

- **`JoinSemilattice`**: A `Poset` with binary join `∨` satisfying `join-left`, `join-right`, and `join-univ`.
- **`Big_∨` / `Big_join`**: Iterated join over a non-empty array.
- **`Big_join-cond`**: `l j <= Big_∨ l`.
- **`join-monotone`**, **`join-idemp`**, **`join-comm`**, **`join-assoc`**: Algebraic laws for `∨`.
- **`join_<=`**, **`join_<='`**: Reflect `x <= y` as `x ∨ y = y` and back.
- **`Big_<=_join0`**, **`Big_<=_join1`**, **`Big_<=_join`**: Lower-bound lemmas for big joins (seed, indexed entry, and length-`suc n` variant).
- **`Big_join-univ`**: Universal property for big joins.
- **`op`**: The dual `MeetSemilattice`.
- **`Joins`** (in `\where`): Data of a join as a `\Sigma`-type; **`Joins-isProp`** shows it is a proposition.

#### Lattice

- **`Lattice`**: Combines `MeetSemilattice` and `JoinSemilattice` on the same poset.
- **`ldistr<=`**: One direction of distributivity always holds: `(x ∧ y) ∨ (x ∧ z) <= x ∧ (y ∨ z)`.
- **`op`**: The dual lattice obtained by swapping meet and join.

#### Distributive Lattice

- **`DistributiveLattice`**: A `Lattice` with the converse distributive inequality `ldistr>=`.
- **`meet-ldistr`**, **`meet-rdistr`**: Full distributivity equalities `x ∧ (y ∨ z) = (x ∧ y) ∨ (x ∧ z)` and the right-handed dual.
- **`rdistr>=`**: Right-handed distributive inequality.
- **`lcodistr`**: Codistributivity `x ∨ (y ∧ z) = (x ∨ y) ∧ (x ∨ z)`, derivable in any distributive lattice.

#### Top Meet Semilattice

- **`TopMeetSemilattice`**: A `MeetSemilattice` with a top element `top` (universal upper bound). Extends `CartesianPrecat`, presenting `top` as the terminal object.
- **`top-left`**, **`top-right`**: Identity laws `top ∧ x = x` and `x ∧ top = x`.
- **`BigMeet`**: Iterated meet of an array, seeded with `top` (works for empty arrays).
- **`BigMeet-cond`**, **`BigMeet-univ`**: Pointwise bound and universal property.
- **`op`**: Dual `BottomJoinSemilattice` with `bottom = top`.
- **`toMonoid`** (in `\where`): Coerces a `TopMeetSemilattice` to a `CMonoid` with `ide = top`, `* = meet`.

#### Bottom Join Semilattice

- **`BottomJoinSemilattice`**: A `JoinSemilattice` with a bottom element. Extends `DirectedSet`, with `bottom` witnessing inhabitedness and `∨` witnessing directedness.
- **`bottom-left`**, **`bottom-right`**: Identity laws for `∨`.
- **`BigJoin`**: Iterated join of an array, seeded with `bottom`.
- **`BigJoin-cond`**, **`BigJoin-univ`**: Pointwise bound and universal property.
- **`FinJoin`**: Join indexed by an arbitrary `FinSet`, defined via the `FinSum` of the underlying additive structure.
- **`FinJoin-cond`**, **`FinJoin-univ`**: Universal properties for `FinJoin`.
- **`FinJoin_Equiv`**: Invariance of `FinJoin` under bijective reindexing.
- **`FinJoin=BigJoin`**: Agreement of `FinJoin` and `BigJoin` on `Fin n`.
- **`BigJoin_EPerm`**: Permutation invariance of `BigJoin`.
- **`BigJoin_++`**: `BigJoin (l ++ l') = BigJoin l ∨ BigJoin l'`.
- **`BigJoin_join`**: Pointwise-join distributes through `BigJoin` of equal-length arrays.
- **`op`**: Dual `TopMeetSemilattice`.
- **`toMonoid`** (in `\where`): Coerces a `BottomJoinSemilattice` to an `AbMonoid` with `zro = bottom`, `+ = join`.

#### Bounded and Distributive Variants

- **`BoundedLattice`**: A `Lattice` that is both `TopMeetSemilattice` and `BottomJoinSemilattice`. **`op`** swaps top/bottom and meet/join.
- **`BottomDistributiveLattice`**: A `DistributiveLattice` with `bottom`.
  - **`bottom-ldistr`**, **`bottom-rdistr`**: `x ∧ bottom = bottom` and the symmetric form.
  - **`BigJoin-ldistr`**, **`BigJoin-rdistr`**: Meet distributes over `BigJoin` on either side.
- **`BoundedDistributiveLattice`**: Combines `BottomDistributiveLattice` and `BoundedLattice`.
  - **`toSemiring`** (in `\where`): Coerces to a `CSemiring` with `+ = join`, `* = meet`, `zro = bottom`, `ide = top`.

#### Complete Lattice

- **`CompleteLattice`**: A `BoundedLattice` with arbitrary set-indexed joins (`Join`) and meets (`Meet`) and their universal properties (`Join-cond`, `Join-univ`, `Meet-cond`, `Meet-univ`). Extends `CompleteCat`, with limits built from `Meet` and pullbacks from binary meets. Binary `join`/`meet`, `top`, `bottom` come with default implementations derived from `Join`/`Meet` over `Bool` or empty/singleton index sets.
- **`Join-double`**: Fubini for joins: `Join (λ i. Join (λ j. f i j)) = Join (λ p. f p.1 p.2)`.
- **`Join_=`**: Pointwise equality of families implies equal joins.
- **`SJoin`**: Join over a subset given by a predicate `U : E -> \Prop`, summed over its total space.
- **`SJoin-cond`**, **`SJoin-univ`**: Universal property of `SJoin`.
- **`join_Join`**: Binary join expressed as a `Join` over `Bool`.
- **`op`**: The dual complete lattice obtained by swapping `Join` ↔ `Meet`.
