### Algebra.Solver

Abstract interface for normalization-based solvers that prove equalities by reducing terms to a normal form.

#### Solver Model

- **`SolverModel`**: Class capturing the structure of a normalization-based solver: a carrier set `M`, syntactic terms `Term n` over `n` variables, normal forms `NF n`, a `normalize` function, interpretation functions `interpret` (for terms) and `interpretNF` (for normal forms) under an environment `env : Fin n -> M`, and a consistency proof `interpretNF-consistent` ensuring `interpretNF env (normalize t) = interpret env t`.

#### Substitution-Based Solver

- **`SubstSolverModel`**: Extends `SolverModel` with substitution structure on normal forms. Provides `nfVar` (variable embedding `Fin n -> NF n`), a monadic bind `>>=` for substituting normal forms into normal forms, and `>>=-consistent` showing that interpretation commutes with substitution: `interpretNF env (nf >>= k) = interpretNF (\lam v => interpretNF env (k v)) nf`.
