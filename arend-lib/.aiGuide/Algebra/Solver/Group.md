### Algebra.Solver.Group

A normalization-based solver for equations in groups (and additive groups), reducing group expressions to reduced word lists over signed variables.

#### Solver Models

- **`GroupSolverModel`**: Constructs a `SubstSolverModel` for any `Group G`, supplying term syntax, normalization to signed-variable lists, interpretation, and consistency proofs needed by the generic solver framework.
- **`AddGroupSolverModel`**: Constructs a `SubstSolverModel` for any `AddGroup A` by reusing `GroupSolverModel` on its multiplicative form `AddGroup.toGroup A`.

#### Term Syntax

- **`Term`**: Inductive type of group terms over `n` variables with constructors `var (Fin n)`, `:ide` (identity), `:inverse` (group inverse), and `:*` (multiplication, infixl 7).

#### Normal Forms and Reduction

- **`normalize`**: Flattens a `Term n` into its normal form `List (\Sigma Bool (Fin n))`, where each entry pairs a sign bit (true = variable, false = its inverse) with a variable index.
- **`inverseNF`**: Computes the formal inverse of a normal form by reversing the list and flipping every sign, accumulating into `acc`.
- **`reduce`**: Cancels adjacent inverse pairs (`(b, v) :: (not b, v) :: ...`) in a normal form list to obtain a freely reduced word.

#### Interpretation

- **`interpretNF'`**: Evaluates a normal form `List (\Sigma Bool V)` in a group via an environment `env : V -> G`, multiplying `env x.2` or `inverse (env x.2)` according to the sign.
- **`interpretNF'.simple`**: A uniform variant of `interpretNF'` that always appends a trailing `G.ide`, used as a convenient form for inductive proofs.
- **`interpretNF'.=simple`**: Equates `interpretNF'` with its `simple` counterpart.

#### Substitution Monad

- **`>>=`**: Bind operation on normal forms (infixl 2): substitutes each signed variable in `List (\Sigma Bool U)` by a normal form from `k : U -> List (\Sigma Bool V)`, applying `inverseNF` for negative entries.

#### Consistency Lemmas

- **`interpretNF'_::`**: Cons step: `interpretNF' env (x :: l) = if x.1 (env x.2) (inverse (env x.2)) * interpretNF' env l`.
- **`interpretNF'_reduce`**: `reduce` preserves interpretation: `interpretNF' env (reduce l) = interpretNF' env l`.
- **`interpretNF'_reduce.simple_reduce`**: Same property stated for `interpretNF'.simple`.
- **`interpretNF'_++`**: Interpretation distributes over list concatenation: `interpretNF' env (l ++ l') = interpretNF' env l * interpretNF' env l'`.
- **`interpretNF'_++._simple`**: Concatenation distributivity for the `simple` form.
- **`interpretNF'_inverseNF`**: `interpretNF' env (inverseNF l acc) = inverse (interpretNF' env l) * interpretNF' env acc`.
- **`interpretNF'_inverseNF._simple`**: Same identity for the `simple` form.
- **`interpretNF'-consistent`**: Normalization preserves meaning: `interpretNF' env (normalize t) = interpret env t`.
- **`interpretNF'_>>=`**: Substitution compatibility: interpreting `l >>= k` equals interpreting `l` in the environment that maps each variable to the interpretation of `k v`.
