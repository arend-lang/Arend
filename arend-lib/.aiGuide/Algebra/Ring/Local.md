### Algebra.Ring.Local

Local rings: rings in which, for every element, either the element or its successor is invertible.

A local ring is a non-zero ring satisfying the constructive locality axiom: for any `x`, either `x` is a unit or `x + 1` is a unit. This formulation is equivalent (constructively) to the classical condition that the non-units form an ideal, and it implies that the ring of idempotents is connected (only `0` and `1` are idempotent). The module derives several useful consequences — including unit-detection in sums and finite sums equaling `1` — and provides a commutative variant.

#### Classes

- **`LocalRing`**: Extends `Ring` and `NonZeroRing`. A local ring with the constructive locality property, axiomatized via the `locality` field.
- **`LocalCRing`**: Extends `LocalRing` and `NonZeroCRing`. The commutative version of a local ring.

#### Axioms

- **`locality`**: For every `x : E`, either `x` is invertible or `x + ide` is invertible. The defining property of a local ring.

#### Locality Lemmas

- **`locality_-`**: Variant of `locality` using subtraction: for any `x`, either `x` is invertible or `ide - x` is invertible.
- **`sum1=>eitherInv`**: If `x + y = 1`, then either `x` or `y` is invertible. Constructive analogue of "non-units form an ideal" for binary sums.
- **`sumInv=>eitherInv`**: If `x + y` is invertible, then either `x` or `y` is invertible.
- **`sumInvArray`**: Array generalization: if `BigSum l` is invertible, some entry `l j` is invertible.
- **`sum1Array`**: If `BigSum l = 1`, some entry `l j` is invertible. Useful for partition-of-unity style arguments.

#### Connectedness

- **`connected`**: Every local ring is connected (`IsConnected`), i.e. its only idempotents are `0` and `1`.
- **`local=>connected`**: Standalone helper showing that any ring satisfying the locality predicate is connected, independent of the `LocalRing` class structure.
