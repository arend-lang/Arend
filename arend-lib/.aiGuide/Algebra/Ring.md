### Algebra.Ring

Ring structures built on top of pseudo-semirings and abelian groups, ranging from non-unital rings to commutative rings with extra structure.

This module organizes the ring hierarchy by combining the additive abelian group structure (`AbGroup`) with multiplicative semiring structure. `PseudoRing` covers the non-unital case where the zero-absorption laws are derived from distributivity and additive cancellation. `Ring` adds a multiplicative identity, integer coefficients, and basic arithmetic with negation, while `CRing` specializes to the commutative setting and exposes Bézout-style divisibility, Krull-dimension bounds, and zero-dimensionality. The nested `With#` and `Dec` subclasses thread an apartness relation `#0` (or decidable equality) through the ring axioms so that constructive results about non-zero divisors and divisibility can be stated.

#### Pseudo-Rings (Non-Unital)

- **`PseudoRing`**: Non-unital ring extending `PseudoSemiring` and `AbGroup`; derives `zro_*-left` and `zro_*-right` from distributivity via additive cancellation.
- **`PseudoRing.negative_*-left`**: `negative x * y = negative (x * y)`.
- **`PseudoRing.negative_*-right`**: `x * negative y = negative (x * y)`.
- **`PseudoRing.negative_*`**: `negative x * negative y = x * y` (sign-cancellation).
- **`PseudoRing.ldistr_-`**: `x * (y - z) = x * y - x * z`.
- **`PseudoRing.rdistr_-`**: `(x - y) * z = x * z - y * z`.
- **`PseudoRing.*_sum_diff`**: Difference-of-squares `(x + y) * (x - y) = x*x - y*y` under a commuting hypothesis.
- **`PseudoRing.*_diff_sum`**: Difference-of-squares `(x - y) * (x + y) = x*x - y*y` under a commuting hypothesis.
- **`PseudoRing.op`**: Opposite pseudo-ring (multiplication reversed).

#### Unital Rings

- **`Ring`**: Unital ring extending `PseudoRing` and `Semiring`.
- **`Ring.intCoef`**: Canonical map `Int -> E` sending `pos n` to `natCoef n` and `neg n` to `negative (natCoef n)`.
- **`Ring.intCoef_neg`**: Compatibility of `intCoef` with negation: `intCoef (neg n) = negative (natCoef n)`.
- **`Ring.negative_ide-left`**: `negative ide * x = negative x`.
- **`Ring.negative_ide-right`**: `x * negative ide = negative x`.
- **`Ring.negative_inv`**: Negation of a unit is a unit, with inverse `negative j.inv`.
- **`Ring.geometric-partial-sum`**: `(1 - x) * Σ_{j<n} x^j = 1 - x^n`, the telescoping geometric identity.
- **`Ring.IsNilpotent`**: Predicate that some power of `x` is zero.
- **`Ring.pow_-1_+2`**: `(-1)^(n+2) = (-1)^n` (period-2 of sign powers).
- **`Ring.pow_-1_even`**: `(-1)^(2n) = 1`.
- **`Ring.pow_-1_mod2`**: `(-1)^n = (-1)^(n mod 2)`.
- **`Ring.idempotent_LDiv`**: If `b*b = b` and `b | a`, then `a = b*a`.
- **`Ring.IsConnected`**: Every idempotent is `0` or `1` (no nontrivial idempotents).
- **`Ring.IsVonNeumannRegular`**: For every `a` there exists `b` with `a = a*b*a`.
- **`Ring.op`**: Opposite ring.

#### Rings with Apartness / Decidability

- **`Ring.With#`**: Ring equipped with a tight apartness `#0` compatible with multiplication; requires `#0-*-left` and `#0-*-right` and derives `#0-negative`.
- **`Ring.With#.#0-FinSum`**: If a finite sum is apart from zero, some summand is.
- **`Ring.With#.#0-FinSum-conv`**: From a single nonzero summand, either the whole sum is nonzero or there is a second nonzero index.
- **`Ring.Dec`**: Ring with decidable equality, instantiating `Ring.With#` via the negation-of-equality apartness.

#### Non-Zero Rings and Pseudo-Commutativity

- **`NonZeroRing`**: Ring whose underlying semiring is non-zero (i.e. `0 ≠ 1`).
- **`PseudoCRing`**: Commutative pseudo-ring (extends `PseudoRing` and `PseudoCSemiring`).

#### Commutative Rings

- **`CRing`**: Commutative unital ring extending `Ring`, `PseudoCRing`, and `CSemiring`.
- **`CRing.IsBezout`**: Every pair `a, b` admits `s, t` such that `s*a + t*b` divides both `a` and `b` (a Bézout combination is a common divisor).
- **`CRing.IsStrictBezout`**: Strengthened Bézout: existence of `s, t, u, v` with `a*u = b*v` and `s*u + t*v = 1`.
- **`CRing.bezout_finitelyGenerated_principal`**: Equivalence between `IsBezout` and "every finitely generated ideal is principal".
- **`CRing.bezoutGCD`**: Construction of a GCD `s*a + t*b` of `a` and `b` from the data of a Bézout combination.
- **`CRing.Dim<=`**: Krull-dimension bound `≤ k`: for any `(k+1)`-tuple `x` there exist `a` and exponents `m` with `fold x a m = 0`.
- **`CRing.Dim<=.fold`**: The auxiliary recursive expression `pow x m * (fold xs as ms - x * a)` used in the dimension bound, with base value `1`.
- **`CRing.IsZeroDimensional`**: For each `a` some power `a^n` equals `a^(n+1) * b`.
- **`CRing.finite-zeroDimensional`**: A finite ring is zero-dimensional.
- **`CRing.With#`**: Commutative ring with apartness; `#0-*-right` is derived from `#0-*-left` by commutativity.
- **`CRing.Dec`**: Commutative ring with decidable equality.
- **`CRing.Dec.divQuotient_dec0`**: Decidability of `a = in~ 0` in the divisibility quotient.

#### Non-Zero Commutative Rings

- **`NonZeroCRing`**: Commutative ring with `0 ≠ 1`.
