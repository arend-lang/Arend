### Data.Or

The disjoint sum (coproduct) type `Or A B` with its eliminators and quasi-equivalence-preservation construction.

`Or A B` is the standard binary sum, formed from constructors `inl` and `inr`. The module provides the recursor and functorial action needed to use sums in proofs and constructions, plus a propositional truncation lemma showing `Or` of disjoint propositions is itself a proposition. The `Or_QEquiv` construction shows that `Or` is functorial on quasi-equivalences, sending a pair of `QEquiv`s to a `QEquiv` between sum types.

#### Type and Constructors

- **`Or`**: The disjoint sum `Or A B` of two types, with right-associative fixity 2.
- **`Or.inl`**: Left injection `A -> Or A B`.
- **`Or.inr`**: Right injection `B -> Or A B`.

#### Eliminators and Functorial Action

- **`Or.rec`**: Non-dependent eliminator: given `f : A -> C` and `g : B -> C`, eliminates `Or A B` into `C`.
- **`Or.map`**: Functorial action: given `f : A -> C` and `g : B -> D`, lifts to `Or A B -> Or C D`.

#### Propositional Truncation

- **`Or.levelProp`**: When `A B : \Prop` are disjoint (i.e., `A -> B -> Empty`), `Or A B` is a proposition: any two elements are equal.

#### Quasi-Equivalence Preservation

- **`Or.Or_QEquiv`**: Given quasi-equivalences `e1 e2 : QEquiv`, a quasi-equivalence between `Or e1.A e2.A` and `Or e1.B e2.B` acting componentwise via `map`.

#### Injectivity of Constructors

- **`uninl`**: `inl` is injective: `inl a = inl a'` in `Or A B` implies `a = a'`.
- **`uninr`**: `inr` is injective: `inr b = inr b'` in `Or A B` implies `b = b'`.

#### Conversion to Bool

- **`orBool`**: Forgets the data of `o : Or A B`, giving `true` for `inl` and `false` for `inr`.
