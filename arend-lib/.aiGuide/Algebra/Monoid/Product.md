### Algebra.Monoid.Product

Componentwise product structures on monoids and additive (commutative) monoids.

This module equips the Cartesian product `\Sigma M N` of two monoids with the pointwise monoid operation, and lifts this construction through the standard hierarchy: plain multiplicative monoids, additive monoids, and their commutative variants. Each instance reuses the underlying multiplicative product when defining its additive or commutative refinement, so the four instances form a small layered tower rather than independent definitions. This is the canonical way to obtain product objects in the categories of monoids used elsewhere in the algebra library.

#### Multiplicative Products

- **`ProductMonoid`**: `Monoid` instance on `\Sigma M N` with identity `(1, 1)` and componentwise multiplication; provides the product in the category of monoids.
- **`ProductCMonoid`**: `CMonoid` instance on the product of two commutative monoids, extending `ProductMonoid` with componentwise commutativity.

#### Additive Products

- **`ProductAddMonoid`**: `AddMonoid` instance on `\Sigma A B` with zero `(0, 0)` and componentwise addition; the additive analogue of `ProductMonoid`.
- **`ProductAbMonoid`**: `AbMonoid` instance on the product of two abelian monoids, extending `ProductAddMonoid` with componentwise commutativity of `+`.
