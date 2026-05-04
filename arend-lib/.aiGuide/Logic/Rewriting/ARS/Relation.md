### Logic.Rewriting.ARS.Relation

Relation utilities for rewriting.

- **`Rel`**: Type alias `A -> A -> \Type`.
- **`StraightJoin`**: Class witnessing a common reduct of two elements.
- **`Closure`**: Non-truncated closure of a relation with `c-trivial`, `c-basic`, `c-connect`.
  - **`compose`**: Composition of closures.
  - **`lift`**: Lifts a closure along a map preserving the relation.
