### Algebra.Solver.BooleanRing

A `SolverModel` instance for `BooleanRing`, normalizing boolean ring expressions into sums of monomials (with duplicate cancellation reflecting `x + x = 0`) to decide equality.

#### Main Instance

- **`BooleanRingSolverModel`**: `SolverModel` instance for any `BooleanRing B`, wiring up `Term`, `NF`, `normalize`, `interpret`, and the consistency proof.

#### Syntax

- **`Term`**: AST of boolean ring expressions over `n` variables, with constructors `var`, `:zro`, `:negative`, `:+`, `:*`.
- **`NF`**: Normal form — `List (Array Bool n)`, representing a sum of monomials where each `Array Bool n` marks which of the `n` variables occur in that monomial.

#### Normalization

- **`normalize`**: Converts a `Term n` to `NF n`. Variables become singleton monomials via `singleAt`, sums concatenate, products call `multiply`, and `:negative` is identity (since `-x = x` in a boolean ring).
- **`multiply`**: Multiplies two normal forms; returns `lnil` if the right operand is empty.
- **`multiply'`**: Tail-recursive helper for `multiply`, accumulating the result; combines monomials by `or`-ing their bool arrays componentwise (since `x*x = x`).
- **`collapse`**: Cancels adjacent duplicate monomials in a sorted `NF` (encoding `m + m = 0`); pairs of equal monomials are dropped, distinct ones are kept.

#### Interpretation

- **`interpret`**: `(env : Array B) -> Term env.len -> B`. Evaluates a `Term` in the boolean ring under an environment.
- **`toArray`**: Selects elements of `env` according to a bool array, producing the list of variables present in a monomial.
- **`sBigProd`**: Product of a non-empty list (returns `B.zro` on empty).
- **`interpretMonomial`**: Interprets a single monomial as `sBigProd (toArray l env)`.
- **`interpretNF'`**: Interprets an `NF` as the sum of its monomials' interpretations.
- **`interpretNF`**: Public interpretation — sorts and collapses the `NF` first, then calls `interpretNF'`.

#### Auxiliary Predicates

- **`NonEmpty`**: A bool array contains at least one `true` index.
- **`AllNonEmpty`**: Every monomial in an `NF` is `NonEmpty`.
- **`nonEmpty-dec`**: Decidability of `NonEmpty`.
- **`BoolOpPoset`**: `LinearOrder.Dec` instance on `Bool` (opposite order), used for sorting.

#### Correctness Lemmas

- **`interpretNF'-consistent`**: `interpretNF' env (normalize t) = interpret env t` — normalization preserves semantics.
- **`interpretNF=interpretNF'`**: Sorting and collapsing don't change the interpretation.
- **`collapse-consistent`**, **`perm-consistent`**, **`sort-consistent`**: Each phase of `interpretNF` preserves the interpretation.
- **`interpretNF_++`**: Interpretation distributes over list concatenation.
- **`interpretNF_multiply`**, **`interpretNF_multiply'`**, **`interpretNF_map`**: Multiplication of NFs corresponds to ring multiplication (under `AllNonEmpty`).
- **`interpretMonomial_or`**: Componentwise `or` of bool arrays corresponds to product of monomials.
- **`toArray_or`**, **`sBigProd_Big`**, **`toArray<=env`**, **`toArray/=nil`**, **`toArray_replicate`**, **`toArray_singleAt`**: Properties of `toArray`/`sBigProd` relating products to `BigJoin`/`Big ∧`.

#### Non-Emptiness Preservation

- **`normalize-nonEmpty`**: `AllNonEmpty (normalize t)`.
- **`multiply-nonEmpty`**, **`multiply'-nonEmpty`**, **`or-nonEmpty`**, **`all-++`**: `AllNonEmpty` is preserved by the normalization operations.

#### Solver Entry Points

- **`terms-equality`**: From `interpretNF env (normalize (t :+ s)) = B.zro` derive `interpret env t = interpret env s`.
- **`terms-equality-conv`**: Converse direction.
- **`apply-axioms`**: Applies a list of provided axioms (witnessed equalities) by multiplying each by a coefficient `NF` and summing into an accumulator `add`, leaving `interpretNF env add` unchanged.
- **`interpretNF_Big_++`**, **`interpretNF_map_zro`**, **`interpretNF_multiply_zro`**, **`interpretNF_multiply'_zro`**: Supporting lemmas for `apply-axioms`, propagating zero through multiplications.
