### Algebra.Solver.Semiring

Reflective solver for semiring equalities, normalizing terms into sorted sums of monomials with coefficients.

This module implements the semiring instance of the generic `SolverModel` framework. Terms over a coefficient semiring `C` and a finite set of variables are normalized to a list of monomials — pairs of a variable list (a monoid word) and a coefficient — which are then multiplied out, sorted lexicographically, and collapsed by combining like terms and dropping zero coefficients. A `Data` class records the embedding `alg : SemiringHom C R` of coefficients into the target semiring `R` together with a commutation hypothesis between coefficients and variables, providing all consistency lemmas needed for the solver. A specialization to `NatSemiring` coefficients (`SemiringData`) gives the off-the-shelf solver via the canonical `natCoef` map, used by `apply-axiom` to rewrite under products and sums.

#### Term and Normal Form Datatypes

- **`Term`**: Inductive type of semiring expressions over variables `Fin n` and coefficients in `C`, with constructors `var`, `coef`, `:zro`, `:ide`, `:+`, `:*`.
- **`NF`**: Normal form as a list of monomials `\Sigma (List (Fin n)) C` — each monomial is a variable word paired with a coefficient.

#### Normalization Pipeline

- **`normalize`**: Recursively converts a `Term` to `NF`: variables and `:ide` become unit-coefficient monomials, `:+` concatenates, `:*` multiplies and post-processes.
- **`multiply`**: Multiplies two normal forms; trivial on empty input, otherwise dispatches to `multiply'`.
- **`multiply'`**: Tail-recursive multiplication accumulating cross products `(a.1 ++ b.1, a.2 * b.2)` into `acc`.
- **`collapse1`**: Helper that merges a pending monomial `(m, c)` into a list, summing coefficients of monomials with matching variable word.
- **`collapse`**: Walks the list combining adjacent equal-keyed monomials via `collapse1` (intended after sorting).
- **`remove0`**: Drops monomials whose coefficient is decidably `0`.

#### Solver Model

- **`SemiringSolverModel`**: Instance of `SolverModel R` for any `Semiring R`, using `Term Nat`/`NF Nat`, the pipeline above, and `SemiringData env` for interpretation.

#### Data Class (Generic Coefficients)

- **`Data`**: Class parameterized by a target semiring `R`, a decidable linearly ordered coefficient semiring `C`, a homomorphism `alg : SemiringHom C R`, and a variable environment `env : Array R`. Requires `alg-comm`: coefficients commute with environment values in `R`.
- **`Data.NF`**: Shorthand for `NF C env.len`.
- **`Data.interpret`**: Evaluates a `Term C env.len` in `R` using `alg` and `env`.
- **`Data.mulCoef`**: Computes `alg c * MonoidSolverModel.interpretNF env l`, with a special case dropping `alg 1` when `c = 1`.
- **`Data.interpretNF'`**: Direct interpretation of a normal form as a sum of `mulCoef`-evaluated monomials.
- **`Data.interpretNF`**: Public interpretation; sorts via `RedBlack.sort`, collapses, removes zeros, then calls `interpretNF'`.

#### Consistency Lemmas

- **`mulCoef-consistent`**: `mulCoef c l = alg c * MonoidSolverModel.interpretNF env l`.
- **`interpretNF_::`**: Cons-step law for `interpretNF'`.
- **`remove0-consistent`**: `remove0` preserves the interpretation.
- **`collapse1-consistent`** / **`collapse-consistent`**: Collapsing like monomials preserves the interpretation.
- **`perm-consistent`**: `interpretNF'` is invariant under permutations (uses `Perm`).
- **`sort-consistent`**: `RedBlack.sort` preserves the interpretation.
- **`interpretNF_++`**: `interpretNF'` is a homomorphism on list concatenation.
- **`interpretNF_Big_++`**: Folded-concat version using `R.BigSum`.
- **`interpretNF_map`** / **`interpretNF_map-left`**: Effect of pre/post-multiplying every monomial in a list by fixed words and a coefficient.
- **`interpretNF_multiply'`** / **`interpretNF_multiply`**: Multiplication on `NF` matches semiring multiplication of interpretations.
- **`normalize-consistent`**: `interpretNF' (normalize t) = interpret t`.
- **`interpretNF-correct`**: Sorted/collapsed/zeroed interpretation agrees with `interpretNF'`.
- **`interpretNF-consistent`**: Composite correctness `interpretNF (normalize t) = interpret t`, the field used by `SolverModel`.
- **`apply-axiom2`**: Substitution rule rewriting `interpret t = interpret s` into an equality of normalized expressions multiplied by `left`/`right` contexts and added to `add`.

#### Natural-Number Specialization

- **`natCoef`**: Map `Nat -> R` with optimized cases `0 ↦ 0`, `1 ↦ 1`, otherwise `R.natCoef`.
- **`natCoef-correct`**: `R.natCoef n = natCoef n`.
- **`natMap'`**: Promotes `natCoef` to a `SemiringHom NatSemiring R`.
- **`SemiringData`**: Concrete `Data` instance with `C = NatSemiring` and `alg = natMap'`, supplying `alg-comm` from commutativity of `natCoef`-images.
- **`apply-axiom`**: Top-level axiom-application lemma over `Term Nat`, used by the solver tactic to rewrite goals using a hypothesis `interpret t = interpret s`.
