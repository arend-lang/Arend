### Algebra.Ring.Local

Local rings: rings where for every element, either it or its successor is invertible.

#### Classes

- **`LocalRing`**: Extends `Ring` and `NonZeroRing`. A local ring, characterized by the `locality` axiom: for every `x : E`, either `x` is invertible or `x + ide` is invertible.
- **`LocalCRing`**: Extends `LocalRing` and `NonZeroCRing`. A commutative local ring.

#### Axioms

- **`locality`**: For any `x : E`, `Inv x || Inv (x + ide)` — the defining property of a local ring (every element or its shift by one is a unit).

#### Lemmas

- **`local=>connected`**: Given a ring `R` satisfying the locality condition (`\Pi (a : R) -> Inv a || Inv (a + 1)`), `R` is connected (`R.IsConnected`).
