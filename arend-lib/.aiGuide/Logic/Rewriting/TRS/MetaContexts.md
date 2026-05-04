### Logic.Rewriting.TRS.MetaContexts

Meta-context combinators for term rewriting.

- **`ModularMetaContext`**: Combines an array of `MetaContext`s into one via disjoint union of meta-variables.
  - **`upgrade-metavariables`**: Embeds a term over one component into the combined meta-context.
- **`PointedModularMetaContext`**: Extends `ModularMetaContext` with a distinguished "point" meta-variable of a given sort and context.
- **`EmptyMetaContext`**: Meta-context with no meta-variables.
