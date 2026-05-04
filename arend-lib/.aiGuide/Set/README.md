### Set Directory Overview

This directory provides set-level types, finiteness notions, filters, subsets, and categorical structure for sets.

#### Core Set Theory

- **`Hedberg.md`**: Hedberg's theorem and related lemmas — types with decidable (or relation-mediated) equality are sets.
- **`Partial.md`**: Partial elements of a set — values defined on a propositional domain, supporting extensionality, lifting of functions, and a partial-monoid structure.
- **`Countable.md`**: Countable types — bijections between `Nat` and various structured types (`Nat × Nat`, tuples, arrays, sums), plus a notion of countability via partial enumeration.

#### Finite Sets

- **`Fin.md`**: Finite sets as types equipped with a cardinality and a merely-existing equivalence with `Fin n`, together with constructions of common finite sets and search/decidability lemmas.
- **`Fin/`**: Subdirectory contains `DFin.md` (Dedekind-finite sets: injective endofunctions are surjective), `KFin.md` (Kuratowski-finite sets: types admitting a surjection from a standard finite type), `Instances.md` (finiteness instances for sigma types, products, and dependent function types with cardinality formulas), `Pigeonhole.md` (pigeonhole principle for finite sets).

#### Subsets and Filters

- **`Subset.md`**: Subsets of a type as `\Prop`-valued predicates, equipped with a frame/locale structure, together with operations on subsets, covers, and refinements.
- **`Filter.md`**: Filters on meet-semilattices and on subset lattices, including proper/weakly-proper variants and their semilattice structure.

#### Categorical Structure

- **`SetHom.md`**: A bare function between two sets, packaging domain, codomain, and the underlying map.
- **`SetCategory.md`**: The category of sets, its bicomplete structure, and a construction of filtered colimits via quotients.
