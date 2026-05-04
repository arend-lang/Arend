### Order.Lattice

Lattices and semilattices: meets, joins, distributivity, bounded and complete variants, with categorical structure (binary products, terminal objects, limits) derived from the order.

#### Meet Semilattices

- **`MeetSemilattice`**: A `Poset` together with a binary meet `∧` satisfying universal property; extends `PrecatWithBprod` so that meets are categorical binary products.
- **`meet`** (alias **`∧`**): Binary greatest lower bound.
- **`meet-left`**, **`meet-right`**: Projections `x ∧ y <= x` and `x ∧ y <= y`.
- **`meet-univ`**: Universal property: `z <= x -> z <= y -> z <= x ∧ y`.
- **`MeetSemilattice.Meets`**: The (Sigma) type expressing that `j` is a meet of `x` and `y` in a `Poset`.
- **`MeetSemilattice.Meets-isProp`**: Meets are unique up to equality (proposition).

#### Join Semilattices

- **`JoinSemilattice`**: A `Poset` together with a binary join `∨` satisfying the dual universal property.
- **`join`** (alias **`∨`**): Binary least upper bound.
- **`join-left`**, **`join-right`**: Inclusions `x <= x ∨ y` and `y <= x ∨ y`.
- **`join-univ`**: Universal property: `x <= z -> y <= z -> x ∨ y <= z`.
- **`JoinSemilattice.Joins`**: The (Sigma) type expressing that `m` is a join of `x` and `y`.
- **`JoinSemilattice.Joins-isProp`**: Joins are unique up to equality (proposition).

#### Lattices and Distributivity

- **`Lattice`**: Combines `MeetSemilattice` and `JoinSemilattice` — both binary meets and joins.
- **`DistributiveLattice`**: A `Lattice` satisfying the (one-sided) distributive inequality `ldistr>= : x ∧ (y ∨ z) <= (x ∧ y) ∨ (x ∧ z)`.

#### Bounded Variants

- **`TopMeetSemilattice`**: A `MeetSemilattice` with a top element `top` (greatest); extends `CartesianPrecat`, with `top` serving as the terminal object. Includes coercion **`toMonoid`** to a `CMonoid` with `ide = top`, `* = meet`.
- **`top`**, **`top-univ`**: Greatest element and its universal property `x <= top`.
- **`BottomJoinSemilattice`**: A `JoinSemilattice` with a bottom element `bottom` (least); extends `DirectedSet` (inhabited and directed via joins). Includes coercion **`toMonoid`** to an `AbMonoid` with `zro = bottom`, `+ = join`.
- **`bottom`**, **`bottom-univ`**: Least element and its universal property `bottom <= x`.
- **`BoundedLattice`**: A `Lattice` that is both `TopMeetSemilattice` and `BottomJoinSemilattice`.
- **`BottomDistributiveLattice`**: Distributive lattice with a bottom element.
- **`BoundedDistributiveLattice`**: Bounded distributive lattice; includes coercion **`toSemiring`** to a `CSemiring` with `+ = join`, `* = meet`, `zro = bottom`, `ide = top`.

#### Complete Lattices

- **`CompleteLattice`**: A `BoundedLattice` with arbitrary set-indexed joins `Join` and meets `Meet`; extends `CompleteCat`, providing all small limits (and pullbacks via meets).
- **`Join`**, **`Join-cond`**, **`Join-univ`**: Set-indexed supremum with coprojection and universal property.
- **`Meet`**, **`Meet-cond`**, **`Meet-univ`**: Set-indexed infimum with projection and universal property.
- All finite/bounded operations (`bottom`, `top`, `meet`, `join`) are derived from the indexed `Join` and `Meet`; the `limit` field constructs categorical limits via `Meet` over the image of the diagram, and `pullback` is realized by binary `meet`.
