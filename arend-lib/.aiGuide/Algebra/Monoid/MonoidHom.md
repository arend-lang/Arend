### Algebra.Monoid.MonoidHom

Homomorphisms between monoids and additive monoids, preserving the unit and binary operation.

#### Multiplicative Monoid Homomorphisms

- **`MonoidHom`**: Class extending `PointedHom` with monoids as domain/codomain. Adds **`func-*`**: `func (x * y) = func x * func y`, expressing preservation of multiplication.

#### Operations on `MonoidHom`

- **`MonoidHom.equals`**: Extensionality for monoid homomorphisms — pointwise equality `\Pi (x : M) -> f x = g x` implies `f = g`.
- **`MonoidHom.id`**: The identity homomorphism `MonoidHom M M`, with `func = \lam x => x`.
- **`MonoidHom.presInv`**: A monoid homomorphism preserves invertibility: given `e : Inv` in `Dom`, produces `Inv (h e)` with inverse `h e.inv`.
- **`MonoidHom.presInvElem`**: Uniqueness of the image of an inverse: if `e'` is any inverse of `h e`, then `h e.inv = e'.inv`.

#### Additive Monoid Homomorphisms

- **`AddMonoidHom`**: Class extending `AddPointedHom` with additive monoids as domain/codomain. Adds **`func-+`**: `func (x + y) = func x + func y`, expressing preservation of addition.

#### Operations on `AddMonoidHom`

- **`AddMonoidHom.toMonoidHom`**: Coercion from `AddMonoidHom` to `MonoidHom`, reusing `func-+` as `func-*` and `func-zro` as `func-ide`.
- **`AddMonoidHom.id`**: The identity additive homomorphism `AddMonoidHom M M`.
- **`AddMonoidHom.func-FinSum`**: An additive homomorphism between abelian monoids commutes with finite sums: `f (A.FinSum a) = B.FinSum (\lam j => f (a j))` over a `FinSet`-indexed family.
