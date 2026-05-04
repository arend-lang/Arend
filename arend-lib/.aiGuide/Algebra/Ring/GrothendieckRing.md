### Algebra.Ring.GrothendieckRing

Constructs the Grothendieck ring of a semiring by completing its additive monoid into an abelian group while preserving the multiplicative structure.

#### Ring Instances

- **`GrothendieckRing`**: Given a `Semiring R`, produces a `Ring` whose additive group is the Grothendieck group `GrothendieckAbGroup R` (formal differences of pairs in `R`), with multiplication, identity `inl~ (1, 0, ())`, associativity, distributivity, and unit/zero laws all derived from the semiring laws via `~-lequiv` on equivalence classes.
- **`GrothendieckCRing`**: Given a `CSemiring R`, extends `GrothendieckRing R` to a commutative ring `CRing` by establishing `*-comm` on equivalence classes using the commutative semiring laws.
