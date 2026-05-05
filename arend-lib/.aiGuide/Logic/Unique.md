### Logic.Unique

Propositionality, contractibility, and set-level truncation.

#### isProp

- **`isProp`**: `\Pi (a a' : A) -> a = a'` — `A` has at most one element.
  - **`=>isSet`**: `isProp A` implies `isSet A` (propositions are sets).
  - **`levelProp`**: `isProp A` is itself a proposition.

#### isSet

- **`isSet`**: `\Pi (a a' : A) (p q : a = a') -> p = q` — `A` has unique identity proofs.
  - **`levelProp`**: `isSet A` is a proposition.

#### Contr

- **`Contr`**: Class with `A`, `center : A`, `contraction : \Pi (a') -> center = a'`.
  - **`make`**: Constructor from data.
  - **`levelProp`**: `Contr A` is a proposition.

#### Conversions

- **`contr-equiv`**: Two contractible types have an equivalence between them.
- **`isContr=>isProp`**: Contractible implies proposition.
- **`isContr'=>isProp`**: `(A -> Contr A) -> isProp A`.
- **`isProp=>isContr`**: Proposition with an element is contractible.
- **`isProp'=>isContr`**: Proposition with truncated element is contractible.
- **`isProp=>PathContr`**: In a proposition, path types are contractible.

#### Pi and Sigma

- **`pi-isProp`**: Dependent product of propositions is a proposition.
- **`pi-Contr`**: Dependent product of contractible types is contractible.
- **`sigma-Contr`**: Sigma of contractible base and fibers is contractible.
