### Algebra.MulOrdered

Monoids equipped with a partial order compatible with multiplication.

#### Classes

- **`OrderedMonoid`**: Extends `Poset` and `Monoid`. A monoid with a partial order such that multiplication is monotone in both arguments.
  - **`<=_*-left`**: Right-multiplication preserves order: `x <= y -> x * z <= y * z`.
  - **`<=_*-right`**: Left-multiplication preserves order: `x <= y -> z * x <= z * y`.
- **`OrderedCMonoid`**: Extends `OrderedMonoid` and `CMonoid`. A commutative ordered monoid; `<=_*-right` is derived from `<=_*-left` via commutativity of `*`.
