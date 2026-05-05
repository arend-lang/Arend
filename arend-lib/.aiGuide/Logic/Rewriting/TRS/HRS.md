### Logic.Rewriting.TRS.HRS

Higher-order rewriting systems (term rewriting with binders).

- **`FSignature`**: Class with `Sort`, `symbol`, `domain` (higher-order arities as lists of `(List Sort, Sort)` pairs).
- **`MetaContext`**: Class with `metaname`, `m-domain` (meta-variable arities).
- **`Term`**: Inductive type with `var`, `metavar`, `func` constructors over an `FSignature`, context, sort, and `MetaContext`.
- **`Substitution`**: Context-to-context term maps; with `apply` (application to terms).
- **`MetaSubstitution`**: Meta-variable instantiation maps; with `apply` (meta-substitution application).
- **`RewriteRule`**: Class with `rr-mc`, `rr-l`, `rr-r` (left/right sides), `rr-l-func-root` (left side is function-rooted).
- **`RuleRegistry`**: Class with `rules` and `rr-sort` mapping rule indices to rewrite rules.
- **`RewriteRelation`**: The one-step rewrite relation `~>` induced by a `RuleRegistry`, forming a `SimpleARS`.
