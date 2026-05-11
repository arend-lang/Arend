### Arith.Real

Constructive real numbers as Dedekind-style cuts that are simultaneously lower- and upper-bounded.

A `Real` extends both `InfReal` (lower-located reals) and `UpperReal`, combining a rounded lower set `L` and rounded upper set `U` with the **focus** property: for every `eps > 0` there is a rational `a` with `L a` and `U (a + eps)`. This focus condition is equivalent to the classical "located" property and implies `LU-located`, making the cut tight enough to compute arbitrarily good rational enclosures. The module builds the linearly ordered abelian group structure on `Real` (addition, negation, lattice operations, order) by transporting from `InfReal`/`UpperReal` while sharpening their constructions to preserve focus, and provides the rational embedding `fromRat` together with characterization lemmas relating `Real` operations to those on the constituent cut types and on `Rat`.

#### Core Definitions

- **`<LU`**: Strict comparison `x <LU y` between an `ExUpperReal` and a `LowerReal`: `∃ (a : Rat) (x.U a) (y.L a)`.
- **`<LU-transitive`**: Transitivity of `<LU` along a lower-real inequality.
- **`Real`**: Record extending `InfReal` and `UpperReal` with the **focus** axiom `LU-focus`: for every `eps > 0`, some rational `a` satisfies `L a` and `U (a + eps)`. Derives `LU-located`, `L-inh`, `U-inh` automatically.
- **`Real.LU_*-focus-right`**: From `0 ∈ L` and `1 < d`, produces `0 < a`, `L a`, and `U (a * d)` (multiplicative right-focus).
- **`Real.LU_*-focus-left`**: Dually, from `0 ∈ L` and `c < 1`, produces `b` with `L (b * c)` and `U b`.
- **`Real.fromRat`** (coerce): Embeds a rational `x : Rat` as a real with `L = (· < x)`, `U = (x < ·)`.
- **`real-ext`**: Extensionality: two reals agree iff their lower sets agree on rationals.
- **`real-lower-ext`**: Equality of reals is determined by equality at the `LowerReal` level.
- **`fromRat-inj`**: Injectivity of the rational embedding.
- **`<=-upper`**, **`=-upper`**: `RealAbGroup` order/equality coincides with `ExUpperReal` order/equality on `Real`.
- **`real-upperReal`**: The inclusion `Real → ExUpperReal` is an additive monoid homomorphism.

#### Algebraic Structure

- **`RealPointed`**: `Pointed Real` with `ide = 1`.
- **`RealAbGroup`**: `LinearlyOrderedAbGroup Real` with `0`, `1`, `+`, `negative`, `meet`, `join`, and positivity `isPos x = x.L 0`.
- **`RealAbGroup.+`**: Sum of reals; combines the `InfReal` sum with the upper-set `U-inh` to preserve focus.
- **`RealAbGroup.+_L`** / **`+_U`**: Characterize membership in `L`/`U` of `x + y` via existence of rational summands `b ∈ x.L`, `c ∈ y.L` (resp. `x.U`, `y.U`) with `a < b + c` (resp. `b + c < a`).
- **`+-upper`**, **`+-lower`**, **`+-inf`**: Real addition agrees with `ExUpperReal`, `LowerReal`, and `InfReal` addition respectively.
- **`+-rat`**: Rational embedding preserves addition.
- **`*n-upper`**: Natural-number scaling agrees with the `ExUpperReal` version.
- **`RealAbGroup.negative`**: Pointwise negation; swaps `L` and `U` via rational negation.
- **`negative_L`**, **`negative_U`**: Characterize `L`/`U` of `negative x`.
- **`negative-rat`**, **`minus-rat`**, **`diff-rat`**: Rational embedding preserves negation and subtraction.
- **`RealAbGroup.meet`** / **`join`**: Lattice operations on reals; for `meet`, `L` is intersection and `U` is union; for `join`, dually.
- **`meet_L`**, **`meet_U`**, **`join_L`**, **`join_U`**: Cut-level characterizations of meet/join.
- **`meet-inf`**, **`join-inf`**, **`join-upper`**: Compatibility with `InfReal` / `ExUpperReal` lattice structure.
- **`join-rat`**, **`rat_real_meet`**, **`rat_real_join`**, **`rat_real_abs`**, **`abs-rat`**, **`dist-rat`**: Rational embedding commutes with `∧`, `∨`, absolute value, and distance.
- **`half+half`**: `½ x + ½ x = x` at the rational embedding.
- **`zro<ide`**: `0 < 1` in `Real`.

#### Order and Comparison

- **`real_<-rat-char`**: `x < y` iff some rational lies between via `x.U a` and `y.L a`.
- **`real_<-char`**: `x < y` iff some rational `a` separates them as reals: `x < a` and `a < y`.
- **`real_<_L`** / **`real_<_U`**: A rational lies in `x.L` (resp. `x.U`) iff it is strictly below (resp. above) `x` as a real.
- **`real_<_InfReal`**, **`real_<=_InfReal`**: Comparison agrees with the `InfReal` ordering.
- **`rat_real_<`**, **`rat_real_<=`**: The embedding `Rat → Real` is order-reflecting and order-preserving.
- **`lower_<-char`**, **`lower_<=-char`**: `LowerReal` order coincides with `RealAbGroup` order on `Real`.
- **`<=_L-char`**, **`<=_U-char`**: `x <= y` iff `x.L ⊆ y.L`, equivalently `y.U ⊆ x.U`.
- **`RealDenseOrder`**: `UnboundedDenseLinearOrder` instance — reals form a dense linear order without upper or lower bound.

#### Locatedness and Focus Lemmas

- **`real-located`**: From `a < b` in `Real`, any real `x` satisfies `a < x` or `x < b` (located comparison).
- **`real-focus`**: For any real `x` and `eps > 0`, there exists `a` with `a < x < a + eps` (real-valued focus).
- **`real_join_L`**: If `x.L a` and `x.L b` then `x.L (a ∨ b)` (lower set is upward-directed).
- **`LU-focus-bound-real`**: For an `InfReal` `x`, real bound `B`, and `eps > 0`: either `B < x` or there exists a real `a < x` with `x < a + eps`.
- **`inf-real-located`**: Located comparison of `InfReal` against a real interval.

#### Promoting `InfReal` to `Real`

- **`inf-real-real`**: Given `x : InfReal` with `x < B` for some real `B`, returns the corresponding `Real` (uses `B` to supply `U-inh`).
- **`inf-real-bounded`**: Same construction taking the bound existentially: `∃ (B : Real) (x < B)`.
