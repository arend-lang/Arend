### Arith.Nat

This module provides arithmetic operations, ordering, and properties for natural numbers (`Nat`).

#### Basic Functions

- **`-'` (truncated subtraction)**: `n -' m` returns `n - m` if `n >= m`, otherwise `0`.
- **`pred`**: Predecessor function; `pred 0 = 0`, `pred (suc x) = x`.
- **`suc/=0`**: Proves `suc n ≠ 0` (eliminates into `Empty`).
- **`toFin`**: Converts a `Nat` less than `n` into `Fin n`.
- **`toFin'`**: Alternative conversion from `Nat` to `Fin n` via `mod`.
- **`mod_Fin`**: Converts a `Nat` to `Fin n` using modular reduction.
- **`mod_div`**: Constructs an `LDiv m n` proof from `n mod m = 0`.

#### Lemmas on `pred` and `suc`

- **`suc_pred`**: `suc (pred n) = n` when `n ≠ 0`.

#### Lemmas on Truncated Subtraction (`-'`)

- **`-'0`**: `n -' 0 = n`.
- **`-'+`**: `n + m -' m = n`.
- **`-'id`**: `n -' n = 0`.
- **`-'-'`**: `a -' b -' c = a -' (b + c)`.
- **`-'_<`**: `0 < n -' m` implies `m < n`.
- **`<_-'`**: `m < n` implies `0 < n -' m`.
- **`-'<=id`**: `n -' m <= n`.
- **`-'+-comm`**: `n + k -' m = n -' m + k` when `m <= n`.
- **`-'-monotone-left`**: `n -' k <= m -' k` when `n <= m`.
- **`-'-monotone-right`**: `k -' n <= k -' m` when `m <= n`.
- **`-'_<=`**: `n -' m = 0` implies `n <= m`.

#### NatOrder Module

- **`<` (strict order on `Nat`)**: Inductively defined with constructors `zero<suc` and `suc<suc`.
- **`unsuc<`**: `suc n < suc m` implies `n < m`.

#### Ordering Lemmas

- **`id<suc`**: `n < suc n`.
- **`id/=suc`**: `n ≠ suc n`.
- **`nonZero>0`**: `n ≠ 0` implies `0 < n`.
- **`fin_<`**: Any `x : Fin n` satisfies `x < n`.
- **`zero<=_`**: `0 <= x` for all `x`.
- **`suc<=suc`**: `x <= y` implies `suc x <= suc y`; with `conv` for the converse.
- **`<=_exists`**: `n <= m` implies `n + (m -' n) = m`.
- **`suc_<_<=`**: `n < m` implies `suc n <= m`.
- **`<_suc_<=`**: `n < suc m` implies `n <= m`.
- **`suc_<=_<`**: `suc n <= m` implies `n < m`.
- **`<=_<_suc`**: `n <= m` implies `n < suc m`.
- **`id<=suc`**: `n <= suc n`.
- **`<=_*`**: Monotonicity of multiplication: `n <= m` and `k <= l` imply `n * k <= m * l`.
- **`monotone-diagonal`**: If `suc (f n) <= f (suc n)` for all `n`, then `n <= f n`.
- **`sequence-monotone`**: A monotone sequence preserves `<=` across indices.
- **`sequence-anti-monotone`**: An anti-monotone sequence reverses `<=` across indices.

#### Fin Lemmas

- **`toFin=id`**: `toFin k p` equals `k` as a `Nat`.
- **`toFin=fin`**: `toFin k (fin_< k) = k` in `Fin n`.
- **`toFin'=id`**: `toFin' p` equals `k` as a `Nat`.
- **`fin_nat-inj`**: Equality of `Fin n` elements as `Nat` implies equality in `Fin n`.
- **`fin_nat-ineq`**: Inequality in `Fin n` implies inequality as `Nat`.
- **`mod_Fin=mod`**: `mod_Fin k p` equals `k mod n` as a `Nat`.
- **`mod_Fin_<`**: `mod_Fin k p` equals `k` as `Nat` when `k < n`.
- **`mod_Fin=id`**: `mod_Fin k p = k` in `Fin n` when `k : Fin n`.
- **`fin_mod_id`**: `x mod suc n = x` for `x : Fin (suc n)`.

#### NatSemiring Instance

- **`NatSemiring`**: Instance of `LinearlyOrderedCSemiring.Dec` for `Nat`, providing `+`, `*`, `<`, `zro`, `ide`, commutativity, associativity, distributivity, and decidable trichotomy.
  - **`triEquals`**: `n - m = 0` implies `n = m`.
  - **`triGreater`**: `n - m = suc d` implies `m < n`.
  - **`triLess`**: `n - m = neg (suc d)` implies `n < m`.
  - **`cancel-right`**: `n + k = m + k` implies `n = m`.
  - **`cancel-left`**: `n + m = n + k` implies `m = k`.
  - **`cancel_*-left`**: `k * n = k * m` with `k ≠ 0` implies `n = m`.
  - **`cancel_*-right`**: `n * k = m * k` with `k ≠ 0` implies `n = m`.

#### NatBSemilattice Instance

- **`NatBSemilattice`**: Instance of `BottomJoinSemilattice` for `Nat` with bottom element `0`.
  - **`<=_cancel-left`**: `n + m <= n + k` implies `m <= k`.
  - **`<=_cancel-right`**: `m + n <= k + n` implies `m <= k`.
  - **`ldistr0`**: `x ∧ 0 = 0`.
  - **`rdistr0`**: `0 ∧ x = 0`.

#### Division and Modular Arithmetic

- **`n*_+_<n`**: `n * q + r < n` implies `q = 0`.
- **`mod-unique`**: Uniqueness of remainder in division.
- **`div-unique`**: Uniqueness of quotient in division.
- **`mod<=left`**: `n mod m <= n`.
- **`mod<right`**: `n mod m < m` when `m ≠ 0`.
- **`div_<`**: `n < m` implies `n div m = 0`.
- **`mod_<`**: `n < m` implies `n mod m = n`.
- **`div_mod`**: `LDiv m n` implies `n mod m = 0`.
- **`id_mod`**: `n mod n = 0`.
- **`div_*<=id`**: `n div m * m <= n`.
- **`n*_+_mod_n`**: `(n * q + r) mod n = r` when `r < n`.
- **`n*_+_mod_n=mod`**: `(suc n * q + r) mod suc n = r mod suc n`; with `nat` variant for general `n`.
- **`mod_+-left`**: `(a mod suc n + b) mod suc n = (a + b) mod suc n`.
- **`mod_+-right`**: `(a + b mod suc n) mod suc n = (a + b) mod suc n`.
- **`mod_*-left`**: `(a mod suc n * b) mod suc n = (a * b) mod suc n`.
- **`mod_*-right`**: `(a * b mod suc n) mod suc n = (a * b) mod suc n`.
- **`mod_+-cong-left`**: Congruence of `mod` under addition on the left.
- **`mod_+-cong-right`**: Congruence of `mod` under addition on the right.

#### Miscellaneous

- **`natUnit`**: `n * m = 1` implies `m = 1`.
- **`natAssociates-areEqual`**: Mutual divisibility of naturals implies equality.
- **`ldiv_<=`**: `LDiv n m` with `m ≠ 0` implies `n <= m`.
- **`nat_<=-dec`**: Decidable `<=` via boolean check `n -' m == 0`.
- **`nat_<-dec`**: Decidable `<` via boolean check.
- **`id<pow2`**: `n < 2^n`.
