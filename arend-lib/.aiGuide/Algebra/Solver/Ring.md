### Algebra.Solver.Ring

Reflective ring solver: normalizes ring expressions to canonical polynomial form for deciding equalities in any `Ring`.

#### Solver Model

- **`RingSolverModel`**: A `SolverModel R` instance for any `Ring R`, packaging the term language, normalization procedure, and consistency lemma so the generic solver can decide ring equalities by reduction to normal form.

#### Term Language

- **`Term`**: Inductive type of ring expressions over a coefficient set `C` with `n` free variables. Constructors: `var` (variable), `coef` (coefficient embedding), `:zro`, `:ide`, `:negative`, `:+`, `:*`.

#### Normalization

- **`normalize`**: Reduces a `Term C n` over a decidable ring to a normal form `NF C n` (sorted, collapsed list of monomials with zero terms removed). Handles negation by negating coefficients and multiplication via `multiply`/`collapse`/`remove0`.
- **`interpret`**: Evaluates a `Term Int env.len` in `R` using an environment `env : Array R`, mapping integer coefficients via `R.intCoef`.

#### Integer Coefficient Embedding

- **`intCoef`**: Optimized embedding `Int -> R` that special-cases `neg 1` to `negative ide`, avoiding redundant work compared to `R.intCoef`.
- **`intCoef-correct`**: `R.intCoef x = intCoef x` — the optimized version agrees with the canonical embedding.
- **`intMap'`**: `RingHom IntRing R` built from `intCoef`, used as the coefficient ring homomorphism for the underlying semiring solver data.
- **`RingData`**: Instance of `Data R IntRing IntRing intMap' env` providing the commutativity proof `alg-comm` linking integer coefficient multiplication with ring multiplication.

#### Interpretation of Normal Forms

- **`signMulCoef`**: Multiplies `r : R` by the sign of an integer `c` (identity for non-negative, negation for negative).
- **`ringMulCoef`**: Computes the value of a monomial `(c, l)` in `R` by combining `signMulCoef` with `(RingData env).mulCoef (iabs c) l`.
- **`ringInterpretNF'`**: Sums the values of monomials in a normal form list using `ringMulCoef`.
- **`ringInterpretNF`**: Top-level interpreter: sorts, collapses, and removes zeros before applying `ringInterpretNF'`.

#### Correctness Lemmas

- **`ringMulCoef-correct`**: `ringMulCoef c l = (RingData env).mulCoef c l` — agreement between the ring solver's monomial evaluator and the underlying semiring data.
- **`ringInterpretNF-correct`**: `ringInterpretNF' l = (RingData env).interpretNF' l` — agreement between the two normal-form interpreters.
- **`interpretNF_negative`**: Interpreting a negated normal form equals the negation of its interpretation.
- **`normalize-consistent`**: `(RingData env).interpretNF' (normalize t) = interpret env t` — normalization preserves semantics.

#### Decision Procedures

- **`terms-equality`**: From `ringInterpretNF env (normalize (t :+ :negative s)) = 0`, derives `interpret env t = interpret env s`. Forward direction of the solver: a syntactic check on the difference's normal form proves semantic equality.
- **`terms-equality-conv`**: Converse — semantic equality of two terms implies their difference normalizes to zero.
- **`apply-axioms`**: Generalized solver step that applies a list of user-supplied equational axioms (each a triple of an `NF` multiplier and two terms with a proof of their equality) and reduces the combined normal form to a target `add`. Enables solving equalities modulo additional ring identities.
