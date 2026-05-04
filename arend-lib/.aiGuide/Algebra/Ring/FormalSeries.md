### Algebra.Ring.FormalSeries

Formal power series over a ring, represented as functions `Nat -> R`, with ring/algebra/domain structure and a homomorphism from polynomials.

#### Core Type

- **`FSeries`**: Formal power series over a set `R`, defined as `Nat -> R` (the `n`-th value is the coefficient of `x^n`).
- **`fseries-apply`**: Pointwise extraction from an equality of series: `x = y -> x n = y n`.

#### Ring Structure

- **`FSeriesRing`**: `Ring` instance on `FSeries R` for a ring `R`. Zero is the constant-zero series, addition is pointwise, and multiplication is the Cauchy product `(f * g) n = Σ_{i+j=n} f i * g j` via `R.FinSum` over `PairsFinSet n`. Identity is `coef 1` (the series `1, 0, 0, ...`).
- **`FSeriesRing.coef`**: Embeds a scalar `a : R` as the constant series `a, 0, 0, ...` (value `a` at `0`, `0` elsewhere).
- **`FSeriesRing.coef_/=0`**: `coef a n = 0` whenever `n /= 0`.
- **`FSeriesRing.PairsFinSet`**: Finite set of triples `(i, j, i + j = n)` of cardinality `suc n`, used to index the Cauchy convolution sum.
- **`FSeriesRing.PairsFinSet-ext1`**, **`FSeriesRing.PairsFinSet-ext2`**: Extensionality lemmas for nested `PairsFinSet` pairs (used for associativity of multiplication).
- **`FSeriesRing.Pairs_zero`**: `FinSum` over `PairsFinSet 0` reduces to the single term `f (0, 0, idp)`.
- **`FSeriesRing.Pairs_suc-left`**: Recurrence splitting `FinSum` over `PairsFinSet (suc n)` into the `(0, suc n)` term plus a sum over `PairsFinSet n` shifted by `suc`.

#### Algebra Structure

- **`FSeriesAlgebra`**: `CAlgebra R` instance over a commutative ring `R`. Scalar multiplication acts pointwise (`(c *c x) n = c * x n`), `coefMap` is the constant-series embedding, and multiplication is commutative when `R` is.

#### Apartness and Domains

- **`FSeriesRingWith#`**: `Ring.With#` instance: a series is apart from zero iff some coefficient is apart from zero (`x #0 := ∃ n, x n #0`).
- **`FSeriesCRingWith#`**: Commutative version of `FSeriesRingWith#`.
- **`FSeriesDomain`**: `Domain` instance on `FSeries R` for a domain `R`. Apartness of a product is established via `aux`, which finds an index where `(x * y) k` is apart from zero given that some `x n` and `y m` are.
- **`FSeriesDomain.aux`**: Inductive helper bounding the search index by `n + m < k`, producing some `k` with `#0 ((x * y) k)`.
- **`FSeriesIntegralDomain`**: `IntegralDomain` instance, combining `FSeriesDomain` with commutativity from `FSeriesAlgebra`.

#### Polynomial Embedding

- **`poly-FSeries`**: `RingHom` from `PolyRing R` to `FSeriesRing R` sending a polynomial to its coefficient sequence (`polyCoef`). Preserves addition, identity, and multiplication.
- **`poly-FSeries.*-lem`**: Compatibility of `polyCoef` with multiplication: `polyCoef (p * q) n = (polyCoef p * polyCoef q) n` in `FSeriesRing R`.

#### Geometric Series

- **`geometric-FSeries`**: The constant-one series `\lam _ => 1` is a multiplicative inverse (in `FSeriesRing R`) of the polynomial coefficients of `1 - x` (i.e. `padd (padd pzero -1) 1`), giving the formal identity `1 / (1 - x) = 1 + x + x^2 + ...`.
