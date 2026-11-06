### Arith

This directory formalizes concrete number systems and elementary functions: booleans, naturals, integers, rationals, finite residues, constructive reals and complex numbers, with exponential, logarithm, trigonometry and the fundamental theorem of algebra.

#### Discrete Number Systems

- **`Bool.md`** — Order structure on `Bool`: a bounded distributive lattice (`and` as meet, `or` as join) and a decidable linear order with `false <= true`.
- **`Nat.md`** — Natural numbers: truncated subtraction, order, semiring instances, divisibility, and supporting arithmetic infrastructure.
- **`Int.md`** — Integers as `pos`/`neg` with the decidable ordered commutative ring `IntRing`, sign, absolute value, and basic number-theoretic operations.
- **`Rat.md`** — Rationals as reduced fractions: a discrete ordered field with decidable equality and `makeRat` renormalization.
- **`Fin.md`** — Modular arithmetic on `Fin (suc n)`, integer modular reduction lemmas, and positional base-`b` digit decomposition.
- **`Prime.md`** — Primality on `Nat`: the abstract `Prime` predicate, divisibility characterizations, and a decidable `isPrime` test by trial division.

#### Reals and Complex Numbers

- **`Real.md`** — Constructive reals as Dedekind cuts with the focus (locatedness) property, extending `InfReal` and `UpperReal`.
- **`Complex.md`** — Complex numbers as pairs of reals with their field structure.

#### Exponential and Logarithm

- **`Exp.md`** — The exponential `exp` on real Banach algebras as the sum of the power series `Σ xⁿ/n!`, with its continuous map `expMap`.
- **`Log.md`** — The series log(1 + w) on the unit ball of a real Banach algebra, its coefficient bounds, and `exp (log (1 + w)) = 1 + w`.

#### Subdirectories

- **`Algebraic/`** — Algebraic numbers as the integral closure of Q in C, with their algebraic closedness, and Newton iteration, separability and Taylor estimates for complex polynomials.
- **`Complex/`** — Complex modulus, normed and Banach structure, polar form, roots, Euler's formula, and the fundamental theorem of algebra (Kneser's lemma, classical proof, Richman's choice-free spectrum).
- **`Exp/`** — Real exponential: positivity, strict monotonicity and injectivity.
- **`Fin/`** — Decidable linear order on `Fin n`.
- **`Log/`** — Logarithm identities in Banach algebras and the real logarithm on the positive reals, inverse to `exp`.
- **`Nat/`** — Euler's totient function with units of `Z/nZ` and the Chinese remainder theorem; minimal witnesses and finiteness for decidable predicates on `Nat`.
- **`Real/`** — Reals: Dedekind-cut variants (lower, upper, extended), field structure, suprema and infima, rational approximation, intermediate value theorem, roots, and π.
- **`Trig/`** — Real and complex sine and cosine, and arctangent, with addition, Pythagorean and sum-to-product identities.
