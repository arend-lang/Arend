### Data

This directory formalizes basic data types and their algebraic, structural, and propositional properties: booleans, finite indices, optionals, sums, pairs, lists, length-indexed arrays, sublists, and sequential colimits.

#### Atomic Types

- **`Bool.md`** — The two-element boolean type with `not`/`and`/`or`/`xor`, the `So` reflection into propositions, and the bridges between boolean equations and their propositional counterparts.
- **`Maybe.md`** — The optional type `Maybe A` with constructors `nothing`/`just`, the non-dependent eliminator `maybe`, functorial `map`, and `just`-injectivity.
- **`Or.md`** — The disjoint sum `Or A B` with `inl`/`inr`, recursor, functorial action, propositionality for disjoint props, and equivalence preservation.
- **`Sigma.md`** — Componentwise `tupleMap` on non-dependent pairs with projection lemmas, plus contractibility of the empty tuple type.

#### Finite Indices

- **`Fin.md`** — Finite types `Fin n` with constructors `fzero`/`fsuc`, case analysis, predecessors, and injectivity/disequality lemmas relating `Fin` to `Nat`.

#### Lists

- **`List.md`** — Inductive `List A` with concatenation, indexing by `Fin`, splitting, predicates (`All`/`All2`/`AllC`/`InList`), permutations and sortedness, insertion sort and red-black tree sort, and `count`/`group` over decidable sets.
- **`SubList.md`** — Order-preserving sublist relation `SubList l r` with identity, composition, extension/naturality lemmas, and contractibility/uniqueness results plus `Transports` coherences for list-equality transports.

#### Length-Indexed Arrays

- **`Array.md`** — Length-indexed arrays `Array A n` with concatenation, `map`, `filter`/`keep`/`remove`, deduplication, `insert`/`skip`/`replace`, counting, membership, search, and conversion to/from `List`.
- **`Array/`** — Further theory of arrays: permutations, sorting, splittings, and pair-indexed structures.

#### Sequential Colimits

- **`SeqColimit.md`** — Higher inductive sequential colimit of a `Nat`-indexed type sequence, with the flattening lemma identifying total spaces as colimits, plus surjectivity and contractibility corollaries.
