### Data.Or

The disjoint sum (coproduct) type `Or A B` with its eliminators and equivalence-preservation lemma.

`Or A B` is the standard binary sum, formed from constructors `inl` and `inr`. The module provides the recursor and functorial action needed to use sums in proofs and constructions, plus a propositional truncation lemma showing `Or` of disjoint propositions is itself a proposition. The `Or_Equiv` construction shows that `Or` is functorial on equivalences, sending a pair of equivalences to an equivalence between sum types.

#### Type and Constructors

- **`Or`**: The disjoint sum `Or A B` of two types, with right-associative fixity 2.
- **`inl`**: Left injection `A -> Or A B`.
- **`inr`**: Right injection `B -> Or A B`.

#### Eliminators and Functorial Action

- **`rec`**: Non-dependent eliminator: given `f : A -> C` and `g : B -> C`, eliminates `Or A B` into `C`.
- **`map`**: Functorial action: given `f : A -> C` and `g : B -> D`, lifts to `Or A B -> Or C D`.

#### Propositional Truncation

- **`levelProp`**: When `A B : \Prop` are disjoint (i.e., `A -> B -> Empty`), `Or A B` is a proposition: any two elements are equal.

#### Equivalence Preservation

- **`Or_Equiv`**: Given equivalences `e1 : Equiv` between `e1.A` and `e1.B`, and similarly `e2`, builds a `QEquiv` between `Or e1.A e2.A` and `Or e1.B e2.B` by mapping componentwise; witnesses for the round-trip identities are derived from those of `e1` and `e2`.
