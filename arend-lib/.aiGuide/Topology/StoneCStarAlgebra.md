### Topology.StoneCStarAlgebra

Commutative real C*-algebras (Stone C*-algebras) characterized by order, norm, and squares.

This module formalizes commutative C*-algebras over the reals via two complementary presentations: an order-theoretic one (`OrderedC*Algebra`) built on a partial order with archimedean and square-positivity axioms, and a norm-theoretic one (`StoneC*Algebra`) built on a Banach algebra structure satisfying the C*-identity. The central result is that these presentations agree: every `OrderedC*Algebra` carries a canonical Banach norm via `toBanach`, and in a `StoneC*Algebra` the order `x <= y` defined by `y - x` being a square coincides with the order from which the norm was derived (`norm-char`). The `\where`-block lemmas (`square-sum`, `square-lem-aux`, `norm-char-aux`) are abstract Banach-algebra-level helpers that let the equivalence be proved without circularity. `Real` itself is the canonical instance.

#### Ordered C*-Algebra

- **`OrderedC*Algebra`**: Class extending `QAlgebra`, `PosetQModule`, and `CRing`. Axiomatizes a commutative real algebra with a compatible partial order via `c*-archimedean` (every element bounded by a natural), `c*-square-positive` (`0 <= a * a`), `c*-<=_*-square` (squares of contractions are contractions), and `c*-<=-square` (squares dominated by `1` give contractions).
- **`zro<=ide`**: `0 <= 1` derived from the axioms.
- **`<=-square`**: From `a * a <= q² *q 1` with `q > 0`, conclude `a <= q *q 1`.
- **`<=-square_negative`**: Same hypothesis yields `negative a <= q *q 1`.
- **`c*-<=_*`**: Product of two contractions is a contraction.
- **`toBanach`**: Constructs a `RealPreBanachAlgebra` from the order, defining the norm of `a` as the upper real with rationals `q` such that some `r < q` satisfies `a <= r *q 1` and `negative a <= r *q 1`. All Banach-algebra axioms (norm of zero, negation, additivity, scaling, multiplicativity, identity bound) are checked.
- **`toBanach_norm<=1`**: If `a <= 1` and `negative a <= 1`, then `toBanach.norm a <= 1`.
- **`toBanach_c*-sum`**: `norm (a*a) <= norm (a*a + b*b)` for the constructed norm.
- **`toBanach_c*-square`**: The C*-identity `norm a * norm a <= norm (a*a)` for the constructed norm.
- **`*q-upperBound`**: Every element is bounded above by some positive rational multiple of `1`.

#### Stone C*-Pseudo-Algebra

- **`StoneC*PseudoAlgebra`**: Class extending `RealBanachPseudoAlgebra` and `CRing`, axiomatized by the pure-norm form of the C*-identity: `c*-sum` (norm monotone under adding squares) and `c*-square` (`norm a * norm a <= norm (a*a)`).
- **`norm_*-char`**: Characterizes the norm as a join: `norm x = sup { norm (x * y) : norm y <= 1 }`.

#### Stone C*-Algebra

- **`StoneC*Algebra`**: Class extending `StoneC*PseudoAlgebra`, `RealBanachAlgebra`, `OrderedC*Algebra`, and `PosetRing`. The order is defined by `x <= y := IsSquare (y - x)`, and all `OrderedC*Algebra` axioms (reflexivity, transitivity, antisymmetry, additivity, archimedean, square-positivity, and the contraction-square equivalences) are derived from the Banach-algebra and C*-identity axioms.
- **`norm-c*-positive`**: If `norm (1 - x) <= 1` then `0 <= x` — the standard "near-identity is positive" lemma.
- **`norm_<=_*q`**: `norm x <= q` implies `x <= q *q 1`.
- **`norm-char`**: The defining norm agrees with the order-induced norm `toBanach.norm`.
- **`square-positive`**: `IsSquare x` is equivalent to `0 <= x`, justifying the order definition.
- **`square-lem`**: For `0 <= x <= 1`, `norm (1 - x) <= 1`.
- **`square_*q-lem`**: Scaled version: for `0 <= x <= q *q 1`, `norm (q *q 1 - x) <= q`.
- **`square-closed`**: The predicate `IsSquare` is topologically closed (a limit of squares is a square).

#### Where-block Helpers

- **`<=`** (in `\where`): The order definition `x <= y := IsSquare (y - x)`, parametric in any ring.
- **`square-lem-aux`**: Abstract version of `square-lem` for any `RealBanachAlgebra` satisfying `c*-sum`.
- **`square-sum1`**: Sum of two contractive squares is a square.
- **`square-sum`**: Sum of squares is a square (general case via rescaling).
- **`norm-char-aux`**: Under the C*-axioms, contractions in the order sense have norm `<= 1` — the key lemma matching the two norms.

#### Canonical Instance

- **`RealStoneC*Algebra`**: The real numbers form a `StoneC*Algebra`, with `CompleteExNormedAbGroup` from `RealValuedRing`, `CRing` from `RealField`, divisibility, the identity-norm bound, scaling, multiplicativity, and both C*-identity axioms verified.
