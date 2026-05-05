### Arith.Real.LowerReal

This module defines lower Dedekind reals (left cuts) and their algebraic structure.

#### ExtendedLowerReal

- **`ExtendedLowerReal`**: Class with fields `L : Rat -> \Prop`, `L-closed` (downward closed), `L-rounded` (no maximum). May be empty.
  - **`fromRat`**: Coercion from `Rat`; `L q = (q < x)`.

#### LowerReal

- **`LowerReal`**: Extends `ExtendedLowerReal` with `L-inh` (inhabitedness of `L`).
  - **`fromRat`**: Coercion from `Rat`.
  - **`BJoin`**: Binary join of a base `LowerReal` with a family of `ExtendedLowerReal`s: `L q = b.L q || ∃ (j : J) ((f j).L q)`.
  - **`BJoin-bound`**: `b <= BJoin b f`.
  - **`BJoin-cond`**: `f j <= BJoin b f` for each `j`.
  - **`BJoin-univ`**: Universal property of `BJoin`.

#### LowerRealAbMonoid Instance

- **`LowerRealAbMonoid`**: Instance of `BiorderedLatticeAbMonoid` for `LowerReal`.
  - **`+`**: Addition of lower reals; `L a = ∃ (b : x.L) (c : y.L) (a < b + c)`.
  - **`+_L`**: Characterization of `(x + y).L a`.
  - **`+-rat`**: `fromRat x + fromRat y = fromRat (x + y)`.
  - **`<=`**: `x <= y` iff `∀ {a : x.L} (y.L a)`.
  - **`<`**: `x < y` iff `∃ (b : y.L) (x <= b)`.
  - **`lower_<_L`**: `(a : LowerReal) < x <-> x.L a`.
  - **`meet`**: Meet (infimum) of two lower reals; `L a = (x.L a, y.L a)`.
  - **`meet_L`**: Characterization of meet's lower cut.
  - **`join`**: Join (supremum) of two lower reals; `L a = x.L a || y.L a`.
  - **`join_L`**: Characterization of join's lower cut.
  - **`zro<ide`**: `0 < 1`.
  - **`<=-char`**: If `Not (x.L a)` then `x <= fromRat a`.
  - **`<_+`**: `a < c` and `b < d` imply `a + b < c + d`.

#### ExtendedLowerRealLattice Instance

- **`ExtendedLowerRealLattice`**: Instance of `CompleteLattice` for `ExtendedLowerReal`, with pointwise join as `Join`.
