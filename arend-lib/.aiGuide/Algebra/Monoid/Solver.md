### Algebra.Monoid.Solver

A reflection-based equality solver for monoids, commutative monoids, and meet-semilattices.

The module formalizes a syntactic representation of monoid expressions (`MonoidTerm`) together with normalization procedures whose consistency lemmas reduce semantic equality of expressions to syntactic equality of normal forms. For plain monoids the normal form is a flat list of variables (associativity collapsed); for commutative monoids the list is additionally sorted (commutativity); for top-meet-semilattices duplicates are also removed (idempotence). The `interpret`/`interpretNF` pair links syntax to a fixed `vars : Array M`, and the `terms-equality` lemmas serve as the entry points used by tactics to discharge equational goals after reducing both sides to a canonical list.

#### Term Syntax

- **`MonoidTerm`**: Inductive type of monoid expressions over a variable type `V`, with constructors `var`, `:ide` (unit), and `:*` (multiplication).
- **`normalize`**: Flattens a `MonoidTerm V` to a `List V` using an accumulator-passing helper `normalize.aux`, eliminating units and associativity.

#### MonoidData (Plain Monoid Solver)

Class parameterized by a monoid `M` and a variable assignment `vars : Array M`; uses `V := Fin vars.len` as the variable type.

- **`interpret`**: Evaluates a `MonoidTerm V` in `M` using `vars`.
- **`interpretNF`**: Evaluates a normal-form `List V` in `M`, with the cons-case lemma `interpretNF.cons`.
- **`normalize-consistent`**: `interpret t = interpretNF (normalize t)`; the auxiliary `normalize-consistent.aux` handles the accumulator.
- **`terms-equality`**: If two terms have equal normal forms, their interpretations are equal — the main solver entry point.
- **`terms-equality-conv`**: Converse direction (assumes the monoid is free on `vars`, used in reverse-reflection contexts).
- **`interpretNF_++`**: List concatenation interprets as monoid product.
- **`replace-consistent`**: Substituting a slice `[i, i+s)` of a list with a list of equal interpretation preserves the overall interpretation; supports rewriting subterms.

#### CMonoidData (Commutative Monoid Solver)

Extends `MonoidData`, overriding `M` to a `CMonoid`. Normal forms are sorted lists.

- **`sort-consistent`**: Sorting a list preserves its interpretation.
- **`perm-consistent`**: Any permutation of a list has the same interpretation.
- **`normalize-consistent`**: `interpret t = interpretNF (sort (normalize t))`.
- **`terms-equality`** / **`terms-equality-conv`**: Solver entry points using sorted normal forms.
- **`replace-consistent`**: Substitution by a list of indices `is`: replaces the elements at `is` with a list `r` of equal interpretation, returning `r ++ removeIndices is l`.
- **`replace-consistent-lem`**: `interpretNF l = interpretNF (indices is l ++ removeIndices is l)` — splits a list into selected and unselected parts.
- **`indices`** (in `\where`): Picks out elements of a list at the positions given by a list of `Nat`.
- **`removeIndices`** (in `\where`): Deletes the elements of a list at the given positions.

#### LData (Top-Meet-Semilattice Solver)

Extends `CMonoidData` with `M` set to a `TopMeetSemilattice` `L`, treating meet as the commutative monoid operation with top as the unit. Normal forms are sorted lists with duplicates collapsed (using `RedBlack.sort`).

- **`removeDuplicates`**: Removes adjacent duplicate variables from a list (relying on decidable equality on `V`); on a sorted list this yields a duplicate-free representative.
- **`removeDuplicates-consistent`**: `interpretNF l = interpretNF (removeDuplicates l)` — uses idempotence of meet.
- **`normalize-consistent`**: `interpret t = interpretNF (removeDuplicates (RedBlack.sort (normalize t)))`.
- **`terms-equality`** / **`terms-equality-conv`**: Solver entry points using sorted, deduplicated normal forms.
