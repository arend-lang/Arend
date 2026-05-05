### Arith.Real.InfReal

This module defines infimum reals (two-sided Dedekind cuts without upper bound inhabitedness) and their ordered additive monoid structure.

#### InfReal Class

- **`InfReal`**: Extends `LowerReal` and `ExUpperReal` with `LU-disjoint` (L and U don't overlap), `LU-located` (for `q < r`, either `L q` or `U r`), and `LU-focus-bound` (focusing with a bound parameter).
  - **`fromRat`**: Coercion from `Rat`.
  - **`pow>id`**: `(3/2)^n > n`.
  - **`focus-iter`**: Iterated focusing: given a contraction `f` with ratio `rat`, after `n` iterations the interval shrinks by `rat^n`.
  - **`real-ext`**: Extensionality: pointwise `L`-equivalence implies equality.
  - **`real-lower-ext`**: Equality as `LowerReal` implies equality as `InfReal`.

#### InfRealAbMonoid Instance

- **`InfRealAbMonoid`**: Instance of `LinearlyBiorderedAbMonoid` for `InfReal`.
  - **`+`**: Addition combining `LowerRealAbMonoid.+` and `ExUpperReal.+` with `LU-disjoint` and `LU-focus-bound`.
  - **`+-lower`**: `x + y = x LowerRealAbMonoid.+ y` as `LowerReal`.
  - **`+-rat`**: `fromRat x + fromRat y = fromRat (x + y)`.
  - **`+_L`**: Characterization of `(x + y).L a`.
  - **`+_L_<=`**: Non-strict variant of `+_L`.
  - **`+-upper`**: `x + y = x ExUpperReal.+ y` as `ExUpperReal`.
  - **`+_U`**: Characterization of `(x + y).U a`.
  - **`+_U_<=`**: Non-strict variant of `+_U`.
  - **`<`**: `x < y` iff `∃ (a : Rat) (x.U a) (y.L a)`.
  - **`<=_L`**: `Not (y < x)` iff `∀ {a : x.L} (y.L a)`.
  - **`<=_U`**: `Not (y < x)` iff `∀ {b : y.U} (x.U b)`.
  - **`meet`** / **`meet_L`** / **`meet_U`**: Meet of two `InfReal`s with cut characterizations.
  - **`meet-lower`**: `meet x y = LowerRealAbMonoid.meet x y` as `LowerReal`.
  - **`join`** / **`join_L`** / **`join_U`**: Join of two `InfReal`s with cut characterizations.
  - **`join-lower`**: `join x y = LowerRealAbMonoid.join x y` as `LowerReal`.
  - **`zro<ide`**: `0 < 1`.
  - **`<_L`**: `(a : InfReal) < x <-> x.L a`.
  - **`<_U`**: `x < (a : InfReal) <-> x.U a`.
  - **`<_+`**: `a < c` and `b < d` imply `a + b < c + d`.

#### Miscellaneous

- **`inf-real_<_LowerReal`**: `x < y` as `InfReal` iff `x < y` as `LowerReal`.
