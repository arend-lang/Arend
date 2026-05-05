### Algebra.Solver.Monoid

A reflective solver for monoid equalities, providing a `SubstSolverModel` that normalizes monoid terms into flat lists of variables.

#### Solver Models

- **`MonoidSolverModel`**: Builds a `SubstSolverModel` for a `Monoid M`, using `Term` as the syntax of monoid expressions and `List (Fin n)` as the normal form (a flat sequence of variables). Wires up `normalize`, `interpret`, `interpretNF`, and substitution into the generic solver framework.
- **`AddMonoidSolverModel`**: Adapts `MonoidSolverModel` to an `AddMonoid` by converting it to its multiplicative form via `AddMonoid.toMonoid`.

#### Term Syntax

- **`Term`**: Inductive datatype of monoid expressions over `n` variables, with constructors `var (Fin n)`, `:ide` (the identity), and `:*` (multiplication, `\infixl 7`).

#### Normalization

- **`normalize-aux`**: Accumulator-based flattening of a `Term n` into a `List (Fin n)`; appends `var v` entries to `acc`, drops `:ide`, and recurses on products right-to-left so the resulting list represents the term as a left-to-right sequence of variables.

#### Interpretation

- **`interpretNF`**: Evaluates a list of variables in environment `env : V -> M` as a monoid product, using `M.ide` for `nil` and avoiding a trailing `* ide` for singleton lists.
- **`interpretNF_::`**: `interpretNF env (x :: l) = env x * interpretNF env l`, the cons-evaluation lemma (handles the singleton special case).
- **`interpretNF_++`**: `interpretNF env (t ++ s) = interpretNF env t * interpretNF env s`, showing list concatenation corresponds to monoid multiplication.
- **`interpretNF-consistent-aux`**: Soundness of `normalize-aux`: `interpretNF env (normalize-aux t acc) = interpret env t * interpretNF env acc`, the key lemma proving normalization preserves meaning.

#### Substitution

- **`>>=`**: List monadic bind (`\infixl 2`), substituting each element of `l : List U` with `k u : List V` and concatenating the results — used for substituting normal forms into normal forms.
- **`>>=-consistent`**: Compatibility of `>>=` with interpretation: `interpretNF env (l >>= k) = interpretNF (\lam u => interpretNF env (k u)) l`, justifying substitution at the normal-form level.
