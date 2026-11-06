### Algebra.Solver.Guarded

Guarded solver models: normalization that needs a hypothesis and is correct only in environments satisfying a guard.

`GuardedSolverModel` is a variant of `SolverModel` for the case where normalization depends on a hypothesis and is sound only if the environment satisfies a guard. For example, a field has no total inverse, so an inverse can be treated as an atom only together with a witness. Normal forms need not be canonical, so an explicit equivalence `NFEquiv` replaces equality of normal forms. It is a separate module from `Algebra.Solver` because the field names of the two classes coincide.

#### Guarded model

- **`GuardedSolverModel`**: a class over a set `M` with types `Term`, `NF` and `Hyp` indexed by the number of variables, a `Guard` on environments, `normalize` depending on a hypothesis, `interpret`, an equivalence `NFEquiv` on normal forms, and the soundness field `terms-equality` and completeness field `terms-equality-conv` relating `NFEquiv` of normal forms to equality of interpretations under the guard.
