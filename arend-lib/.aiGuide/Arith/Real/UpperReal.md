### Arith.Real.UpperReal

This module defines upper Dedekind reals (right cuts) and their algebraic structure, including multiplication.

#### ExUpperReal

- **`ExUpperReal`**: Class with fields `U : Rat -> \Prop`, `U-closed` (upward closed), `U-rounded` (no minimum). May be empty.
  - **`fromRat`**: Coercion from `Rat`; `U q = (x < q)`.
  - **`fromRat-inj`**: `fromRat` is injective.
  - **`+`**: Addition; `U a = ∃ (b : x.U) (c : y.U) (b + c < a)`.
  - **`+_U`** / **`+_U_<=`**: Characterizations of `(x + y).U a` (strict and non-strict).
  - **`+-rat`**: `fromRat x + fromRat y = fromRat (x + y)`.
  - **`<=`**: `x <= y` iff `∀ {b : y.U} (x.U b)`.
  - **`<=_+-char`**: If `x <= y + z` and `y.U a` and `z.U b`, then `x.U (a + b)`.
  - **`<=-rat`**: `a <= b` as `Rat` iff `a <= b` as `ExUpperReal`.
  - **`<_<=`**: `x.U q` implies `x <= q`.
  - **`meet`** / **`meet_U`**: Meet; `U a = x.U a || y.U a`.
  - **`join`** / **`join_U`**: Join; `U a = (x.U a, y.U a)`.
  - **`join-bounded`**: Join of bounded upper reals is bounded.
  - **`*`**: Multiplication (for non-negative upper reals); `U a = ∃ (b : x.U) (0 < b) (c : y.U) (0 < c) (b * c < a)`.
  - **`*_U`** / **`*_U_<=`** / **`*_U_<`**: Characterizations of `(x * y).U a`.
  - **`*-rat`**: `fromRat x * fromRat y = fromRat (x * y)` when `x >= 0`, `y >= 0`.

#### ExUpperRealPointed

- **`ExUpperRealPointed`**: `Pointed ExUpperReal` with `ide = fromRat 1`.

#### real_meet_U

- **`real_meet_U`**: `x.U a` and `x.U b` imply `x.U (a ∧ b)`.

#### ExUpperRealAbMonoid Instance

- **`ExUpperRealAbMonoid`**: Instance of `BiorderedLatticeAbMonoid` for `ExUpperReal`.
  - **`<`**: `x < y` iff `∃ (q : x.U) (q <= y)`.
  - **`<-rat`**: `x < y` (as `Rat`) iff `x.U y`.
  - **`zro<ide`**: `0 < 1`.
  - **`<_+`** / **`<_+-left`** / **`<_+-right`**: Strict ordering is compatible with addition.
  - **`<=_+-cancel-left`** / **`<=_+-cancel-right`**: Cancellation of bounded upper reals in `<=` under addition.
  - **`*n_U`**: `(n *n x).U q` iff `∃ (r : x.U) (n * r <= q)`.
  - **`*n_finv`**: `(n *n x).U q` iff `x.U (finv n * q)`.

#### rat-upperReal

- **`rat-upperReal`**: `AddMonoidHom` from `RatField` to `ExUpperRealAbMonoid` via `fromRat`.

#### ExUpperRealSemigroup Instance

- **`ExUpperRealSemigroup`**: Instance of `CSemigroup` for `ExUpperReal` (multiplication).
  - **`ide-left`** / **`ide-right`**: `1 * x = x` and `x * 1 = x` when `x >= 0`.
  - **`ide-left_<=`**: `x <= 1 * x` (unconditional).
  - **`<=_*`**: Monotonicity of `*` w.r.t. `<=`.
  - **`*_join`**: `x * y = join x 0 * join y 0`.
  - **`<_*`** / **`<_*-left`** / **`<_*-right`** and primed variants: Strict monotonicity of `*`.
  - **`<_*_U-left`** / **`<_*_U-right`**: Upper bound propagation through `*`.
  - **`*_>=0`**: `0 <= x * y`.
  - **`ldistr`** / **`rdistr`**: Distributivity when arguments are `>= 0`.
  - **`ldistr_<=`** / **`rdistr_<=`**: Distributivity as `<=` (unconditional).
  - **`zro_*-left`** / **`zro_*-right`**: `0 * x = 0` when `x` is bounded.
  - **`*n_*_<=`** / **`*n_*`**: Relating `*n` (iterated addition) to `*`.
  - **`<_*-positive`**: `0 < x` and `0 < y` imply `0 < x * y`.
  - **`div-lb-rat`**: Given `a > 0` and `b > 0`, there exists `c > 0` with `a * c <= b`.
  - **`finv_<=-rotate-right`** / **`finv_<-rotate-right`** / **`finv_<-rotate-left`**: Rotation lemmas for `finv` and `*`.
  - **`pow`**: Power function for `ExUpperReal`.
  - **`rat-pow`**: `pow x n = fromRat (pow x n)` for rational `x >= 0`.
  - **`pow_<=`**: Monotonicity of `pow`.
  - **`pow_>=0`**: `0 <= pow x n`.
  - **`square_<=`** / **`square<=1`**: Square root–like bounds from `x * x <= q * q`.
  - **`square-bound-div`**: If `x * x <= q * x` and `x` is bounded, then `x <= q`.
  - **`*n-bounded`**: `n *n x` is bounded when `x` is bounded.

#### UpperReal

- **`UpperReal`**: Extends `ExUpperReal` with `U-inh` (inhabitedness of `U`).
  - **`ex-ext`**: Equality as `ExUpperReal` implies equality as `UpperReal`.

#### UpperRealSemigroup Instance

- **`UpperRealSemigroup`**: Instance of `CSemigroup` for `UpperReal` (multiplication).
  - **`*_U`**: Characterization of `(x * y).U a` for `UpperReal`.
  - **`*-ex`**: `x * y = x ExUpperReal.* y`.
