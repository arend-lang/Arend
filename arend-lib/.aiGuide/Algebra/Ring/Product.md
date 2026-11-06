### Algebra.Ring.Product

The binary product of commutative rings, with its two projections.

For `R S : CRing` the product ring has carrier `\Sigma R S` with all operations defined componentwise. The projections to both factors are ring homomorphisms.

#### Product ring

- **`ProductCRing`**: the commutative ring structure on `\Sigma R S` with componentwise addition, multiplication, zero, unit and negation. It has two ring homomorphisms `ProductCRing.proj1` and `ProductCRing.proj2` onto `R` and `S`.
