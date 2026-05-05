### Data.Sigma

This module provides utility functions for sigma types (pairs/tuples).

#### Tuple Mapping

- **`tupleMap`**: Maps both components of a pair `(A, C)` to `(B, D)` via `f : A -> B` and `g : C -> D`.
- **`tupleMapProj1`**: `(tupleMap f g p).1 = f p.1`.
- **`tupleMapProj2`**: `(tupleMap f g p).2 = g p.2`.
- **`tupleMapLeft`**: Maps only the first component: `tupleMap f id`.
- **`tupleMapRight`**: Maps only the second component: `tupleMap id f`.
- **`tupleMapLeftProj1`**: Projection lemma for `tupleMapLeft`, first component.
- **`tupleMapLeftProj2`**: Projection lemma for `tupleMapLeft`, second component.
- **`tupleMapRightProj1`**: Projection lemma for `tupleMapRight`, first component.
- **`tupleMapRightProj2`**: Projection lemma for `tupleMapRight`, second component.

#### Unit Type

- **`unit-isContr`**: The unit type `\Sigma` (empty tuple) is contractible (`Contr (\Sigma)`).
