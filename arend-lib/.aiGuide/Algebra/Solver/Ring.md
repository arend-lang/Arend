### Algebra.Solver.Ring

Reflective solver model for proving equalities in arbitrary (commutative) rings.

This module instantiates the generic `SolverModel` framework over a `Ring R` by representing ring expressions as a syntactic `Term` AST with integer coefficients, normalizing them to sums of signed coefficient-times-monomial pairs, and reducing equality goals `t = s` to checking that the normal form of `t - s` is zero. It reuses the semiring solver's normalization machinery (`SemiringSolverModel.NF`, `multiply`, `collapse`, `remove0`, `Sort.RedBlack.sort`) and lifts it to rings by handling negation via `:negative` and integer coefficients via `intCoef : Int -> R`. The model also supports applying user-supplied equational axioms during normalization (`apply-axioms`), enabling solving in quotient rings or settings with extra relations.

#### Solver Model

- **`RingSolverModel`**: The `SolverModel R` instance for a `Ring R`, using `Term Int` syntax, `NF Int` normal forms, and `ringInterpretNF` as the semantic interpretation. Bridges syntactic ring expressions to the generic solver framework.

#### Term Syntax

- **`Term`**: AST of ring expressions over coefficient set `C` with `n` free variables. Constructors:
  - **`var`**: A free variable indexed by `Fin n`.
  - **`coef`**: An element of the coefficient set `C` (typically `Int`).
  - **`:zro`**, **`:ide`**: Ring zero and one.
  - **`:negative`**: Unary negation.
  - **`:+`**, **`:*`**: Binary addition and multiplication (left-associative).

#### Normalization

- **`normalize`**: Reduces a `Term C n` (over a decidable ring `C`) to a normal form `NF C n` — a list of `(monomial, coefficient)` pairs. Recursively flattens sums, multiplies and collapses products, and pushes negation through coefficients.

#### Interpretation

- **`interpret`**: Evaluates a `Term Int env.len` in a ring `R` given a variable environment `env : Array R`, using `R.intCoef` for integer coefficients.
- **`intCoef`**: Custom integer-to-ring coercion that special-cases `neg 1` as `negative ide` and otherwise delegates to `R.natCoef`. Used internally for efficiency / to match the normal-form interpretation.
- **`intCoef-correct`**: Proves `R.intCoef x = intCoef x`, reconciling the canonical and custom coercions.
- **`intMap'`**: Promotes `intCoef` to a `RingHom IntRing R`.

#### Normal-Form Interpretation

- **`RingData`**: Bundles `env`, `intMap'`, and ring-commutativity data into the `Data` record consumed by the underlying semiring solver machinery.
- **`signMulCoef`**: Multiplies `r : R` by the sign of an integer `c` (identity if `c >= 0`, negation if `c < 0`).
- **`ringMulCoef`**: Interprets a single normal-form term `(c, l)` as `signMulCoef c · mulCoef |c| l`, splitting the integer coefficient into sign and magnitude.
- **`ringInterpretNF'`**: Sums `ringMulCoef`-interpretations over a list of normal-form pairs.
- **`ringInterpretNF`**: Top-level NF interpretation: sorts the list (via `Sort.RedBlack.sort`), collapses like terms, removes zero-coefficient entries, then interprets via `ringInterpretNF'`.

#### Correctness Lemmas

- **`ringMulCoef-correct`**: `ringMulCoef c l` agrees with the semiring `mulCoef`.
- **`ringInterpretNF-correct`**: `ringInterpretNF'` agrees with the semiring `interpretNF'`.
- **`interpretNF_negative`**: Mapping coefficient negation over a normal form negates its interpretation.
- **`normalize-consistent`**: The interpretation of `normalize t` equals `interpret env t` — soundness of normalization.
- **`terms-equality`**: If `ringInterpretNF env (normalize (t :+ :negative s)) = 0`, then `interpret env t = interpret env s`. The main solving step: reduces equality to a zero-check on the difference's normal form.
- **`terms-equality-conv`**: Converse of `terms-equality`.

#### Axiom Application

- **`apply-axioms`**: Given a list of axioms `(NF, t, s, t = s)` and an additional normal form `add`, proves that interpreting the combined NF (each axiom's NF multiplied by `normalize (t :+ :negative s)`, summed with `add`) equals `ringInterpretNF' add`. Used to discharge equational hypotheses when solving in rings with extra relations.
