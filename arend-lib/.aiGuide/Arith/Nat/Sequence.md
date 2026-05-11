### Arith.Nat.Sequence

Decidability-based search and finiteness lemmas for predicates on natural numbers.

This module provides two foundational results about decidable predicates `A : Nat -> \Prop`. The first establishes that if such a predicate is inhabited, there is a unique minimal witness — a constructive form of the well-ordering principle for `Nat`. The second shows that a decidable predicate bounded above carves out a finite subset of `Nat`. Together they form basic building blocks for working with computable subsets and minimization over the naturals.

#### Search and Minimization

- **`search`**: Given a decidable predicate `A : Nat -> \Prop` and a proof `∃ (n : Nat) (A n)` that it is inhabited, produces a contractible type of minimal witnesses `\Sigma (n0 : Nat) (A n0) (\Pi (n : Nat) -> A n -> n0 <= n)`. This is the well-ordering principle: every nonempty decidable subset of `Nat` has a unique least element.

#### Finiteness of Bounded Subsets

- **`NatFinSubset`**: Given a decidable predicate `A : Nat -> \Prop` together with an upper bound `M : Nat` such that every `n` satisfying `A n` has `n < M`, produces a `FinSet` instance on `\Sigma (n : Nat) (A n)`. Used to convert decidable, bounded subsets of the naturals into finite sets for enumeration and finite-set reasoning.
