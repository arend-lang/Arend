### Order

This directory formalizes order-theoretic structures: preorders, partial orders, strict orders, linear orders, lattices, and their interactions.

#### Core Order Structures

- **`PartialOrder.md`** — Preorders and posets as thin categories, with opposites, products, sub-posets, the preorder-to-poset quotient reflection, and predicate forms of meet/join.
- **`StrictOrder.md`** — Strict (irreflexive) order class with reasoning combinators for chained strict/equational steps and the `IsStrictlyMonotone` map predicate.
- **`Biordered.md`** — Structures combining a strict and partial order with mixed transitivity, extended to lattices where strict inequalities propagate through binary and indexed meets/joins.
- **`LinearOrder.md`** — Total and linear orders with `<-comparison`/`<-connectedness`, decidable trichotomy, density, unboundedness, and min/max search over arrays.

#### Lattices and Algebras

- **`Lattice.md`** — The lattice hierarchy from meet/join semilattices through bounded, distributive, and complete lattices, with monoid/semiring coercions and `Big`/`FinJoin`/`SJoin` operations.
- **`HeytingAlgebra.md`** — Heyting algebras as bounded distributive lattices with implication via the meet⊣exponential adjunction, supporting negation, double negation, and Cartesian closed structure.
- **`BooleanAlgebra.md`** — Boolean algebras as bounded distributive lattices with complement (also Heyting), De Morgan and symmetric-difference lemmas, and the regular-elements construction `HeytingBooleanAlgebra`.

#### Lexicographic Orders

- **`Lexicographical.md`** — Lexicographic decidable linear orders on pairs `\Sigma A B` and lists `List A`, lifting an underlying decidable linear order with full trichotomy proofs.
- **`LexicographicalArray.md`** — Lexicographic strict order on fixed-length arrays `Array A n`, packaged as a decidable linear order instance.

#### Directed Sets and Subsets

- **`Directed.md`** — Directed sets (inhabited preorders with binary upper bounds) and their closure under products via a `HasProduct` instance.
- **`Elem.md`** — Lifts strict, partial, biordered, and linear order instances from `X` to subsets `Elem U` by projecting through `.1`.

#### Categorical View

- **`Category.md`** — Categories `PosetCat`, `StrictPosetCat`, and `DecLinearOrderCat` with monotone (resp. strictly monotone) maps as morphisms.
