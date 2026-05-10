### Algebra.Ring.Noetherian

Noetherian commutative rings, defined via the ascending chain condition on finitely generated ideals.

This module formalizes Noetherianity constructively. Rather than asserting that every ideal is finitely generated (which is classically equivalent but constructively stronger), the definition requires that every chain of finitely generated ideals stabilizes. This phrasing makes the property accessible without relying on classical logic, while still capturing the essential finiteness behavior of Noetherian rings.

#### Classes

- **`NoetherianCRing`**: Extends `CRing`. A commutative ring satisfying the ascending chain condition on finitely generated ideals.
  - **`isNoetherian`**: Given a sequence of ideals `I : Nat -> Ideal \this`, if each `I n` is finitely generated (`Ideal.IsFinitelyGenerated`), then the chain `I` satisfies `Ideal.ChainCondition` (i.e., it eventually stabilizes).
