### Logic.Rewriting.ARS.Relation

Foundational definitions for abstract rewriting systems: binary relations, joins of reduction sequences, and reflexive-transitive closures.

This module provides the basic vocabulary used throughout the ARS development. A `Rel A` is a binary relation on `A`, a `StraightJoin` packages two reductions converging on a common reduct (the "valley" pattern in confluence proofs), and `Closure` is an inductive reflexive-transitive closure presented as a list-like sequence of single-step reductions. The `Closure` namespace also provides the standard combinators — concatenation, functorial lifting along relation morphisms, monadic flattening of nested closures, and case analysis — that let later modules build confluence and termination arguments compositionally.

#### Relations

- **`Rel`**: Type of binary relations on `A`, defined as `A -> A -> \Type`.

#### Joins

- **`StraightJoin`**: Record witnessing that two elements `a, b : A` reduce to a `common-reduct` under `rel`, with fields `a~>cr : rel a common-reduct` and `b~>cr : rel b common-reduct`. Encodes the "valley" shape used in confluence properties.

#### Reflexive-Transitive Closure

- **`Closure`**: Inductive reflexive-transitive closure of a relation `R`, with constructors:
  - `c-trivial`: reflexivity from a path `a = b`.
  - `c-basic`: a single `R`-step.
  - `c-connect`: prepending an `R`-step to an existing closure (cons-like).

#### Closure Operations

- **`Closure.compose`**: Concatenation of two closures `Closure R a b -> Closure R b c -> Closure R a c`, establishing transitivity.
- **`Closure.lift`**: Functoriality: given `map : A -> B` and a relation morphism `rel-map`, transports a `Closure rel x y` to `Closure rel' (map x) (map y)`.
- **`Closure.flatten`**: Monadic join: collapses `Closure (Closure rel) x y` to `Closure rel x y`, used to merge nested closures into a single sequence.
- **`Closure.extract`**: Case analysis on a closure, returning either a proof that `x = y` (trivial case) or a witness `(z, rel x z)` of an initial step. Useful for inducting on whether reduction has occurred.
