### Arith.Real.Field

The ordered field structure on the real numbers, defining multiplication, inverse, and the `OrderedFieldAlgebra` instance over the rationals.

This module completes the algebraic structure of `Real` by extending the additive group `RealAbGroup` with multiplication, scalar action by rationals, and multiplicative inverses. Multiplication is constructed by lifting the locally uniform rational multiplication map through the cover-space completion (`real-lift2`), exploiting the fact that `RealNormed` is the completion of `RatNormed`. Uniqueness lemmas (`unique1`, `unique2`, `unique3`) provide the standard tool for proving identities on reals by checking them on rationals. The positive inverse is constructed directly from the Dedekind-cut presentation via `real-pos-inv`.

#### Main Instance

- **`RealField`**: The `OrderedFieldAlgebra RatField Real` instance, equipping `Real` with multiplication, unit, scalar action `*c = \lam a x => fromRat a * x`, coefficient embedding `coefMap = Real.fromRat`, natural-number coefficient map, and proofs of field/order axioms (`positive_*`, `positive=>#0`, `#0=>eitherPosOrNeg`).

#### Uniqueness via Density

- **`unique1`**: Two cover maps `f, g : RealNormed -> X` into a separated cover space are equal at any real `x` if they agree on all rationals.
- **`unique2`**: Binary version: agreement on rational pairs implies agreement on real pairs.
- **`unique3`**: Ternary version for maps from a triple product of `RealNormed`.

#### Multiplication

- **`*-rat-locally-uniform`**: Rational multiplication `(s.1, s.2) ↦ s.1 * s.2` is locally uniformly continuous on `RatNormed ⨯ RatNormed`.
- **`*-cover-def`**: The lifted cover map `RealNormed ⨯ RealNormed -> RealNormed`, obtained by composing `*-rat-locally-uniform` with `rat_real` and applying `real-lift2`.
- **`*`** (infixl 7): Multiplication on `Real`, defined as `*-cover-def (x, y)`.
- **`*-rat`**: Compatibility — multiplying rationals viewed as reals equals the rational product cast to `Real`.
- **`pow-rat`**: Real-power of a rational coincides with the rational power, embedded into `Real`.
- **`*-cover`**: Repackages `*` as a `CoverMap` from the product cover space `RealNormed ⨯ RealNormed` to `RealNormed`.

#### Cut Characterizations of the Product

- **`*_>=0-char`**: For non-negative `x, y`, the upper cut `(x * y).U d` holds iff there exist `a ∈ x.U`, `b ∈ y.U` with `a * b <= d`.
  - **`*_>0-char`**: If `y > 0` and `x * y < d`, there exists `a ∈ x.U` with `a * y < d`.
  - **`*_>0-char2`**: Refinement giving rational witnesses on both factors when `x >= 0` and `y > 0`.
- **`*-upper`**: For non-negative reals, `*` agrees with the multiplication on extended upper reals (`ExUpperReal.*`).
- **`pow-upper`**: For non-negative `x`, real-powering coincides with `ExUpperRealSemigroup.pow`.
- **`*_positive-char`**: For strictly positive `x, y`, characterizes membership of `x * y` in an open rational interval `(c, d)` via rational witnesses for `x` and `y`.
- **`*_positive-L`**: Characterizes the lower cut of `x * y` for positive `x, y`.
- **`*_positive-U`**: Characterizes the upper cut of `x * y` for positive `x, y`.

#### Multiplicative Inverse

- **`real-pos-inv`**: Constructs the inverse of a strictly positive real `x` directly as a Dedekind cut: lower cut consists of `a <= 0` or `x.U (finv a)`; upper cut consists of `0 < a` with `x.L (finv a)`.
- **`real-pos-inv>0`**: The inverse of a positive real is again positive (`L 0` holds).
- **`pos-inv_rat`**: For a positive rational `x`, the field inverse `RealField.pinv x` agrees with `Real.fromRat (finv x)`.
- **`finv-left`**: `finv x * x = 1` for nonzero rational `x`, computed as a real product.
- **`finv-right`**: `x * finv x = 1` for nonzero rational `x`.

#### Utilities

- **`half`**: `half x = x * ratio 1 2`, halves a real number.
