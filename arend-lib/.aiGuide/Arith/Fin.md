### Arith.Fin

Modular arithmetic on finite types `Fin (suc n)` and integer modular reduction lemmas.

This module establishes that the finite type `Fin (suc n)` carries the standard structure of integers modulo `n+1`. It begins with utility lemmas about integer and natural division/modulo (used to bridge `Int` and `Nat` arithmetic on residues), then defines a positional base-`b` digit decomposition, and finally builds the algebraic hierarchy on `Fin (suc n)`: a commutative decidable ring, a Euclidean ring structure, and — when `suc n` is prime — a discrete field via Bézout-derived inverses. The design routes computations through `IntEuclidean` so that ring identities on `Fin (suc n)` reduce to identities on `Int` modulo `suc n`.

#### Integer Modular Arithmetic Lemmas

- **`int_n*_+_mod_n`**: `(pos (suc n) * q + r) mod suc n = r` when `r < suc n`; reduction kills the multiple of the modulus.
- **`int_n*_+_mod_n=mod`**: `(pos (suc n) * q + r) mod suc n = r mod suc n`; absorbs a multiple of the modulus inside `mod`.
- **`int_mod_*-left`**: `(pos (a mod suc n) * b) mod suc n = (a * b) mod suc n`; `mod` distributes through left multiplication.
- **`int_mod_*-right`**: Right-multiplication analogue: `(a * b mod suc n) mod suc n = (a * b) mod suc n`.

#### Natural Division Lemmas

- **`div_suc-lem`**: `(n + m) div m = suc (n div m)` for `m /= 0`; adding the divisor increments the quotient.
- **`n*_+_div_n=div`**: `(m * q + r) div m = q + r div m`; quotient on a `mq + r` decomposition.
- **`*_div=id`**: `(q * m) div m = q` for nonzero `m`; division cancels a clean multiple.
- **`div-monotone`**: Monotonicity of division: `n <= k` implies `n div m <= k div m` (proved via the helper `aux` by induction on a bounding constant).

#### Base-`b` Digit Decomposition

- **`base-digit`**: `base-digit n b j` extracts the `j`-th digit of `n` in base `b` by iterating `div b` and taking `mod b`.
- **`base-digit-sum`**: Inverse property: if all entries of an array `l` are below `b`, then `l j` recovers as `base-digit` of `BigSum (\lam j => l j * b^j)`.

#### Ring Structure on `Fin (suc n)`

- **`FinRing`**: Instance of `CRing.Dec` on `Fin (suc n)`. Addition and multiplication are inherited from `Nat` (with the result coerced back to `Fin`), `negative x = iabs (suc n Nat.- x)`, `natCoef = Fin.fromNat`, and equality is decidable. Provides the canonical commutative ring of integers modulo `suc n`.
- **`FinRing.mod-mod`**: Idempotence: `a mod pos (suc n) Nat.mod suc n = a mod pos (suc n)` (as a `Nat`).
- **`FinRing.ldiv-modEq`**: If `suc n` divides `x - y` (in `Int`), then `x mod suc n = y mod suc n`.
- **`FinRing.modEq-ldiv`**: Converse: equality of residues yields a left-divisibility witness `Monoid.LDiv (suc n) (x - y)` in `IntEuclidean`.
- **`FinRing.finEq-modeq`**: Equality in `Fin (suc n)` implies equality of `Nat` residues mod `suc n`.
- **`FinRing.ldiv-finEq`**: Divisibility of `x Nat.- y` by `suc n` yields equality in `Fin (suc n)`.
- **`FinRing.intCoef-char`**: Characterizes the integer coefficient map: `intCoef k = k mod suc n`.
- **`FinRing.intCoef_pos-char`**: For natural `k`, `intCoef (pos k) = k` (under the `Fin` coercion).
- **`FinRing.intCoef-surj`**: The integer coefficient map into `FinRing` is surjective; `aux` shows `natCoef k = k` for `k : Fin (suc n)`.

#### Euclidean Structure

- **`FinEuclidean`**: Instance of `EuclideanRingData` on `Fin (suc n)` extending `FinRing`. The Euclidean map is the identity `Fin (suc n) -> Nat`, and `divMod x y` lifts `Nat.divMod` and reduces both quotient and remainder mod `suc n`.

#### Field Structure (Prime Modulus)

- **`FinField`**: Instance of `DiscreteField` on `Fin (suc n)` when `suc n` is prime, extending `FinRing`. The inverse `finv x` is computed from Bézout coefficients of `pos (suc n)` and `pos x` via `IntEuclidean.natDivMod`, reduced mod `suc n`. Establishes `Z/p` as a discrete field.
- **`FinField.gcd=1`**: For a prime `p` and `0 /= x < p`, `gcd p x = 1`; the key coprimality fact underlying invertibility of nonzero residues mod a prime.
