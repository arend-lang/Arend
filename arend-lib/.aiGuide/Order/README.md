### Order

This directory formalizes order-theoretic structures: preorders, partial orders, strict orders, linear orders, lattices, and their interactions.

#### Core Order Structures

- **`PartialOrder.md`** — Preorders and posets as categories, with product and subset constructions.
- **`StrictOrder.md`** — Strict (irreflexive) order structures and strictly monotone maps.
- **`Biordered.md`** — Structures combining a strict order with a partial order that interact coherently.
- **`LinearOrder.md`** — Total and linear orders, including decidable, dense, and unbounded variants, with associated lattice structure.

#### Lattices and Algebras

- **`Lattice.md`** — Lattices and semilattices: meets, joins, distributivity, bounded and complete variants, with categorical structure derived from the order.
- **`HeytingAlgebra.md`** — Heyting algebras: bounded distributive lattices with implication (relative pseudo-complement), forming cartesian closed posets.
- **`BooleanAlgebra.md`** — Boolean algebras as bounded distributive lattices with complement; negated elements of a Heyting algebra form a Boolean algebra.

#### Lexicographic Orders

- **`Lexicographical.md`** — Lexicographical orderings on pairs and lists, lifting decidable linear orders componentwise.
- **`LexicographicalArray.md`** — Lexicographic strict order on fixed-length arrays, making `Array A n` a decidable linear order.

#### Directed Sets and Subsets

- **`Directed.md`** — Directed sets: inhabited preorders where every pair has a common upper bound.
- **`Elem.md`** — Lifts order structures from a type to its subset `Elem U`, inheriting strict, partial, biordered, and linear order instances.

#### Categorical View

- **`Category.md`** — Categories of ordered sets (posets, strict posets, decidable linear orders) with order-preserving morphisms.
