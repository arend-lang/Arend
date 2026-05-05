### Logic.Rewriting.TRS.Union

Union (sum) of term rewriting systems.

- **`SumFSignature`**: Combines a family of `FSignature`s indexed by `J` into one via disjoint union of symbols.
- **`inject-term`**: Embeds a term over `S j` into `SumFSignature S`.
- **`inject-rule-container`**: Embeds a `RewriteRule` into the sum signature.
- **`SumRegistry`**: Combines a family of `RuleRegistry`s into one for the sum signature.
