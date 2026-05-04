### Relation.Truncated

Truncated relation closures (propositionally truncated).

- **`Rel`**: Type alias `A -> A -> \Prop` for relations on a set.
- **`transitive-closure`**: Truncated transitive closure with `tc-direct` and `tc-connect`.
- **`transitive-refl-closure`**: Truncated transitive-reflexive closure with `trc-direct` (equality) and `trc-connect`.
- **`trc-unary`**: Embeds a single `R`-step into `transitive-refl-closure`.
- **`trc-direct'`**: Reflexivity for `transitive-refl-closure`.
- **`symmetric-closure`**: Symmetric closure with `sc-left` and `sc-right`.
- **`reflexive-closure`**: Reflexive closure with `rc-id` (equality) and `rc-rel`.
