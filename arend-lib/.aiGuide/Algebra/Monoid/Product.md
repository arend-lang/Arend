### Algebra.Monoid.Product

Componentwise monoid structures on the cartesian product of two monoids.

#### Multiplicative Products

- **`ProductMonoid`**: `Monoid` instance on `\Sigma M N` for monoids `M N`, with identity `(1, 1)` and componentwise multiplication.
- **`ProductCMonoid`**: `CMonoid` instance on the product of two commutative monoids, extending `ProductMonoid` with componentwise commutativity.

#### Additive Products

- **`ProductAddMonoid`**: `AddMonoid` instance on `\Sigma A B` for additive monoids `A B`, with zero `(0, 0)` and componentwise addition.
- **`ProductAbMonoid`**: `AbMonoid` instance on the product of two abelian monoids, extending `ProductAddMonoid` with componentwise commutativity of `+`.
