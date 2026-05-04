### Arith.Real

This module defines the Dedekind real numbers (`Real`) and provides an ordered abelian group structure, lattice operations, and interaction lemmas with rationals and other real-number representations.

#### Lower–Upper Relation

- **`<LU`**: `x <LU y` holds when there exists a rational `a` with `x.U a` and `y.L a` (for `ExUpperReal` and `LowerReal`).
- **`<LU-transitive`**: `x <LU y` and `y <= z` imply `x <LU z`.

#### Real Class

- **`Real`**: Extends `InfReal` and `UpperReal`. A Dedekind real is determined by lower and upper cuts `L`, `U` on `Rat` satisfying locatedness and a focus condition.
  - **`LU-focus`**: For any `eps > 0`, there exist `a` in `L` with `U (a + eps)`.
  - **`fromRat`**: Coercion embedding `Rat` into `Real`.
  - **`real-ext`**: Extensionality: reals with the same lower cut are equal.
  - **`real-lower-ext`**: Equality as `LowerReal` implies equality as `Real`.
  - **`fromRat-inj`**: `fromRat` is injective.
  - **`<=-upper`**: `x <= y` in `RealAbGroup` iff `x <= y` in `ExUpperReal`.
  - **`=-upper`**: `x = y` as `Real` iff `x = y` as `ExUpperReal`.

#### Homomorphism

- **`real-upperReal`**: `AddMonoidHom` from `RealAbGroup` to `ExUpperRealAbMonoid` via the identity.

#### Instances

- **`RealPointed`**: `Pointed Real` with `ide = fromRat 1`.
- **`RealAbGroup`**: `LinearlyOrderedAbGroup Real` providing `+`, `negative`, `meet`, `join`, `zro = fromRat 0`, and all ordered lattice-group laws.
- **`RealDenseOrder`**: `UnboundedDenseLinearOrder` for `Real`.

#### RealAbGroup Where-Block

- **`+` (addition)**: Defined via `InfRealAbMonoid.+` with upper bound witness.
- **`+_L`** / **`+_U`**: Characterize `L` and `U` of `x + y` in terms of `L`/`U` of `x` and `y`.
- **`+-upper`**: `x + y` equals `x ExUpperReal.+ y`.
- **`+-lower`**: `x + y` equals `x LowerRealAbMonoid.+ y`.
- **`+-rat`**: `x + y` as reals equals `x + y` as rationals (for rational inputs).
- **`+-inf`**: `x + y` equals `x InfRealAbMonoid.+ y`.
- **`*n-upper`**: `n *n x` in `RealAbGroup` equals `n *n x` in `ExUpperRealAbMonoid`.
- **`negative`**: Negation swapping `L` and `U` via `RatField.negative`.
- **`negative_L`** / **`negative_U`**: Characterize `L`/`U` of `negative x`.
- **`negative-rat`**: `negative x = RatField.negative x` for rational `x`.
- **`minus-rat`** / **`diff-rat`**: `x - y` as reals equals `x - y` as rationals.
- **`meet`**: Binary meet (infimum) of two reals.
- **`meet-inf`**: `meet x y` equals `InfRealAbMonoid.meet x y`.
- **`meet_L`** / **`meet_U`**: Characterize `L`/`U` of `meet x y`.
- **`join`**: Binary join (supremum) of two reals.
- **`join-inf`**: `join x y` equals `InfRealAbMonoid.join x y`.
- **`join_L`** / **`join_U`**: Characterize `L`/`U` of `join x y`.
- **`join-rat`**: `join x y = x ∨ y` for rational `x`, `y`.
- **`join-upper`**: `join x y` equals `ExUpperReal.join x y`.
- **`zro<ide`**: `0 < 1` in `Real`.
- **`lower_<-char`** / **`lower_<=-char`**: `<` and `<=` in `LowerRealAbMonoid` iff in `RealAbGroup`.
- **`abs-rat`**: `abs x` as real equals `abs x` as rational.
- **`dist-rat`**: `abs (x - y)` as real equals `abs (x - y)` as rational.
- **`half+half`**: `half x + half x = fromRat x`.

#### Ordering Characterizations

- **`real_<-rat-char`**: `x < y` iff there exists rational `a` with `x.U a` and `y.L a`.
- **`real_<-char`**: `x < y` iff there exists rational `a` with `x < a` and `a < y`.
- **`real_<_L`**: `(a : Real) < x` iff `x.L a`.
- **`real_<_U`**: `x < (a : Real)` iff `x.U a`.
- **`real_<_InfReal`** / **`real_<=_InfReal`**: `<` and `<=` on `Real` iff on `InfRealAbMonoid`.
- **`rat_real_<`** / **`rat_real_<=`**: `<` and `<=` on `Rat` iff on `Real` via `fromRat`.
- **`rat_real_meet`** / **`rat_real_join`**: `fromRat` preserves meet and join.
- **`rat_real_abs`**: `fromRat` preserves absolute value.

#### Characterizations via Cuts

- **`<=_L-char`**: `x <= y` iff `x.L a` implies `y.L a` for all `a`.
- **`<=_U-char`**: `x <= y` iff `y.U a` implies `x.U a` for all `a`.

#### Locatedness and Focus

- **`real-located`**: For reals `a < b`, either `a < x` or `x < b`.
- **`real-focus`**: For any `x` and `eps > 0`, there exists `a < x < a + eps`.
- **`real_join_L`**: If `x.L a` and `x.L b`, then `x.L (a ∨ b)`.
- **`LU-focus-bound-real`**: Focus with a real bound: either `B < x` or there exists `a` with `a < x < a + eps`.
- **`inf-real-located`**: Locatedness for `InfReal` against real bounds.

#### InfReal to Real Conversion

- **`inf-real-real`**: Converts an `InfReal` with an explicit upper bound `B` into a `Real`.
- **`inf-real-bounded`**: Converts an `InfReal` with a truncated upper bound witness into a `Real`.
