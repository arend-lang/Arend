### Logic.Unique

Propositions, sets, and contractible types — the basic h-levels of homotopy type theory.

This module formalizes the fundamental truncation levels: `isProp` (any two elements are equal), `isSet` (any two parallel paths are equal), and `Contr` (a center together with a contraction to every point). It establishes that being a proposition is itself a proposition, that propositions are sets, and that propositions and contractible types are equivalent (given a witness). These predicates are the foundation for working with mere propositions, set-level mathematics, and uniqueness proofs throughout the library.

#### Propositions

- **`isProp`**: A type `A` is a proposition iff `\Pi (a a' : A) -> a = a'`.
- **`isProp.=>isSet`**: Every proposition is a set; produced via `\use \sfunc` so propositions automatically have decidable path equality at the next level.
- **`isProp.levelProp`**: `isProp A` is itself a proposition (registered as a level instance).
- **`pi-isProp`**: A dependent product `\Pi (a : A) -> B a` is a proposition whenever each `B a` is.

#### Sets

- **`isSet`**: A type `A` is a set iff any two parallel paths are equal: `\Pi (a a' : A) (p q : a = a') -> p = q`.
- **`isSet.levelProp`**: `isSet A` is a proposition (registered as a level instance).

#### Contractible Types

- **`Contr`**: Class of contractible types — packages `center : A` with `contraction : \Pi (a' : A) -> center = a'`.
- **`Contr.make`**: Convenience constructor building a `Contr` from a center and contraction function.
- **`Contr.levelProp`**: Being contractible is itself a proposition.
- **`contr-equiv`**: Any function between contractible types is an equivalence.

#### Conversions Between Levels

- **`isContr=>isProp`**: A contractible type is a proposition (paths via the center: `inv (c a) *> c a'`).
- **`isContr'=>isProp`**: If `A -> Contr A`, then `A` is a proposition.
- **`isProp=>isContr`**: A proposition with a witness is contractible.
- **`isProp'=>isContr`**: A proposition with a propositionally-truncated witness (`TruncP A`) is contractible.
- **`isProp=>PathContr`**: Path spaces in a proposition are contractible: `Contr (a = a')`.

#### Closure Properties

- **`pi-Contr`**: Dependent products of contractible types are contractible.
- **`sigma-Contr`**: A `\Sigma`-type is contractible when the base and each fiber are contractible.
