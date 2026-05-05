### Arith.Prime

This module provides primality definitions, characterizations, a decision procedure, and related lemmas for natural numbers.

#### Primality Characterizations

- **`nat_irr-isPrime`**: An irreducible natural number is prime: `Irr n` implies `Prime n`.
- **`prime-div`**: Characterization of `Prime n` as: `n ≠ 1` and every divisor `k` of `n` satisfies `k = n` or `k = 1`.
  - **`dir`**: Forward direction: `Irr n` and `LDiv k n` imply `k = n || k = 1`.
  - **`conv`**: Converse: the divisor condition implies `Prime n`.
- **`prime-less`**: Characterization of `Prime n` as: `n > 1` and every `k < n` dividing `n` satisfies `k = 1`.
  - **`dir`**: Forward direction.
  - **`conv`**: Converse.

#### Primality Testing (`isPrime`)

- **`isPrime`**: Boolean primality test for `Nat`. Returns `false` for `0` and `1`, `true` for `2`, and for `n >= 3` checks oddness and trial division up to `√n`.
  - **`isPositive`**: Helper returning `false` for `0`, `true` otherwise.
  - **`<`** (boolean): Boolean less-than via integer subtraction.
  - **`rec`**: Recursive trial division: checks divisibility by odd numbers starting from `j`, bounded by counter `c`.

#### Correctness of `isPrime`

- **`isPrime=>prime`**: `isPrime n = true` implies `Prime n`.
  - **`mod_div-lem`**: `isPositive (n mod k) = true` implies `Not (LDiv k n)`.
  - **`isOdd`**: Predicate for odd natural numbers (defined recursively).
  - **`square_<=-lem`**: `k <= k * k`.
  - **`odd_suc-lem`**: `isOdd j` and `isOdd (suc j)` is contradictory.
  - **`odd-lem`**: Two distinct odd numbers with `j <= k` satisfy `suc (suc j) <= k`.
  - **`rec-lem`**: Main inductive lemma: if `rec` returns `true`, no odd divisor in range divides `n`.
  - **`oddOrEven`**: Every natural number is odd or divisible by `2`.
  - **`prime-lem`**: If `n > 1`, not divisible by `2`, and no odd `k >= 3` with `k² <= n` divides `n`, then `Prime n`.
  - **`prime-lem2`**: If `n > 1` and every `j` with `j² <= n` dividing `n` equals `1`, then `Prime n`.
- **`prime=>isPrime`**: `Prime n` implies `isPrime n = true`.
  - **`mod-lem`**: If `n` is prime and `k < n` with `k ≠ 1`, then `isPositive (n mod k) = true`.
  - **`<-lem`**: `n < m` implies `n isPrime.< m = true`.
  - **`square_<-lem`**: `j > 1` implies `suc j < j * j`.
  - **`rec-lem`**: If `n` is prime and `j > 1`, then `rec n c j = true`.

#### Decidability

- **`prime-isDec`**: Decidable primality: `Dec (Prime n)`, using `isPrime` and the correctness lemmas.
