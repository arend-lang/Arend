### Arith.Real.InfReal

Constructive real numbers extended with positive infinity, presented as Dedekind-style cuts of rationals.

An `InfReal` packages a lower cut `L : Rat -> \Prop` (from `LowerReal`) together with an upper cut `U : Rat -> \Prop` (from `ExUpperReal`, which permits `U` to be empty so `+∞` is representable). The defining axioms are disjointness `LU-disjoint` and a constructive locatedness-with-bound principle `LU-focus-bound`: for any `B` and `eps > 0`, either `B` is in the lower cut (the number exceeds `B`) or there is some `a` in `L` with `a + eps` in `U` (a finite `eps`-bracket). Standard locatedness `LU-located` is derived from `LU-focus-bound` by interval bisection (via `focus-iter`, which iterates a contraction with ratio `3/2`), and the lower-cut inhabitedness `L-inh` is also derived. Equality of `InfReal` values is determined by the lower cut alone (`real-lower-ext`), so the upper cut is recovered from `L` plus the bracketing axiom.

#### Main Record

- **`InfReal`**: Extends `LowerReal` and `ExUpperReal`. Adds `LU-disjoint` (cuts don't overlap), `LU-focus-bound` (constructive bracketing allowing `+∞`), and a default `LU-focus-bound-impl` proof. `LU-located` and `L-inh` are derived from `LU-focus-bound`. The internal lemma `LU-less` gives `L q -> U r -> q < r`.

#### Coercion and Helpers

- **`fromRat`** *(coercion)*: Embeds a rational `x : Rat` into `InfReal` using `LowerReal.fromRat` and `ExUpperReal.fromRat`.
- **`pow>id`**: For `n : Nat`, `(3/2)^n > n`; used to bound the number of bisection steps in `focus-iter`.
- **`focus-iter`**: Iterated contraction lemma: given a relation `P` with a one-step shrink rule, after `n` iterations one obtains `q', r'` with `(r' - q') * rat^n <= r - q`. Used to build `LU-focus-bound` from `LU-located` by bracket bisection.
- **`real-ext`**: Two `InfReal`s with equivalent lower cuts are equal.
- **`real-lower-ext`**: An `InfReal` equality reduces to equality at the `LowerReal` level.

#### Algebraic Structure

- **`InfRealAbMonoid`**: Instance of `LinearlyBiorderedAbMonoid InfReal`, equipping `InfReal` with `0 = fromRat 0`, addition, strict order `<`, and lattice operations `meet`, `join`, satisfying biordered abelian monoid laws (no negation, since `+∞` is allowed).

#### Addition

- **`+`** *(sfunc)*: Adds two `InfReal`s by combining their `LowerReal` and `ExUpperReal` addends.
- **`+-lower`**: `x + y` equals `x +_LowerReal y` as `LowerReal`s.
- **`+-upper`**: `x + y` equals `x +_ExUpperReal y` as upper-real structures.
- **`+-rat`**: `fromRat x + fromRat y = fromRat (x + y)`; the embedding is a homomorphism.
- **`+_L`**: Characterizes the lower cut of a sum: `L(x+y, a) <-> ∃ b c, L(x,b) ∧ L(y,c) ∧ a < b + c`.
- **`+_L_<=`**: Sufficient condition for `L(x+y, a)` from witnesses with `a <= b + c`.
- **`+_U`**: Characterizes the upper cut of a sum: `U(x+y, a) <-> ∃ b c, U(x,b) ∧ U(y,c) ∧ b + c < a`.
- **`+_U_<=`**: Sufficient condition for `U(x+y, a)` from witnesses with `b + c <= a`.

#### Order

- **`<`**: `x < y` defined as `∃ a : Rat, U(x,a) ∧ L(y,a)` — there is a rational strictly between `x` and `y`.
- **`<=_L`**: `¬(y < x) <-> ∀ a, L(x,a) -> L(y,a)`; the negation of `<` is lower-cut inclusion.
- **`<=_U`**: `¬(y < x) <-> ∀ b, U(y,b) -> U(x,b)`; equivalently, upper-cut inclusion in the other direction.
- **`zro<ide`**: `0 < 1` in `InfReal`.
- **`<_L`**: For a rational `a`, `a < x <-> L(x,a)`.
- **`<_U`**: For a rational `a`, `x < a <-> U(x,a)`.
- **`<_+`**: Addition is strictly monotone in both arguments: `a < c -> b < d -> a + b < c + d`.

#### Lattice Operations

- **`meet`** *(sfunc)*: Pointwise minimum via the underlying `LowerReal`/`ExUpperReal` meets.
- **`meet-lower`**: Equality with `LowerRealAbMonoid.meet` at the `LowerReal` level.
- **`meet_L`**: `L(meet x y, a) <-> L(x,a) ∧ L(y,a)`.
- **`meet_U`**: `U(meet x y, a) <-> U(x,a) ∨ U(y,a)`.
- **`join`** *(sfunc)*: Pointwise maximum via the underlying joins.
- **`join-lower`**: Equality with `LowerRealAbMonoid.join` at the `LowerReal` level.
- **`join_L`**: `L(join x y, a) <-> L(x,a) ∨ L(y,a)`.
- **`join_U`**: `U(join x y, a) <-> U(x,a) ∧ U(y,a)`.

#### Cross-Module Comparison

- **`inf-real_<_LowerReal`**: The `InfReal` strict order agrees with the `LowerReal` strict order: `x < y <-> x <_LowerReal y`. Lets one transport order facts between the two presentations.
