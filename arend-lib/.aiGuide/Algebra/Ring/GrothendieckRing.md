### Algebra.Ring.GrothendieckRing

Constructs the Grothendieck ring of a semiring by extending the Grothendieck group construction with a multiplicative structure.

This module promotes the additive Grothendieck completion (`GrothendieckAbGroup`) of a semiring to a full ring by defining multiplication on equivalence classes of pairs `(a, b)` representing formal differences `a - b`. The product follows the standard "difference of products" formula `(a₁ - a₂)(b₁ - b₂) = (a₁b₁ + a₂b₂) - (a₂b₁ + a₁b₂)`, with well-definedness verified via the localization equivalence relation. When the input semiring is commutative, the resulting ring is also commutative.

#### Ring Instances

- **`GrothendieckRing`**: Given a `Semiring R`, produces a `Ring` whose underlying abelian group is `GrothendieckAbGroup R`. Multiplication on classes `in~ (a₁, a₂)` and `in~ (b₁, b₂)` is defined as `in~ (a₁b₁ + a₂b₂, a₂b₁ + a₁b₂)`, with identity `inl~ (1, 0, ())`. Congruence under `~-equiv` is established via `equation.semiring`.
- **`GrothendieckCRing`**: Given a `CSemiring R` (commutative semiring), promotes `GrothendieckRing R` to a `CRing` by adding commutativity of multiplication.
