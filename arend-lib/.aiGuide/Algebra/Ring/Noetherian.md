### Algebra.Ring.Noetherian

Noetherian commutative rings, characterized by the ascending chain condition on finitely generated ideals.

#### Classes

- **`NoetherianCRing`**: Extends `CRing`. A commutative ring is Noetherian if every chain of finitely generated ideals stabilizes.
  - **`isNoetherian`**: Given a sequence `I : Nat -> Ideal \this` of ideals such that each `I n` is finitely generated, the chain `I` satisfies the ascending chain condition (`Ideal.ChainCondition I`).
