### Logic (root file)

Core logical types and propositions.

#### Empty and Negation

- **`Empty`**: The empty type (no constructors).
- **`absurd`**: Elimination from `Empty` to any type.
- **`Not`**: `A -> Empty`.
- **`/=`**: `Not (a = a')`.
- **`/=-sym`**: Symmetry of disequality.

#### Propositional Truncation

- **`TruncP`**: Propositional truncation with `inP` constructor and `truncP` path constructor.
  - **`levelProp`**: `TruncP A` is a proposition.
  - **`remove`**: Extracts from `TruncP A` when `A` is a proposition.
  - **`remove'`**: Variant for `A : \Prop`.
  - **`rec`**: Recursor into propositions.
  - **`rec-eval`**: `rec` computes on `inP`.
  - **`rec-set`**: Recursor into sets with proof-irrelevant output.
  - **`map`**: Functorial action.

#### Proposition Helpers

- **`prop-pi`**: Any two elements of a `\Prop` are equal.
- **`prop-isProp`**: A `\Prop` satisfies `isProp`.
- **`set-pi`**: Any two proofs of equality in a `\Set` are equal.
- **`prop-dpi`**: Dependent path in a family of propositions.

#### ToProp

- **`ToProp`**: Wraps `A` with an `isProp` proof into a proposition. Has `fromProp`, `levelProp`.

#### Truncated Disjunction

- **`||`**: Propositional disjunction with `byLeft`, `byRight`. Has `rec`, `rec'`, `map`, `fromOr`, `toOr`, `flip`.

#### Biconditional and Propositional Extensionality

- **`<->`**: `\Sigma (P -> Q) (Q -> P)` for propositions.
- **`<->_=`**, **`<->refl`**, **`<->trans`**, **`<->sym`**: Biconditional lemmas.
- **`propExt`**: Propositional extensionality: mutual implication implies equality. Has `dir`, `conv`.

#### Miscellaneous

- **`OneOf`**: `∃ (P : l) P` — at least one proposition in an array holds.
- **`ElemOf`**: `Given (P : l) P` — a chosen witness from an array.
- **`arraySubset`**: `∃ (y : l) (y = x)` — membership in an array.
- **`NegatedProp`**: Class for propositions satisfying double negation elimination.
- **`EmptyNegated`**: `Empty` is a `NegatedProp`.
