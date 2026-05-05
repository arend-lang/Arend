### Logic.Rewriting.TRS.Linearity

Linear terms for term rewriting systems.

- **`GenericLinearTerm`**: Inductive type with `l-func`, `l-full-metavar`, `l-var` — terms where each meta-variable occurs at most once.
- **`Linear`** module: `convert-to-term` converts a linear term to a `Term` with `LinearMetaContext`.
- **`LinearMetaContext`**: Computes the `MetaContext` of a linear term (one meta-variable per `l-full-metavar` leaf).
