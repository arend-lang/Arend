### Arith Directory Overview

This directory provides formalized arithmetic for standard number types and related algebraic structures.

#### Core Number Types

- **`Nat.md`** / **`Nat/`**: Natural number arithmetic — truncated subtraction, ordering (`NatOrder`), `NatSemiring` (linearly ordered commutative semiring with decidable trichotomy), `NatBSemilattice`, division/modular arithmetic, `Fin` conversion utilities, and monotonicity lemmas. Subdirectory contains `Sequence.md` (well-ordering/search, `NatFinSubset`) and `EulerTotient.md` (Euler's totient, invertible subgroups, Chinese Remainder Theorem ingredients).
- **`Int.md`**: Integer arithmetic — `isuc`/`ipred`, `signum`, `IntRing` (`OrderedCRing.Dec`), absolute value (`iabs`), ordering lemmas, divisibility, Nat–Int interaction, units, and decidable ordering.
- **`Rat.md`**: Rational numbers — `Rat` data type with `makeRat`/`ratio` constructors, `RatField` (`DiscreteOrderedField`), floor/ceiling/rounding, decidable ordering, bounds, powers, and `RatDenseOrder`.
- **`Real.md`** / **`Real/`**: Real numbers via Dedekind cuts — `Real` class (two-sided located cuts), `RealAbGroup` (linearly ordered abelian group with `+`, `negative`, `meet`, `join`), `RealDenseOrder`, ordering/cut characterizations, locatedness. Subdirectory contains `LowerReal.md`, `UpperReal.md`, `InfReal.md` (extended/partial reals), `Field.md` (multiplication/inverse), `Approximate.md` (rational approximation), `Extremes.md` (sup/inf), `IVT.md` (intermediate value theorem), `Root.md` (square/n-th roots), `UpperRealLattice.md` (complete lattice).
- **`Complex.md`**: Complex numbers — `Complex` class with `re`/`im : Real`, `ComplexField` (`Field`) with standard multiplication and `inv-char`.

#### Finite Types and Modular Arithmetic

- **`Fin.md`** / **`Fin/`**: Modular arithmetic on `Fin (suc n)` — `FinRing` (`CRing.Dec`), `FinEuclidean` (Euclidean ring), `FinField` (discrete field when prime), integer mod lemmas, division lemmas, base-digit decomposition. Subdirectory contains `Order.md` (`LinearOrder.Dec` for `Fin n`).

#### Number Theory

- **`Prime.md`**: Primality — characterizations (`nat_irr-isPrime`, `prime-div`, `prime-less`), boolean `isPrime` test with correctness proofs, and `prime-isDec`.
- **`Bool.md`**: Lattice/order instances for `Bool` — `BoolLattice` (`BoundedDistributiveLattice`) and `BoolPoset` (`Dec` linear order).

#### Analysis

- **`Exp.md`**: Exponential function — `exp : CoverMap A A` for `RealBanachAlgebra A` via convergent power series `∑ x^n / n!`.
