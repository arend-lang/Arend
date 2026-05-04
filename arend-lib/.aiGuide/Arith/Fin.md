### Arith.Fin

This module provides modular arithmetic on integers, division lemmas for natural numbers, base-digit decomposition, and ring/field instances for `Fin (suc n)`.

#### Integer Modular Arithmetic

- **`int_n*_+_mod_n`**: `(pos (suc n) * q + r) mod suc n = r` when `r < suc n`, for integer `q`.
- **`int_n*_+_mod_n=mod`**: `(pos (suc n) * q + r) mod suc n = r mod suc n` for integer `q` and `r`.
- **`int_mod_*-left`**: `(pos (a mod suc n) * b) mod suc n = (a * b) mod suc n` for integers `a`, `b`.
- **`int_mod_*-right`**: `(a * b mod suc n) mod suc n = (a * b) mod suc n` for integers `a`, `b`.

#### Division Lemmas for Nat

- **`div_suc-lem`**: `(n + m) div m = suc (n div m)` when `m ≠ 0`.
- **`n*_+_div_n=div`**: `(m * q + r) div m = q + r div m` when `m ≠ 0`.
- **`*_div=id`**: `(q * m) div m = q` when `m ≠ 0`.
- **`div-monotone`**: `n <= k` implies `n div m <= k div m`.
  - **`aux`**: Helper with an explicit bound parameter.

#### Base-Digit Decomposition

- **`base-digit`**: Extracts the `j`-th digit of `n` in base `b`: `base-digit n b 0 = n mod b`, `base-digit n b (suc j) = base-digit (n div b) b j`.
- **`base-digit-sum`**: If all entries of an array `l` are less than `b`, then `l j = base-digit (BigSum (λ j => l j * b^j)) b j`.

#### FinRing Instance

- **`FinRing`**: Instance of `CRing.Dec` for `Fin (suc n)`, providing `+`, `*`, `negative`, `zro`, `ide`, commutativity, associativity, distributivity, and decidable equality. Arithmetic operations are performed modulo `suc n`.
  - **`mod-mod`**: `(a mod pos (suc n)) mod (suc n) = a mod pos (suc n)` (idempotence of mod on representatives).
  - **`ldiv-modEq`**: If `suc n` divides `x - y` (as integers), then `x mod suc n = y mod suc n`.
  - **`modEq-ldiv`**: Converse: `x mod suc n = y mod suc n` implies `suc n` divides `x - y`.
  - **`finEq-modeq`**: `x = y` in `Fin (suc n)` implies `x mod suc n = y mod suc n` as `Nat`.
  - **`ldiv-finEq`**: If `suc n` divides `x - y` (as integers), then `x = y` in `Fin (suc n)`.
  - **`intCoef-char`**: `intCoef k = k mod suc n` in `FinRing`.
  - **`intCoef_pos-char`**: `intCoef (pos k) = k` in `FinRing`.
  - **`intCoef-surj`**: `intCoef` is surjective for `FinRing`.
    - **`aux`**: `natCoef k = k` for `k : Fin (suc n)`.

#### FinEuclidean Instance

- **`FinEuclidean`**: Instance of `EuclideanRingData` for `Fin (suc n)`, with Euclidean map being the identity and division/modulo lifted from `Nat` then reduced modulo `suc n`.

#### FinField Instance

- **`FinField`**: Instance of `DiscreteField` for `Fin (suc n)` when `suc n` is prime. Provides field inverse via the Bézout identity.
  - **`gcd=1`**: If `p` is prime and `0 < x < p`, then `gcd p x = 1`.
