### Algebra.Algebra

Algebras over commutative rings: modules equipped with a compatible (pseudo-)ring structure where scalar multiplication commutes with the ring product.

#### Algebra Classes

- **`PseudoAlgebra`**: Extends `LModule` and `PseudoRing` over a commutative ring `R`, with compatibility laws `*c-comm-left` (`r *c (a * b) = (r *c a) * b`) and `*c-comm-right` (`r *c (a * b) = a * (r *c b)`) linking scalar action and ring multiplication.
- **`AAlgebra`**: Extends `PseudoAlgebra` and `Ring`, adding a structure map `coefMap : R -> E` from the base ring into the algebra, with the defining law `coefMap x = x *c 1`.
- **`PseudoCAlgebra`**: Extends `PseudoAlgebra` and `CRing`; in the commutative setting `*c-comm-right` is derived automatically from `*c-comm-left` and commutativity of multiplication.
- **`CAlgebra`**: Extends `AAlgebra` and `PseudoCAlgebra` — a commutative algebra over a commutative ring.

#### Constructions

- **`homAlgebra`**: Given a ring homomorphism `f : RingHom R E` between commutative rings, builds a `CAlgebra R` structure on `E` whose scalar action is induced by `f` via `homLModule`.
- **`ringAlgebra`**: The canonical `CAlgebra R` structure on `R` itself, obtained from the identity ring homomorphism.

#### Scalar Action via Coefficient Map

- **`*a`**: Scalar multiplication of an algebra-module by base ring elements: `a *a x = coefMap a *c x`, lifting the `R`-action through `coefMap` to any `LModule` over an `AAlgebra R`.
- **`*a-assoc`**: Associativity/compatibility of `*a` with the ring product in `R`: `(a * b) *a x = a *a (b *a x)`.
