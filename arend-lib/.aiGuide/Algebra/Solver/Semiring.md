### Algebra.Solver.Semiring

Reflective solver for semiring equalities, providing normalization of semiring expressions into sums of monomials with coefficients.

#### Solver Model

- **`SemiringSolverModel`**: The `SolverModel` instance for a `Semiring R`, packaging `Term`, `NF`, `normalize`, and the interpretation/consistency lemmas needed by the generic solver framework.

#### Term Syntax

- **`Term`**: Inductive type of semiring expressions over a coefficient set `C` with `n` variables. Constructors: `var` (variable from `Fin n`), `coef` (constant from `C`), `:zro`, `:ide`, `:+` (addition), `:*` (multiplication).
- **`NF`**: Normal form as `List (\Sigma (List (Fin n)) C)` — a sum of monomials, each a pair of a variable list (product) and a coefficient.

#### Normalization

- **`normalize`**: Reduces a `Term` to `NF` form by recursive rewriting: variables and `:ide` become singleton monomials, `:zro` becomes `nil`, addition concatenates, multiplication uses `multiply` followed by `collapse` and `remove0`.
- **`multiply`**: Multiplies two normal forms; dispatches to `multiply'` when the second list is non-empty.
- **`multiply'`**: Tail-recursive helper that distributes each monomial of `l1` over `l2`, concatenating variable lists and multiplying coefficients, accumulating into `acc`.
- **`collapse1`**: Merges adjacent monomials with equal variable lists by adding their coefficients, threaded through the rest of the list.
- **`collapse`**: Top-level coefficient combiner; folds `collapse1` across the list to consolidate like monomials.
- **`remove0`**: Drops monomials whose coefficient is decidably equal to `0`, requiring `DecSet C`.

#### Solver Data

- **`Data`**: Record bundling the target semiring `R`, coefficient semiring `C` with decidable linear order, a `SemiringHom alg : C -> R`, an environment `env : Array R` mapping variables to ring elements, and a commutativity hypothesis `alg-comm` stating that images of coefficients commute with arbitrary ring elements.
- **`natCoef`**: Maps `Nat` to `R` with special-cased `0` and `1` to use the semiring's own zero/one, falling back to `R.natCoef` otherwise.
- **`natCoef-correct`**: Shows `R.natCoef n = natCoef n`, ensuring the special-cased version agrees with the canonical embedding.
- **`natMap'`**: The `SemiringHom NatSemiring R` built from `natCoef`.
- **`SemiringData`**: Constructs a `Data` instance for the canonical case `C = NatSemiring`, using `natMap'` and proving `alg-comm` via `natComm` and `natCoef-correct`.

#### Soundness

- **`apply-axiom`**: Records that two terms with equal interpretations can be added as an axiom to extend the solver, taking the proof `p` together with auxiliary normal forms `left`, `right`, `add`.
