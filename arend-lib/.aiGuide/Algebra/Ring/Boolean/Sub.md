### Algebra.Ring.Boolean.Sub

Sub-structures of Boolean rings, providing the canonical Boolean ring structure inherited by a subring of a Boolean ring.

A Boolean ring is a (pseudo-)ring in which every element is idempotent (`x * x = x`). Since this property is preserved under restriction to any subring, a `SubPseudoRing` of a `BooleanRing` automatically inherits the Boolean ring axiom. This module provides the class that packages this fact and constructs the inherited Boolean ring structure on the subring.

#### Sub-Boolean-Ring Class

- **`SubBooleanPseudoRing`**: Class extending `SubPseudoRing`, with the ambient ring `S` overridden to be a `BooleanRing`. Represents a sub-pseudo-ring of a Boolean ring.
  - **`IBooleanPseudoRing`**: The induced `BooleanRing` structure on the underlying sub-pseudo-ring `IPseudoRing`, witnessing that the Boolean (idempotence) axiom transfers to the subring.
