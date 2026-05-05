### Algebra.Monoid.Solver

Reflective solver infrastructure for proving equalities in monoids and commutative monoids by normalizing syntactic terms.

#### Term Syntax

- **`MonoidTerm`**: Abstract syntax tree of monoid expressions over a variable type `V`, with constructors `var` (variable injection), `:ide` (identity element), and `:*` (binary product).

#### Normalization

- **`normalize`**: Flattens a `MonoidTerm V` into a `List V` representing the sequence of variables in order, dropping identities and associativity. Implemented via the accumulator-passing helper `normalize.aux`.

#### Data Classes

- **`MonoidData`**: Carries a `Monoid M` together with an interpretation `vars : Array M` mapping variable indices to monoid elements; the basic context used to evaluate `MonoidTerm`s in a non-commutative monoid.
- **`CMonoidData`**: Extends `MonoidData`, overriding `M` to be a `CMonoid` so that solver routines may additionally exploit commutativity (e.g. by sorting normalized terms).
- **`LData`**: Extends `CMonoidData` with a `TopMeetSemilattice L`, identifying `M` with `L`; provides the context for solving equalities in a top-bounded meet-semilattice viewed as a commutative idempotent monoid.

#### Index Manipulation Helpers (in `CMonoidData`)

- **`indices`**: Given a list of positions `is : List Nat` and a list `l : List A`, returns the sublist of `l` at those positions; used by the commutative solver to project selected variable occurrences.
- **`removeIndices`**: Dual to `indices`; returns `l` with the entries at positions `is` deleted, supporting cancellation steps in the commutative normalization procedure.
