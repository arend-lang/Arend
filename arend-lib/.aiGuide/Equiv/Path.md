### Equiv.Path

Equivalences derived from path types.

- **`pathEquiv`**: Given a relation `R : A -> A -> \Type` with a retraction from `a = a'` to `R a a'`, constructs a `QEquiv {a = a'} {R a a'}`.
- **`pathConcatEquiv`**: Pre-concatenation with `p : a = b` is a `QEquiv {a = c} {b = c}`.
- **`pmapEquiv`**: If `e` is an `Equiv`, then `pmap e` is a `QEquiv {a = a'} {e a = e a'}`.
- **`pmapEmbedding`**: If `e` is an `Embedding`, then `pmap e` is an `Embedding` on paths.
- **`pmapSection`**: If `s` is a `Section`, then `pmap s` is a `Section` on paths.
