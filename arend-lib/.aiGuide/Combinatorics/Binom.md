### Combinatorics.Binom

Binomial coefficients on natural numbers and the binomial theorem for commutative rings.

This module defines `binom n i` via Pascal's recurrence — using the standard double base case (the column `i = 0` and the row `n = 0`) so that values above the diagonal collapse to zero — and then derives the two basic boundary lemmas plus the binomial expansion of `(x + y)^n` in a commutative ring. The expansion is stated as a `BigSum` over an array of length `suc n`, with each term scaled by `natCoef (binom n i)` and pairing `pow x i` with `pow y (iabs (n - i))` so that the exponents are computed via integer subtraction and then taken back to a natural number via `iabs`. This setup makes the lemma directly usable when reasoning about polynomial identities and ring expansions.

#### Definition

- **`binom`**: Binomial coefficient `binom n i : Nat`, defined by Pascal's recurrence: `binom _ 0 = 1`, `binom 0 _ = 0`, and `binom (suc n) (suc i) = binom n i + binom n (suc i)`.

#### Boundary Lemmas

- **`binom.binom_0`**: `binom n 0 = 1` for every `n`.
- **`binom.binom_<`**: Vanishing above the diagonal: if `n < m`, then `binom n m = 0`.

#### Binomial Theorem

- **`binom.expansion`**: Binomial expansion in a commutative ring `R`: `pow (x + y) n` equals the `BigSum` over `i : Fin (suc n)` of `natCoef (binom n i) * (pow x i * pow y (iabs (n - i)))`, using integer subtraction with `iabs` to express the complementary exponent.
