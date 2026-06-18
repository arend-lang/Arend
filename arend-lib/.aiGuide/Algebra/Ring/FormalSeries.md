### Algebra.Ring.FormalSeries

Formal power series over a ring, represented as functions `Nat -> R` indexed by their coefficients.

A formal series is encoded as `FSeries R := Nat -> R`, mapping each natural number `n` to the coefficient of `x^n`. Multiplication is the Cauchy convolution computed via a finite sum over index pairs `(i, j)` with `i + j = n`. The module lifts the ring/algebra/domain structure of `R` pointwise to `FSeries R`, and connects polynomials to formal series by exhibiting the coefficient map `polyCoef` as a ring homomorphism `PolyRing R -> FSeriesRing R`.

#### Core Type

- **`FSeries`**: `FSeries R := Nat -> R`, the type of formal power series over a set `R`, indexed by coefficient position.
- **`fseries-apply`**: Pointwise extraction from a path of series: `x = y -> x n = y n`.

#### Ring Structure

- **`FSeriesRing`**: Ring instance on `FSeries R` for any `Ring R`. Addition is pointwise; the unit `ide` is `coef 1`; multiplication is the Cauchy product `(f * g) n = Σ_{i+j=n} f i * g j` via `R.FinSum` over `PairsFinSet n`.
- **`FSeriesRing.coef`**: Embeds a constant `a : R` as the series with `coef a 0 = a` and `coef a (suc _) = 0`.
- **`FSeriesRing.coef_/=0`**: For `n /= 0`, `coef a n = 0`.
- **`FSeriesRing.PairsFinSet`**: The finite set `{(i, j) | i + j = n}` of cardinality `suc n`, indexing the convolution sum.
- **`FSeriesRing.PairsFinSet-ext1`**, **`FSeriesRing.PairsFinSet-ext2`**: Extensionality lemmas for nested pairs in iterated convolutions, used in associativity proofs.
- **`FSeriesRing.Pairs_zero`**: Reduction of the convolution sum at `n = 0`: `FinSum f = f (0, 0, idp)`.
- **`FSeriesRing.Pairs_suc-left`**: Recursive splitting of the convolution at `suc n`, separating the `(0, suc n)` term from the rest.

#### Algebra Structure

- **`FSeriesAlgebra`**: `CAlgebra R` instance on `FSeries R` for a commutative ring `R`. Scalar multiplication is pointwise: `(c *c x) n = c * x n`; the coefficient map sends `a` to `coef a`.

#### Apartness and Domain Structure

- **`FSeriesRingWith#`**: `Ring.With#` instance lifting an apartness relation: `x #0` iff there exists some index `n` with `x n #0` in `R`.
- **`FSeriesCRingWith#`**: Commutative version of `FSeriesRingWith#` for `CRing.With#`.
- **`FSeriesDomain`**: `Domain` instance on `FSeries R` for a domain `R`. The auxiliary lemma `aux` proves that if `x n #0` and `y m #0`, then some coefficient of `x * y` is apart from `0`, establishing `#0-*` for the convolution product.
- **`FSeriesDomain.aux`**: Existence of a nonzero coefficient in the product, given nonzero coefficients in each factor at indices summing below a bound.
- **`FSeriesIntegralDomain`**: `IntegralDomain` instance on `FSeries R` for an integral domain `R`.

#### Polynomial-to-Series Embedding

- **`poly-FSeries`**: Ring homomorphism `PolyRing R -> FSeriesRing R` given by the coefficient extraction map `polyCoef`. Witnesses that polynomials sit inside formal series as those with finitely many nonzero coefficients.
- **`poly-FSeries.*-lem`**: Compatibility of `polyCoef` with multiplication: `polyCoef (p * q) n` equals the Cauchy product of the coefficient series.
- **`geometric-FSeries`**: The constant series `1, 1, 1, …` is invertible in `FSeriesRing R`, with inverse the image of the polynomial `1 - x` (i.e. `padd (padd pzero -1) 1`). Encodes the geometric series identity `(1 - x) · Σ x^n = 1`.
