### Algebra.QModule

Abelian groups equipped with rational scalar multiplication, formalizing ℚ-modules and ℚ-algebras as uniquely divisible, torsion-free structures.

The module builds up the notion of a ℚ-module from two complementary axioms: divisibility (every element has an n-th part for n ≠ 0) and torsion-freeness (multiplication by a nonzero natural is injective). Together these make division by naturals unique, which lets `*q` be defined by picking the unique witness from `uniquelyDivisible` and scaling by an integer numerator. An auxiliary alternative representation `Rat.AltRat` (numerator/positive-denominator pairs) is used to prove the algebraic laws of `*q` modulo the equivalence relation defining `Rat`. The hierarchy then layers rings and algebras on top, with conversions to and from the standard `LModule`/`PseudoAlgebra`/`AAlgebra` interfaces over `RatField`.

#### Base Group Classes

- **`DivisibleGroup`**: Extends `AbGroup`. Every element `a` has, for each nonzero `n`, some `b` with `n *n b = a` (existence only).
- **`TorsionFreeGroup`**: Extends `AbGroup`. `n *n a = 0` implies `a = 0` for nonzero `n`.
  - **`noTorsion-div`**: Cancellation form: `n *n a = n *n b` implies `a = b`.

#### QModule

- **`QModule`**: Extends `DivisibleGroup` and `TorsionFreeGroup`. The combined axioms make division unique, yielding a ℚ-module structure.
- **`uniquelyDivisible`**: For nonzero `n`, the type `\Sigma (b : E) (n *n b = a)` is contractible — division by `n` is unique.

#### Rational Scalar Multiplication

- **`*q`** (infixl 7): Multiplication by a `Rat`, defined as `ratNom q *i (b)` where `b` is the unique division by `ratDenom q`.
- **`*q_*i`**: On integer scalars, `*q` agrees with integer multiplication `*i`.
- **`*q_*n`**: On natural scalars, `*q` agrees with `*n`.
- **`*q_*2`**: `2 *q a = a + a`.
- **`ide_*q`**: `1 *q a = a`.
- **`*q-assoc`**: `(q * r) *q a = q *q (r *q a)`.
- **`*q-cancel`**: Cancellation: `q *q a = q *q b` implies `a = b` for `q ≠ 0`.

#### Unique Division Helpers

- **`ud-cond`**: The chosen division witness satisfies `n *n (...).1 = a`.
- **`ud-unique`**: Any other witness coincides with the chosen one.
- **`ud-pi`**: The witness is invariant under propositionally equal denominators.

#### Alternative Rational Representation

- **`*aq`**: Scalar multiplication using `Rat.AltRat` (numerator, positive denominator) representation, defined by recursion on the quotient with the corresponding `~-equiv` coherence proof.
  - **`>_/=`**, **`>_iabs`**: Auxiliary lemmas relating `signum`, `iabs`, and positivity of integers.
  - **`*q_*aq`**: Bridge: `q *q a = rat_alt q *aq a`.
  - **`*aq-assoc`**, **`*aq-rdistr`**: Associativity and right-distributivity for the alternative form (used to derive the `*q` laws).

#### Bridge to LModule

- **`toRatModule`**: Packages the `QModule` as an `LModule` over `RatField` using `*q` as scalar multiplication.
- **`fromRatModule`**: Converts an `LModule RatField` back into a `QModule` by extracting divisibility and torsion-freeness from the rational scalar action.

#### Homomorphism Compatibility

- **`func-*q`**: Any `AddGroupHom` between `QModule`s automatically commutes with rational scaling: `f (q *q a) = q *q f a`.

#### QPseudoAlgebra

- **`QPseudoAlgebra`**: Extends `QModule` and `PseudoRing`. A pseudo-ring whose additive group is a ℚ-module, with rational scalars commuting with multiplication.
  - **`*q-comm-left`**: `r *q (a * b) = (r *q a) * b`.
  - **`*q-comm-right`**: `r *q (a * b) = a * (r *q b)`.
  - **`*q-square`**: A square scalar times a square element is a square: `IsSquare q -> IsSquare a -> IsSquare (q *q a)`.
  - **`toRatAlgebra`**: Packages as a `PseudoAlgebra RatField`.
- **`fromRatAlgebra`** (in `\where`): Converts a `PseudoAlgebra RatField` to a `QPseudoAlgebra`.

#### QAlgebra

- **`QAlgebra`**: Extends `QPseudoAlgebra` and `Ring`. A unital ℚ-algebra.
  - **`natCoef_*_*q`**: The natural-number coefficient `natCoef n` acts as `n *q -`.
  - **`toRatAlgebra`**: Packages as an `AAlgebra RatField`.
- **`fromRatAlgebra`** (in `\where`): Converts an `AAlgebra RatField` to a `QAlgebra`.

#### Canonical Instance

- **`RatQAlgebra`**: `Rat` itself as a `QAlgebra`, obtained from `RatField`'s self-algebra structure.
