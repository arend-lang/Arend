### Algebra.Solver.CGroup

A reflective solver for equalities in commutative groups and abelian groups, using integer-coefficient vectors as normal forms.

#### Solver Models

- **`CGroupSolverModel`**: Builds a `SolverModel` for any commutative group `G`, with `Term` as the syntactic term type, normal forms as `Array Int n` (integer exponent vectors indexed by variables), and `normalize`/`interpret`/`interpretNF` glued by `interpretNF-consistent`.
- **`AbGroupSolverModel`**: Specializes `CGroupSolverModel` to an abelian group `A` by routing through `AbGroup.toCGroup`.

#### Normal-Form Interpretation

- **`toArray`**: Expands an integer coefficient vector `cs : Array Int` against an environment `env : Array G` into a flat list of group elements (positive coefficients duplicate, negative coefficients duplicate the inverse).
- **`sBigProd`**: Right-associated big product over a list of group elements, returning `ide` on the empty list and avoiding a trailing `* ide`.
- **`sBigProd_::`**: `sBigProd (a :: l) = a * sBigProd l`, the cons rewrite for `sBigProd`.
- **`interpretNF`**: Interprets a normal form `l : Array Int n` under `env : Fin n -> G` via `sBigProd ∘ toArray`.
- **`interpretNF'`**: Alternative interpretation as `BigProd (\j => ipow (env j) (l j))` — exponent-vector semantics using integer powers.
- **`interpretNF-correct`**: `sBigProd (toArray cs env) = interpretNF' cs env`, identifying the two interpretations.

#### Normalization

- **`normalize`**: Reduces a `Term n` to its `Array Int n` exponent vector — `var v` becomes the unit basis vector, `:ide` the zero vector, `:inverse` negates entrywise, and `:*` adds entrywise (using commutativity).
- **`interpretNF-consistent'`**: `interpretNF' (normalize t) env = interpret env t` — soundness of normalization for the `interpretNF'` semantics.
- **`interpretNF-consistent`**: Same soundness statement for `interpretNF`, the form required by `SolverModel`.

#### Equality Decision

- **`terms-equality`**: From `interpretNF env (normalize (t :* :inverse s)) = ide` derive `interpret env t = interpret env s` — the forward direction used by the solver to discharge group equalities.
- **`terms-equality-conv`**: The converse, turning a semantic equality back into a normal-form identity.

#### Axiom Application

- **`apply-axioms'`**: Given a list of hypotheses `(cᵢ, tᵢ, sᵢ, tᵢ = sᵢ)`, the integer combination `∑ᵢ cᵢ · (normalize tᵢ − normalize sᵢ)` interprets to `ide` under `interpretNF'`.
- **`apply-axioms'.interpretNF_+`**: `interpretNF'` distributes over entrywise addition as group multiplication.
- **`apply-axioms'.interpretNF_negative`**: `interpretNF'` of the negated vector is the inverse.
- **`apply-axioms'.interpretNF-coef`**: Scaling a coefficient vector by `c` corresponds to taking the integer `c`-th power.
- **`apply-axioms'.interpretNF_BigSum`**: `interpretNF'` of a column-wise integer sum equals the group big-product of the per-row interpretations.
- **`apply-axioms`**: User-facing variant that adds a `right`-hand vector and shows the combined normal form interprets equally with or without the axiom-derived combination — the workhorse used by `Algebra.Meta` to rewrite by hypotheses.

#### Abelian Group Specializations

- **`AbGroupSolverModel.terms-equality`**: `terms-equality` re-exposed for an `AbGroup` via the `toCGroup` view.
- **`AbGroupSolverModel.terms-equality-conv`**: Converse direction for the abelian case.
- **`AbGroupSolverModel.apply-axioms`**: `apply-axioms` re-exposed for an `AbGroup`.
