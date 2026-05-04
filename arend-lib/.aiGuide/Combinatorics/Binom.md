### Combinatorics.Binom

Binomial coefficients and the binomial theorem expansion for commutative rings.

#### Definition

- **`binom`**: Binomial coefficient `binom n i` (i.e., `n choose i`) defined recursively via Pascal's rule: `binom (suc n) (suc i) = binom n i + binom n (suc i)`, with `binom _ 0 = 1` and `binom 0 _ = 0`.

#### Basic Lemmas

- **`binom_0`**: `binom n 0 = 1` for any `n`.
- **`binom_<`**: Vanishing above the diagonal: if `n < m` then `binom n m = 0`.

#### Binomial Theorem

- **`expansion`**: The binomial theorem in a commutative ring `R`: `(x + y)^n = Σᵢ binom(n,i) · x^i · y^(n-i)`, expressed as a `BigSum` over an array of length `suc n` with the natural-number coefficient injected via `natCoef`.
