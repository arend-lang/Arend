### Topology.StoneCStarAlgebra

C*-algebras over the reals, axiomatized via norm/order compatibility conditions, with Stone-style square-sum and square-norm laws.

#### Ordered C*-Algebra

- **`OrderedC*Algebra`**: Class extending `QAlgebra`, `PosetQModule`, and `CRing`. An ordered commutative algebra in which every element is bounded by a natural number, squares are non-negative, and the unit ball is characterized by squares.
  - **`c*-archimedean`**: For every `a`, there exists `B : Nat` with `a <= natCoef B`.
  - **`c*-square-positive`**: `0 <= a * a`.
  - **`c*-<=_*-square`**: If `a <= 1` and `-a <= 1`, then `a * a <= 1`.
  - **`c*-<=-square`**: If `a * a <= 1`, then `a <= 1`.

#### Stone C*-(Pseudo)Algebra

- **`StoneC*PseudoAlgebra`**: Class extending `RealBanachPseudoAlgebra` and `CRing`. A real Banach pseudo-algebra satisfying the C*-style norm inequalities for sums of squares and norm-square submultiplicativity.
  - **`c*-sum`**: `norm (a * a) <= norm (a * a + b * b)`.
  - **`c*-square`**: `norm a * norm a <= norm (a * a)`.
- **`StoneC*Algebra`**: Class extending `StoneC*PseudoAlgebra`, `RealBanachAlgebra`, `OrderedC*Algebra`, and `PosetRing`. The full notion of a Stone C*-algebra: a real Banach commutative algebra whose order is given by `IsSquare (y - x)`, with all order-theoretic axioms derived from the norm/square conditions.

#### Order via Squares

- **`StoneC*Algebra.<=`**: The defining order on a ring `R`: `x <= y` iff `y - x` is a square (`R.IsSquare (y - x)`).

#### Auxiliary Square Lemmas

- **`square-lem-aux`**: In a `RealBanachAlgebra` satisfying `c*-sum`, if both `x` and `1 - x` are squares, then `norm (1 - x) <= 1`.
- **`square-sum1`**: Squares are closed under addition when both summands are bounded in norm by `1` (using `c*-sum`).
- **`square-sum`**: General closure of squares under addition: `IsSquare x` and `IsSquare y` imply `IsSquare (x + y)`. Used to prove transitivity and additivity of `<=`.
- **`norm-char-aux`**: Norm characterization helper: if `x <= 1` and `-x <= 1` in the square-order, then `norm x <= 1`. Combines `c*-sum` and `c*-square` to relate the order to the norm.

#### Canonical Instance

- **`RealStoneC*Algebra`**: Instance of `StoneC*Algebra` on `Real`. The real numbers form a Stone C*-algebra with the usual norm, field structure, and square-based order.
