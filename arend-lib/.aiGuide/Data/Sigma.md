### Data.Sigma

Basic operations on non-dependent pair types and contractibility of the unit type.

This module provides the functorial action of pairing on functions — applying maps componentwise to a `\Sigma`-tuple — together with projection lemmas that confirm the action commutes with `.1` and `.2`. Convenience wrappers `tupleMapLeft` and `tupleMapRight` cover the common case of transforming only one component while leaving the other fixed via `id`. It also supplies the standard fact that the empty tuple type `\Sigma` is contractible.

#### Tuple Map

- **`tupleMap`**: Functorial action on pairs: given `f : A -> B` and `g : C -> D`, sends `(a, c) : \Sigma A C` to `(f a, g c) : \Sigma B D`.
- **`tupleMapLeft`**: Applies `f : A -> B` to the first component, leaving the second unchanged via `id`.
- **`tupleMapRight`**: Applies `f : B -> C` to the second component, leaving the first unchanged via `id`.

#### Projection Lemmas

- **`tupleMapProj1`**: `(tupleMap f g p).1 = f p.1`.
- **`tupleMapProj2`**: `(tupleMap f g p).2 = g p.2`.
- **`tupleMapLeftProj1`**: First-projection lemma specialized to `tupleMapLeft`.
- **`tupleMapLeftProj2`**: Second-projection lemma specialized to `tupleMapLeft` (the second component is unchanged).
- **`tupleMapRightProj1`**: First-projection lemma specialized to `tupleMapRight` (the first component is unchanged).
- **`tupleMapRightProj2`**: Second-projection lemma specialized to `tupleMapRight`.

#### Contractibility

- **`unit-isContr`**: The empty tuple type `\Sigma` is contractible, with center `()` and the trivial contraction `\lam _ => idp`.
