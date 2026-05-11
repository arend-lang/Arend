### Algebra.Solver.Group

A reflective decision procedure for equalities in groups, instantiating the generic substitution-based solver framework with a group-specific term language and normal form.

This module implements `SubstSolverModel` for groups by representing terms as a small AST (variable, identity, inverse, multiplication) and normal forms as lists of signed variables `(Bool, Fin n)`, where the boolean tag indicates whether the variable appears or its inverse appears. Normalization flattens products and pushes inverses to the leaves via `inverseNF`, then `reduce` cancels adjacent inverse pairs. The monadic bind `>>=` lifts variable substitutions through signed-list normal forms, dualizing them with `inverseNF` whenever a negative occurrence is substituted, which is what makes the model compatible with the generic substitution-based solver.

#### Term Language

- **`Term`**: AST of group expressions over `Fin n` variables: `var`, `:ide`, `:inverse`, and `:*` (left-associative product).

#### Normalization

- **`inverseNF`**: Reverses a signed-variable list while flipping every sign, producing the normal form of the inverse of a product; uses an accumulator.
- **`normalize`**: Converts a `Term n` into a `List (\Sigma Bool (Fin n))`: variables become a single positive entry, identity becomes `nil`, inverses recurse through `inverseNF`, and products concatenate.
- **`reduce`**: Cancels adjacent entries with the same variable but opposite signs in a normal form, called once after normalization.

#### Interpretation

- **`interpretNF'`**: Evaluates a signed-variable list in a group, mapping `(true, v)` to `env v` and `(false, v)` to `inverse (env v)`, multiplying components.
- **`interpretNF'.simple`**: Variant that always emits a final `* G.ide`, simplifying inductive proofs.
- **`interpretNF'.=simple`**: Equates `interpretNF'` with its `simple` counterpart.

#### Algebraic Lemmas on `interpretNF'`

- **`interpretNF'_::`**: Cons unfolds as `if-then-else * tail`.
- **`interpretNF'_reduce`**: `reduce` preserves interpretation (with helper `simple_reduce`).
- **`interpretNF'_++`**: Concatenation of normal forms corresponds to group multiplication (with `_simple` helper).
- **`interpretNF'_inverseNF`**: `inverseNF l acc` interprets as `inverse (⟦l⟧) * ⟦acc⟧` (with `_simple` helper).
- **`interpretNF'-consistent`**: `interpretNF' env (normalize t) = interpret env t`, the soundness of normalization.

#### Substitution

- **`>>=`**: Monadic bind over signed-variable lists; substitutes each variable with its assigned normal form, applying `inverseNF` to negatively-signed occurrences, and concatenates the results.
- **`interpretNF'_>>=`**: Substitution commutes with interpretation: interpreting a bind equals interpreting the outer list under the environment that interprets each substituted normal form.

#### Solver Models

- **`GroupSolverModel`**: The `SubstSolverModel G` instance for a `Group G`, packaging the term language, normalization, signed-list NF, `nfVar` (positive singleton), and the `>>=` substitution with its consistency proofs.
- **`AddGroupSolverModel`**: The `SubstSolverModel` instance for an `AddGroup A`, obtained by reusing `GroupSolverModel` on `AddGroup.toGroup A`.
