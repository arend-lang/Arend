### Logic

Foundational propositional logic primitives: the empty type, propositional truncation, disjunction, and basic prop-level reasoning utilities.

This module sets up the proof-relevant logical scaffolding used throughout the library. `Empty` and `Not` give negation; `TruncP` is the propositional truncation that turns any type into a proposition while still allowing recursion into propositions/sets; `||` is the prop-valued disjunction (distinct from the constructive `Or` in `Data.Or`). The `propExt`, `prop-pi`, and `set-pi` lemmas exploit Arend's universe-level system (`\Prop`, `\Set`) to obtain proof irrelevance and propositional extensionality. `NegatedProp` axiomatizes propositions for which double-negation elimination holds, providing a hook for classical reasoning over specific types.

#### Empty Type and Negation

- **`Empty`**: The empty type (false proposition).
- **`absurd`**: Ex falso: `Empty -> A` for any `A`.
- **`Not`**: Negation: `A -> Empty`.
- **`/=`**: Disequality: `Not (a = a')`.
- **`/=-sym`**: Symmetry of disequality.

#### Propositional Truncation

- **`TruncP`**: Propositional truncation of a type, with constructor `inP` and identification path `truncP`.
- **`TruncP.levelProp`**: Witness that `TruncP A` is a proposition.
- **`TruncP.remove`**: Eliminate `TruncP A` when `A` is already a proposition.
- **`TruncP.remove'`**: `remove` specialized for `A : \Prop`.
- **`TruncP.rec`**: Recursion into a proposition: factor `A -> B` through `TruncP A`.
- **`TruncP.rec-eval`**: Computation rule: `rec p (inP a) f = f a`.
- **`TruncP.rec-set`**: Recursion into a set, returning the unique image together with its witness.
- **`TruncP.map`**: Functorial action: `(A -> B) -> TruncP A -> TruncP B`.

#### Propositional Equality and Extensionality

- **`prop-pi`**: Proof irrelevance: any two elements of a `\Prop` are equal.
- **`prop-isProp`**: Every `\Prop` satisfies `isProp`.
- **`set-pi`**: UIP for `\Set`: any two paths between equal points are equal.
- **`prop-dpi`**: Dependent proof irrelevance over a path of propositions.
- **`propExt`**: Propositional extensionality: bi-implication of `\Prop`s yields equality.
- **`propExt.dir`**, **`propExt.conv`**: Forward/backward transport along a `\Prop` equality.

#### ToProp Wrapper

- **`ToProp`**: Wraps a type `A` together with `isProp A` to produce something that is propositionally `A`.
- **`ToProp.fromProp`**: Extract the underlying `A`.
- **`ToProp.levelProp`**: Witness that `ToProp A p` is a proposition.

#### Disjunction

- **`||`**: Prop-valued disjunction with constructors `byLeft` and `byRight`.
- **`||.rec`**: Recursion into a proposition `C` from `A -> C` and `B -> C`.
- **`||.rec'`**: Variant where the target `C` is itself a `\Prop`.
- **`||.map`**: Functorial action on both summands.
- **`||.fromOr`**: Convert a constructive `Or A B` into `A || B`.
- **`||.toOr`**: Convert `A || B` into `TruncP (Or A B)`.
- **`||.flip`**: Swap the disjuncts.

#### Bi-implication

- **`<->`**: Logical equivalence of propositions: `\Sigma (P -> Q) (Q -> P)`.
- **`<->_=`**: `(P <-> Q) <-> (P = Q)` via propositional extensionality.
- **`<->refl`**, **`<->trans`**, **`<->sym`**: Reflexivity, transitivity, symmetry of `<->`.

#### Quantification over Arrays

- **`OneOf`**: Truncated existence: some predicate in an array of types is inhabited.
- **`ElemOf`**: Untruncated `Given` form of `OneOf`.
- **`arraySubset`**: `x` belongs to the array `l`: `∃ (y : l) (y = x)`.

#### Classical Hooks

- **`isProp-prover`**: Meta tactic discharging propositional equalities by `prop-pi` or contradiction.
- **`NegatedProp`**: Class of propositions admitting double-negation elimination, with field `isNegated : Not (Not P) -> P`.
- **`EmptyNegated`**: Instance witnessing that `Empty` is a `NegatedProp`.
