### Topology.BanachAlgebra

Banach algebras: complete normed rings that are simultaneously Banach spaces, with a constructive square root for elements near the identity.

This module layers algebra structure on top of `BanachSpace`, producing `BanachAlgebra` over a generic complete normed field and `RealBanachAlgebra` specialized to `Real`. The centerpiece is a constructive square root `sqrt` defined on the closed unit ball around `1` (i.e. `norm (1 - x) <= 1`) via a Newton-style iteration `y_{n+1} = (1 - x + y_n^2)/2`, with `z_n = 1 - y_n` converging uniformly to a square root of `x`. The bounding sequence `rfunc` over rationals controls the iteration's growth and convergence, letting the construction transfer through the cover-space completion. The `BanachAlgebraCompletion` instance lifts the multiplication of a `RealPreBanachAlgebra` to its Banach completion using locally uniform extension.

#### Main Classes

- **`BanachAlgebra`**: Extends `BanachSpace`, `QAlgebra`, and `CompleteExNormedRing`; a complete normed unital algebra over the rationals.
  - **`fromRat`**: Embeds a rational `q` as `q *q 1` in the algebra.
- **`RealPreBanachAlgebra`**: Extends `RealPreBanachSpace` and `ExPseudoNormedRing`; a pre-Banach (not necessarily complete) algebra over the reals.
- **`RealBanachPseudoAlgebra`**: Extends `RealBanachSpace`, `QPseudoAlgebra`, `ExPseudoNormedPseudoRing`, and `CompleteExNormedAbGroup`; the pseudo (possibly non-unital, non-Hausdorff) variant over the reals.
- **`RealBanachAlgebra`**: Extends `RealBanachPseudoAlgebra`, `BanachAlgebra`, and `RealPreBanachAlgebra`; the full real Banach algebra.

#### Scalar/Multiplication Compatibility (in `RealBanachPseudoAlgebra`)

- **`*r-comm-left`**: `r *r (a * b) = r *r a * b` — real scalar passes through to the left factor.
- **`*r-comm-right`**: `r *r (a * b) = a * (r *r b)` — real scalar passes through to the right factor.
- **`*r_>=0-square`**: A nonnegative-real scaling of a square remains a square.
- **`*q_>=0-square`**: A nonnegative-rational scaling of a square remains a square.
- **`toRealAlgebra`**: Reinterprets the structure as a `PseudoAlgebra` over `RealField`, combining `toRealModule` with the existing ring structure.

#### Square Root Construction (in `RealBanachAlgebra`)

- **`sqrt`**: Given `x : E` with `norm (1 - x) <= 1`, produces `sqrt x : E` as the limit of the Newton iteration `zfunc n x`.
- **`sqrt.UnitBall`**: The pseudometric subspace of `E` consisting of elements satisfying `norm (1 - x) <= 1`.
- **`sqrt.yfunc`**: The auxiliary sequence with `y_0 = 0` and `y_{n+1} = (1 - x + y_n^2)/2`.
- **`sqrt.zfunc`**: The shifted iterates `z_n = 1 - y_n`, which converge to `sqrt x`.
- **`sqrt.zfunc-lim`**: Defines `sqrt x` as `funcLimit` of `zfunc` over `UnitBall` using its functional convergence.
- **`sqrt.yfunc<=rfunc`**: Bounds `norm (yfunc n s.1) <= rfunc n` uniformly on the unit ball.
- **`sqrt.yfunc-comm`**, **`sqrt.zfunc-comm`**: If `a` commutes with `x`, it commutes with each iterate.
- **`sqrt.yfunc-rec`**: Recurrence `y_{n+2} - y_{n+1} = ((y_{n+1} + y_n)(y_{n+1} - y_n))/2`.
- **`sqrt.yfunc_rfunc-diff1`**, **`sqrt.yfunc_rfunc-diff`**: Step-by-step and telescoped bounds `norm (y_{n+k} - y_n) <= rfunc(n+k) - rfunc n`.
- **`sqrt.zfunc_rfunc-diff`**: Same Cauchy-style bound for the `zfunc` sequence.
- **`sqrt.zfunc-uni`**, **`sqrt.zfunc-funcConv`**: Uniform/functional convergence of `zfunc` on `UnitBall`.
- **`sqrt.yfunc-cover`**, **`sqrt.zfunc-cover`**: Each iterate is a cover map, enabling passage to the completion.
- **`sqrt.isSquare`**: `sqrt x * sqrt x = x` — the constructed value squares back to `x`.
- **`sqrt.isSquare.dist-lem`**: Identity `z_n^2 - x = 2 *q (y_{n+1} - y_n)` driving the limit argument.
- **`sqrt.comm`**: If `y * x = x * y`, then `sqrt x` commutes with `y`.

#### Square Predicates

- **`norm-square`**: If `norm (1 - x) <= 1`, then `x` is a square (`IsSquare x`).
- **`norm-square'`**: If `norm x <= 1`, then `1 - x` is a square.
- **`norm_*q-square`**: If `norm (q *q 1 - x) <= q` for `q > 0`, then `x` is a square (rescaled version).

#### Rational Bounding Sequence (in `\where` of `RealBanachAlgebra`)

- **`rfunc`**: Rational sequence with `r_0 = 0` and `r_{n+1} = (1 + r_n^2)/2`, bounding the iterates.
- **`rfunc>=0`**, **`rfunc<=1`**: `rfunc n` lies in `[0, 1]`.
- **`rfunc-inc`**: The sequence is monotone nondecreasing.
- **`rfunc-rec`**: Recurrence `r_{n+2} - r_{n+1} = ((r_{n+1} + r_n)(r_{n+1} - r_n))/2`.
- **`rfunc-bound`**: Geometric tail estimate `1 - rfunc n <= (1 - eps/2)^n` whenever `rfunc n <= 1 - eps`.
- **`rfunc-limit`**: `rfunc` converges to `1` in the topological sense.

#### Completion

- **`BanachAlgebraCompletion`**: Instance promoting any `RealPreBanachAlgebra X` to a `RealBanachAlgebra` on its `BanachCompletion`, with multiplication, unit (`pointCF 1`), associativity, distributivity, identity laws, and submultiplicative norm bounds inherited from the locally uniform lift.
- **`BanachAlgebraCompletion.*-cover`**: The product cover map `Completion X ⨯ Completion X -> Completion X` obtained by `lift2` of locally uniform multiplication.
- **`BanachAlgebraCompletion.*-func`**: The induced binary multiplication on regular Cauchy filters.
