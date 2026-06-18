### Logic.Rewriting.TRS.MetaContexts

Constructions for combining and specializing metavariable contexts in higher-order term rewriting systems.

This module provides ways to assemble `MetaContext`s from simpler pieces — taking a finite family of metacontexts and merging them into one (`ModularMetaContext`), distinguishing a special "hole" metavariable (`PointedModularMetaContext`), or building a context with a single metavariable at a chosen sort (`SingularMetaContext`). These constructions support compositional reasoning about terms with metavariables, where a rewrite rule's pattern, the surrounding context, and a substitution may each carry their own metavariables that must be combined coherently. The `upgrade-metavariables` helpers reindex terms from a component metacontext into the merged one, and the modularity lemma ensures that applying a substitution commutes with this upgrade.

#### Combined Metacontexts

- **`ModularMetaContext`**: Merges a finite family `Fin n -> MetaContext Sort` into one metacontext whose metanames are pairs `(i, m)` of an index and a metaname from the `i`-th component, with domains inherited componentwise.
- **`ModularMetaContext.upgrade-metavariables`**: Lifts a `Term` over the `index`-th component metacontext into a term over the merged `ModularMetaContext`, by retagging each metavariable with its component index.
- **`PointedModularMetaContext`**: Extends `ModularMetaContext` with a distinguished metavariable at a specified `point : Sort` having context `context-for-point`; metanames are `Or` of the equation `s = point` and the modular metanames.
- **`PointedModularMetaContext.upgrade-metavariables`**: Lifts a term from a component metacontext (indexed within `pointed-context`) into the pointed merged metacontext, embedding ordinary metavariables on the `inr` side.

#### Atomic Metacontexts

- **`SingularMetaContext`**: A metacontext with a single metavariable at sort `point` over the given `context`; metanames are proofs `s = point`.
- **`EmptyMetaContext`**: The metacontext with no metavariables (`metaname = Empty`); used to express closed/pure terms.
- **`PureTerm`**: A `Term` over `EmptyMetaContext`, i.e., a term containing no metavariables.

#### Modularity Lemma

- **`apply-modularity`**: States that applying a metasubstitution to a weakened component term equals applying the merged substitution `\lam m => substitutions m.1 m.2` to the upgraded term — i.e., per-component substitution and modular substitution agree, so reasoning can move freely between the component view and the merged view.
