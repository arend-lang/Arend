### Algebra.Solver.Monoid

A reflection-based solver model for proving equalities in monoids and additive monoids.

This module provides a `SubstSolverModel` instance that decides equalities of monoid expressions by normalizing them to lists of variables (flat words). The `Term` syntax mirrors the monoid signature (variables, identity, multiplication), and normalization uses an accumulator-based traversal to flatten nested products into a single list, exploiting associativity and the unit laws. The `>>=` operation provides substitution (list monad bind) needed by the substitution-based solver framework, allowing variables to be replaced by sub-normal-forms. An additive variant is obtained by reusing the multiplicative model via `AddMonoid.toMonoid`.

#### Solver Models

- **`MonoidSolverModel`**: The main `SubstSolverModel M` instance for a `Monoid M`. Reifies monoid expressions into `Term`, normalizes to `List (Fin n)`, and interprets normal forms back into `M`.
- **`AddMonoidSolverModel`**: The corresponding solver model for an `AddMonoid`, obtained by transporting `MonoidSolverModel` through `AddMonoid.toMonoid`.

#### Term Syntax

- **`Term`**: Inductive type of monoid expressions over `n` variables, with constructors `var (Fin n)`, `:ide` (identity), and `:* (infixl 7)` (multiplication).

#### Normalization

- **`normalize-aux`**: Accumulator-passing flattening of a `Term n` into a `List (Fin n)`. Variables are consed onto the accumulator, the identity is dropped, and products recurse right-then-left so the resulting list reads left-to-right.

#### Normal Form Interpretation

- **`interpretNF`**: Interprets a list of variables as a left-associated product in `M`, with `nil` mapped to `M.ide` and a singleton mapped without a trailing identity (avoiding a redundant `* ide`).
- **`interpretNF_::`**: Cons lemma: `interpretNF env (x :: l) = env x * interpretNF env l`, used to bridge the singleton special case and the general case.
- **`interpretNF_++`**: Concatenation lemma: `interpretNF env (t ++ s) = interpretNF env t * interpretNF env s`, the key homomorphism property of normal-form interpretation.
- **`interpretNF-consistent-aux`**: Soundness of `normalize-aux`: `interpretNF env (normalize-aux t acc) = interpret env t * interpretNF env acc`. Drives the solver's correctness proof.

#### Substitution

- **`>>=`**: List monad bind, used as the substitution operation for the `SubstSolverModel`. Concatenates the results of applying `k` to each element of `l`.
- **`>>=-consistent`**: States that interpreting a substituted normal form equals interpreting the outer list under the environment that interprets each substituted sub-list — the homomorphism law making substitution compatible with interpretation.
