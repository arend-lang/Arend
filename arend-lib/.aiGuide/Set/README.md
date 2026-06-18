### Set

This directory formalizes h-sets and the structures built on them: subsets and their lattice/locale, finite and countable sets, filters, partial elements, set-theoretic categorical structure, and tools for proving types are sets.

#### Set Criteria

- **`Hedberg.md`** — Hedberg's theorem and relation-based criteria for upgrading a type to an h-set (UIP).

#### Morphisms and Categorical Structure

- **`SetHom.md`** — The base record of a function between bare sets, used as the carrier of richer algebraic homomorphisms.
- **`SetCategory.md`** — The bicomplete category `SetCat` of h-sets, with concrete (co)limits, set colimits over small precategories, and a structure identity principle.

#### Subsets

- **`Subset.md`** — Subsets as predicates `X -> \Prop`, the powerset locale `SetLattice`, preimage as a frame homomorphism, and the language of covers and refinements.

#### Finite and Countable Sets

- **`Fin.md`** — The `FinSet` class of types with a chosen cardinality and a propositional bijection to `Fin n`, with combinatorics, search, and standard instances.
- **`Countable.md`** — Countable sets via partial surjections `Nat -> Maybe A` and explicit `Nat`-bijections, closed under sums, surjections, quotients, and factor rings.

#### Filters

- **`Filter.md`** — Filters on top meet-semilattices, specialized to subsets with proper/weakly-proper variants, pushforward along functions, and a meet-semilattice structure.

#### Partial Elements

- **`Partial.md`** — Partial elements as a propositional definedness paired with a value, with extensionality lemmas, functorial lifts, and a pointwise `AddMonoid` instance.

#### Subdirectories

- **`Fin/`** — Further development of finite sets: Kuratowski-finite sets, decidable-finite variants, the pigeonhole principle, and concrete `FinSet` instances.
