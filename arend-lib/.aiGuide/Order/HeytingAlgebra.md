### Order.HeytingAlgebra

Heyting algebras: bounded distributive lattices equipped with an implication operation.

A Heyting algebra extends `BoundedDistributiveLattice` with an implication `-->` characterized by the adjunction `x ∧ a <= b ↔ x <= a --> b` (the fields `<=-curry` and `<=-uncurry`). From this single adjunction the module derives `top`, distributivity, evaluation, and the exponential functor structure, exhibiting `_ --> y` as right adjoint to `_ ∧ y`; `toCCC` views the algebra as a `CartesianClosedPrecat`. Negation `HeytingAlebra.neg` is defined as `neg x := x --> bottom`, supporting the standard intuitionistic theory of double negation, negated elements, and continuations `(x --> y) --> y`.

#### Main Class

- **`HeytingAlebra`**: Extends `BoundedDistributiveLattice`. Adds implication `-->` with the adjunction `<=-curry`/`<=-uncurry`. Provides default implementations of `top`, `top-univ`, and `ldistr>=` derived from the implication structure.
- **`HeytingAlebra.-->`**: Heyting implication operator on the carrier.
- **`HeytingAlebra.toCCC`**: Exhibits the algebra as a `CartesianClosedPrecat`, with `-->` as the exponential, `<=-eval` as evaluation and `<=-curry` as currying.

#### Implication Basics

- **`HeytingAlebra.<=-eval`**: Evaluation/counit: `(x --> y) ∧ x <= y`.
- **`HeytingAlebra.modus-ponens`**: `x ∧ (x --> y) <= y`, the symmetric form of `<=-eval`.
- **`HeytingAlebra.composition-law`**: `(x --> y) ∧ (y --> z) <= x --> z`.
- **`HeytingAlebra.curry`**: `x ∧ y --> z = x --> (y --> z)`, the curried form of implication.
- **`HeytingAlebra.top_exponent`**: If `x <= y` then `top <= x --> y`.

#### Monotonicity and Distributivity Lemmas

- **`HeytingAlebra.exponent-monotone`**: `-->` is antitone in the first argument and monotone in the second: `x' <= x` and `y <= y'` give `x --> y <= x' --> y'`.
- **`HeytingAlebra.exponent_meet`**: `-->` distributes over meet on the right: `x --> y ∧ z = (x --> y) ∧ (x --> z)`.
- **`HeytingAlebra.exponent_join`**: `x ∨ y --> z = (x --> z) ∧ (y --> z)`, implication turns joins into meets.

#### Negation

- **`HeytingAlebra.neg`**: `neg x := x --> bottom`, intuitionistic negation.
- **`HeytingAlebra.neg-inverse`**: `neg` is contravariant: `x <= y` gives `neg y <= neg x`.
- **`HeytingAlebra.neg_bottom`**: `neg bottom = top`.
- **`HeytingAlebra.neg_top`**: `neg top = bottom`.
- **`HeytingAlebra.id<=neg_neg`**: `x <= neg (neg x)`, the unit of double-negation.
- **`HeytingAlebra.neg_join`**: `neg (x ∨ y) = neg x ∧ neg y`, De Morgan for join.

#### Negated Elements

- **`HeytingAlebra.IsNegated`**: Predicate `neg (neg x) <= x`, characterizing the elements fixed by double negation.
- **`HeytingAlebra.bottom-negated`**: `bottom` is negated.
- **`HeytingAlebra.meet-negated`**: Meets of negated elements are negated.
- **`HeytingAlebra.neg-negated`**: Every `neg x` is negated.

#### Continuation and Double Negation

- **`HeytingAlebra.continuation`**: `continuation y x := (x --> y) --> y`, the continuation monad applied at `y`.
- **`HeytingAlebra.double-exponent-increasing`**: `x <= (x --> y) --> y`, unit of the continuation.
- **`HeytingAlebra.double-exponent-idempotent`**: Iterated double continuation collapses: `(((x --> y) --> y) --> y) --> y <= (x --> y) --> y`.
- **`HeytingAlebra.double-exponent-monotone`**: Continuation at `x` is monotone in its target: `y <= z` gives `continuation x y <= continuation x z`.

#### Adjunction with Meet

- **`HeytingAlebra.exponent-unit`**: `x <= y --> x ∧ y`, unit of the meet⊣exponential adjunction.

#### Functoriality

- **`HeytingAlebra.exponent-functor`**: For each `x`, `(x --> -)` as an endofunctor on the Heyting algebra viewed as a category.
