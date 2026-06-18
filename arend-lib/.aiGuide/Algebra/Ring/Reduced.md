### Algebra.Ring.Reduced

Reduced rings: rings with no nonzero nilpotent elements, plus several strengthenings.

A ring is **reduced** when `a * a = 0` implies `a = 0`; this propagates to `pow a n = 0 -> a = 0` via a doubling trick that reduces arbitrary powers to powers of two. The hierarchy layers commutativity (`ReducedCRing`), the existence of annihilator-witnessing idempotent multipliers (`PPRing`, i.e. principally projective / pp-rings), and the impotent condition where idempotents are forced to be `0` or `1` (`ImpotentRing`). On commutative reduced rings, divisibility behaves cancellatively: a divisor of one factor in a zero/equal product determines the other factor uniquely, and these cancellation lemmas are also given in `pow`-divisibility form for use with radical-style arguments.

#### Reduced Rings

- **`ReducedRing`**: Extends `Ring` with `isReduced : a * a = 0 -> a = 0`.
- **`ReducedRing.noNilpotent`**: Any nilpotent is zero: `pow a n = 0 -> a = 0`.
- **`ReducedRing.noNilpotent.aux`**: Auxiliary form for power-of-two exponents `pow a (2^n) = 0 -> a = 0`, used to bootstrap `noNilpotent` by rounding `n` up to a power of two.

#### Reduced Commutative Rings

- **`ReducedCRing`**: Extends `ReducedRing` and `CRing`; reduced commutative rings, where divisibility cancellation becomes available.
- **`ReducedCRing.div_zero`**: If `a` divides `b` and `a * b = 0`, then `b = 0`.
- **`ReducedCRing.div_pow_zero`**: Variant where `a` divides a power `pow b n`: still `a * b = 0 -> b = 0`.
- **`ReducedCRing.div-cancel`**: If `a` divides both `b` and `b'` and `a * b = a * b'`, then `b = b'` (left cancellation through a common divisor).
- **`ReducedCRing.div_pow-cancel`**: Same cancellation when `a` only divides powers of `b` and `b'`.
- **`ReducedCRing.div_div-cancel`**: Lifts a divisibility `LDiv (a * b) (a * b')` to `LDiv b b'` when `a` divides both `b` and `b'`.
- **`ReducedCRing.div_pow_div-cancel`**: The `pow`-divisibility analogue of `div_div-cancel`.

#### PP-Rings

- **`PPRing`**: Extends `ReducedCRing` with `isPPRing`: for every `a` there exists `u` with `a = u * a` and `u` annihilating everything `a` annihilates. Such `u` acts as an idempotent generator of the annihilator complement; reducedness is derived automatically from this data.
- **`PPRing.bezout->strictBezout`**: Upgrades a Bézout structure on a pp-ring to a strict Bézout structure (where the gcd witnesses are coprime in a refined sense).

#### Impotent Rings

- **`ImpotentRing`**: Extends `ReducedRing` and `NonZeroRing`; idempotents are trivial: `a * a = a -> (a = 0) || (a = 1)`. Models rings with no nontrivial connected components (e.g. integral domains, local rings up to reducedness).
- **`ImpotentRing.decomp`**: From a partition of unity `a + b = 1` with `a * b = 0`, one of `a`, `b` is `1` and the other is `0`.
- **`ImpotentRing.subring`**: Transports the `ImpotentRing` structure along an injective ring homomorphism `f : R -> E` into an impotent ring, equipping the source `R` with an `ImpotentRing` instance.

#### Impotent Commutative Rings

- **`ImpotentCRing`**: Extends `ImpotentRing`, `ReducedCRing`, and `NonZeroCRing`; the commutative version of `ImpotentRing`.
- **`ImpotentCRing.subring`**: Commutative analogue of `ImpotentRing.subring`: pulls back an impotent structure along an injective ring homomorphism from a `CRing`, yielding an `ImpotentCRing`.
