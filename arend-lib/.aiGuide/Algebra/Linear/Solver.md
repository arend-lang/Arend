### Algebra.Linear.Solver

Reflective solver for linear (in)equality problems over linearly ordered semirings and rings.

This module extends the ring solver framework (`AlgData`) to handle ordered structures by encoding hypotheses as a list of equations tagged with a comparison operator (`<`, `<=`, `=`). A goal is discharged by exhibiting a *certificate* — an array of natural-number coefficients — whose nonnegative linear combination of the hypotheses contradicts a strict inequality. The construction reduces `<=`, `=`, and `<` goals to refuting an augmented contradiction problem, and the strictness witness comes either from a hypothesis tagged `Less` with positive coefficient or from a positive `f` slack in the certificate. The solver is parameterized over the algebraic setting (semiring, ring, ℚ-algebra) via class extensions.

#### Operation Tags

- **`Operation`**: Enumeration of comparison kinds (`Less`, `LessOrEquals`, `Equals`) used to label equations.
- **`isLess`**: Boolean test for the `Less` tag, with `correct` lemma extracting `o = Less` from `isLess o = true`.

#### Core Class: LinearData

- **`LinearData`**: Reflective solver base class extending `AlgData` with `R : LinearlyOrderedSemiring`. Carries the machinery for problems, certificates, and solving lemmas.
- **`LinearSemiringData`**: Specialization combining `LinearData` with `SemiringData`.
- **`LinearRingData`**: Specialization for ordered rings, extending `LinearData` and `RingData` with `R : OrderedRing`.
- **`LinearRatData`**: Specialization for rational arithmetic over an ordered ring.
- **`LinearRatAlgebraData`**: Specialization for ordered ℚ-algebras, extending `RatAlgebraData` and `LinearData`.

#### Equations and Problems

- **`Equation`**: A triple `(RingTerm C V, Operation, RingTerm C V)` representing one (in)equality.
- **`interpretEq`**: Interprets an `Equation` as a proposition (`<`, `<=`, or `=` between the interpreted ring terms).
- **`interpretEq_<=`**: Lemma that any holding `interpretEq e` implies the non-strict bound `interpret e.1 <= interpret e.3`.
- **`Problem`**: A list of equations, i.e. `Array Equation`, representing a hypothesis set.
- **`isConst`**: Boolean test recognizing closed `RingTerm`s (no variables) by structural recursion.

#### Certificates

- **`Cert`**: `Array Nat n`, a vector of natural-number multipliers indexed by problem size.
- **`certSum`**: Interprets a certificate as `∑ⱼ natCoef (c j) * interpret (l j)`.
- **`cert-toTerm`**: Builds a `RingTerm` representing the linear combination `∑ coef (natCoef kⱼ) :* tⱼ`.
- **`interpretCert`**: Normalized interpretation of a certificate term via `interpretRingNF ∘ normalize`.
- **`cert-toRingTerm-correct`**: Shows `AlgData.interpret (cert-toTerm l c) = certSum l c`.
- **`interpretCert_certSum`**: Equates the normalized form `interpretCert l c` with `certSum l c`.

#### Strictness Detection

- **`isSuc`**: Boolean test for positive naturals, with `correct` lemma yielding `0 < n` from `isSuc n = true`.
- **`hasNegative`**: Boolean predicate detecting whether any equation tagged `Less` has a positive coefficient in the certificate (i.e. provides strict slack).
- **`hasNegative-correct`**: Extracts a witness `(j, 0 < c j, (p j).2 = Less)` from `hasNegative p c = true`.

#### Correct Certificates

- **`CorrectCert`**: A certificate `c`, slack `f : Nat`, equality between the LHS- and RHS-combinations (with an extra `:ide` weighted by `f`), and a proof that strictness is achieved either via `hasNegative` or `isSuc f`.
- **`certToLeq`**: From a `CorrectCert`, derives the non-strict bound `certSum (map __.3 p) c.1 <= certSum (map __.1 p) c.1`.
- **`certToLess`**: Strengthens to a strict bound when the slack `f` is positive (`isSuc c.2 = true`).

#### Goal-Solving Lemmas

- **`solveContrProblem`**: Refutes a problem: given a `CorrectCert` and proofs of all `interpretEq (p j)`, derives `Empty`. The contradiction backbone of the solver.
  - **`aux_<=`**: Sums hypotheses interpreted as `<=` inequalities into `certSum (map __.1 p) c <= certSum (map __.3 p) c`.
  - **`aux`**: Strengthens `aux_<=` to a strict inequality given a `Less`-tagged hypothesis with positive coefficient.
- **`solve<=Problem`**: Proves `interpret t1 <= interpret t2` by reducing to a contradiction problem `(t2, Less, t1) :: p`.
- **`solve=Problem`**: Proves `interpret t1 = interpret t2` via two contradiction problems (each strict direction).
- **`solve<Problem`**: Proves `interpret t1 < interpret t2` by reducing to the contradiction problem `(t2, LessOrEquals, t1) :: p`.
