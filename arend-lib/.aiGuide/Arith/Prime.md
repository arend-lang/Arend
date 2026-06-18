### Arith.Prime

Primality for natural numbers, connecting the abstract `Prime` predicate from monoid theory to a concrete decidable test.

This module bridges three views of primality on `Nat`: the algebraic notion `Prime` (irreducibility in a monoid), divisibility-based characterizations (no proper divisors other than 1), and a computable boolean test `isPrime` based on trial division by odd numbers up to the square root. The equivalences between these views are packaged so that primality on `Nat` becomes decidable, with `prime-isDec` as the user-facing result. The trial-division algorithm only checks odd divisors `j` while `j * j <= n`, which is the source of the `isOdd`-based bookkeeping lemmas.

#### Abstract Characterizations

- **`nat_irr-isPrime`**: An irreducible element of `NatSemiring` is `Prime` (with itself as the witness of non-invertibility).
- **`prime-div`**: Equivalence `Prime n = (n /= 1) ∧ (∀ k, LDiv k n -> k = n ∨ k = 1)` — primality as "only divisors are 1 and `n`".
  - **`prime-div.dir`**, **`prime-div.conv`**: The two directions of the equivalence.
- **`prime-less`**: Equivalence `Prime n = (n > 1) ∧ (∀ k, k < n -> LDiv k n -> k = 1)` — primality as "no nontrivial divisor below `n`".
  - **`prime-less.dir`**, **`prime-less.conv`**: The two directions.

#### Decidable Test

- **`isPrime`**: Boolean primality test on `Nat`. Returns `false` for 0 and 1, `true` for 2, and otherwise checks that `n` is odd and has no odd divisor `j` with `j * j <= n`.
  - **`isPrime.isPositive`**: `Bool` predicate testing `n /= 0`, used to read off `n mod k /= 0`.
  - **`isPrime.<`**: Boolean less-than on `Nat` via `Int` subtraction.
  - **`isPrime.rec`**: Recursive trial-division loop: for fuel `c` starting at `j`, returns true once `n < j * j`, otherwise requires `j ∤ n` and recurses with `j + 2`.

#### Soundness: Boolean Test Implies `Prime`

- **`isPrime=>prime`**: If `isPrime n = true`, then `Prime n`.
- **`isPrime=>prime.mod_div-lem`**: If `n mod k` is positive then `k` does not divide `n`.
- **`isPrime=>prime.isOdd`**: Propositional predicate "`n` is odd", defined by recursion on `suc (suc n)`.
- **`isPrime=>prime.square_<=-lem`**: `k <= k * k`.
- **`isPrime=>prime.odd_suc-lem`**: `n` and `n + 1` cannot both be odd.
- **`isPrime=>prime.odd-lem`**: Two distinct odd numbers `j <= k` differ by at least 2: `suc (suc j) <= k`.
- **`isPrime=>prime.rec-lem`**: Core invariant of the trial-division loop: if `rec n c j = true` and `k` is an odd divisor of `n` with `j <= k` and `k * k <= n`, derive a contradiction.
- **`isPrime=>prime.oddOrEven`**: Every natural is either odd or divisible by 2.
- **`isPrime=>prime.prime-lem`**: Reduces primality to: `n > 1`, `2 ∤ n`, and no odd `k >= 3` with `k * k <= n` divides `n`.
- **`isPrime=>prime.prime-lem2`**: Reduces primality to: `n > 1` and every divisor `j` with `j * j <= n` equals 1.

#### Completeness: `Prime` Implies Boolean Test

- **`prime=>isPrime`**: If `Prime n`, then `isPrime n = true`.
- **`prime=>isPrime.mod-lem`**: For prime `n`, any `k < n` with `k /= 1` satisfies `n mod k > 0`.
- **`prime=>isPrime.<-lem`**: Reflects the propositional `<` on `Nat` into the boolean `isPrime.<`.
- **`prime=>isPrime.square_<-lem`**: For `j > 1`, `suc j < j * j` (used to terminate the loop).
- **`prime=>isPrime.rec-lem`**: For prime `n` and any `j > 1`, the trial-division loop returns `true`.

#### Decidability

- **`prime-isDec`**: `Prime n` is decidable on `Nat`, by case analysis on `isPrime n` and the two directions above.
