### Order.HeytingAlgebra

Heyting algebras: bounded distributive lattices equipped with an implication operation forming a Cartesian closed structure.

A Heyting algebra extends `BoundedDistributiveLattice` and `CartesianClosedPrecat`, with the implication `-->` characterized by the adjunction `x ∧ a <= b ↔ x <= a --> b`. From this single adjunction the module derives top, distributivity, evaluation, and the exponential functor structure, exhibiting `_ --> y` as right adjoint to `_ ∧ y`. Negation is defined as `neg x := x --> bottom`, supporting the standard intuitionistic theory of double negation, negated elements, and continuations `(x --> y) --> y`.

#### Auxiliary

- **`=<=`**: Mixes a path with an order step: `x = y -> y <= z -> x <= z`. Useful for equational-then-inequational reasoning chains.

#### Main Class

- **`HeytingAlebra`**: Extends `BoundedDistributiveLattice` and `CartesianClosedPrecat`. Adds implication `-->` with the adjunction `exponent-left`/`exponent-right`. Provides default implementations of `top`, `top-univ`, `ldistr>=`, and `exp` derived from the implication structure.

#### Implication Basics

- **`implies` / `-->`**: Heyting implication operator on the carrier.
- **`exponent-left`**, **`exponent-right`**: The defining adjunction `x ∧ a <= b ↔ x <= a --> b`.
- **`eval`**: Evaluation/counit: `(x --> y) ∧ x <= y`.
- **`modus-ponens`**: `x ∧ (x --> y) <= y`, the symmetric form of `eval`.
- **`composition-law`**: `(x --> y) ∧ (y --> z) <= x --> z`.
- **`curry`**: `x ∧ y --> z <= x --> (y --> z)`, the curried form of implication.
- **`-->-increasing`**: `y <= x --> y` (any element implies a weaker hypothesis).

#### Monotonicity Lemmas

- **`-->-monotone`**: `y <= z` implies `x --> y <= x --> z` (covariant in the codomain).
- **`_-->-antimonotone`**: `y <= z` implies `z --> x <= y --> x` (contravariant in the domain).
- **`meet-monotone-partial`**, **`meet-monotone-partial'`**: One-sided monotonicity of meet.
- **`-->_join`**: `x ∨ y --> z = (x --> z) ∧ (y --> z)`, implication turns joins into meets.

#### Negation

- **`neg`**: `neg x := x --> bottom`, intuitionistic negation.
- **`neg_bottom`**: `neg bottom = top`.
- **`neg_top`**: `neg top = bottom`.
- **`id<=neg_neg`**: `x <= neg (neg x)`, the unit of double-negation.
- **`neg_join`**: `neg (x ∨ y) = neg x ∧ neg y`, De Morgan for join.

#### Negated Elements

- **`IsNegated`**: Predicate `neg (neg x) <= x`, characterizing the elements fixed by double negation.
- **`bottom-negated`**: `bottom` is negated.
- **`meet-negated`**: Meets of negated elements are negated.
- **`neg-negated`**: Every `neg x` is negated.

#### Continuation and Double Negation

- **`continuation`**: `continuation y x := (x --> y) --> y`, the continuation monad applied at `y`.
- **`double-neg`**: `continuation bottom`, the double-negation operator.
- **`double-->-increasing`**: `x <= (x --> y) --> y`, unit of the continuation.
- **`double-->-idempotent`**: Iterated double continuation collapses: `((((x --> y) --> y) --> y) --> y) <= (x --> y) --> y`.
- **`double-->-monotone`**: Continuation at `x` is monotone in its target.

#### Adjunction with Meet

- **`exponent-adj-unit`**: `x <= y --> x ∧ y`, unit of the meet⊣exponential adjunction.
- **`adj-lemma`**: Recovers `x ∧ z <= y` from `x <= z --> y`.

#### Cartesian Closed Structure

- **`exponent-functor`**: For each `X`, `(X --> -)` as an endofunctor on the Heyting algebra viewed as a category.
- **`exp`** (default): Constructs the right adjoint to `bprodFunctorRight Y`, exhibiting the algebra as a `CartesianClosedPrecat` via the unit `exponent-adj-unit` and counit `eval`.
