### Algebra.Solver

Abstract framework for term-rewriting solvers that decide equalities in algebraic structures by normalizing syntactic terms.

This module provides the categorical scaffolding shared by all algebraic solvers in the library (groups, rings, lattices, linear arithmetic, etc.). The core idea is the `SolverModel` class: a set `M` is equipped with a syntax of `Term`s, a notion of `NF` (normal forms), an interpretation into `M`, and a `normalize` function whose consistency with interpretation lets one prove equalities in `M` by computing on normal forms. The `SubstSolverModel` extension adds substitution structure on normal forms, making `NF` a monad indexed by variable count and allowing pattern-based axiom application — essential for solvers that work modulo a set of equational axioms.

#### Core Solver Framework

- **`SolverModel`**: Class over a carrier set `M` packaging the data needed for a normalization-based decision procedure. Fields: `Term n` (terms with `n` free variables), `NF n` (normal forms), `normalize : Term n -> NF n`, `interpret`/`interpretNF` (evaluation in `M` given an environment `Fin n -> M`), and `interpretNF-consistent` (normalization preserves meaning).
- **`SolverModel.terms-equality`**: The main user-facing lemma — to prove `interpret env t = interpret env s`, it suffices to show their normal forms have equal interpretations. This is the primary entry point used by `Meta`-driven solver tactics.
- **`SolverModel.terms-equality-conv`**: Converse direction — equality of interpretations implies equality of normalized interpretations. Useful when chaining solver invocations or reflecting back into normal-form land.

#### Substitution-Based Solvers

- **`SubstSolverModel`**: Extends `SolverModel` with a Kleisli-style monadic structure on `NF`, allowing axioms to be applied at arbitrary positions inside a normal form via substitution.
- **`SubstSolverModel.nfVar`**: Variable embedding `Fin n -> NF n` — the unit of the substitution monad.
- **`SubstSolverModel.>>=`**: Substitution / Kleisli bind `NF m -> (Fin m -> NF n) -> NF n`, replacing each variable in a normal form by another normal form.
- **`SubstSolverModel.>>=-consistent`**: Coherence: substituting then interpreting equals interpreting under the substituted environment. Makes `NF` a lawful indexed monad with respect to interpretation.
- **`SubstSolverModel.apply-axiom`**: Given a proven term equality `interpret env t = interpret env s` and a `pattern : NF (suc env.len)` with one hole, derives equality of `pattern` instantiated with `normalize t` versus `normalize s`. This is the key mechanism for plugging user-supplied equations into the normalization machinery.
