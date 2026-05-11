### Algebra.Monoid.MonoidHom

Homomorphisms between monoids and additive monoids, preserving the unit and the binary operation.

This module defines the records `MonoidHom` and `AddMonoidHom` as extensions of their pointed-map counterparts, enriched with multiplicativity (resp. additivity) of the underlying function. From these basic preservation laws, the module derives the standard consequences a monoid homomorphism must satisfy: it preserves powers, big products (or sums), divisibility witnesses, and invertible elements. The `AddMonoidHom` record is connected to `MonoidHom` via a coercion, allowing additive homomorphisms to be reused wherever multiplicative ones are expected.

#### Multiplicative Monoid Homomorphisms

- **`MonoidHom`**: Record extending `PointedHom` with domain and codomain refined to `Monoid` and the multiplicativity law `func (x * y) = func x * func y`.
- **`func-pow`**: A monoid homomorphism preserves natural-number powers: `func (x ^ n) = (func x) ^ n`.
- **`func-BigProd`**: Preservation of the big product over an array: `func (BigProd l) = BigProd (\lam j => func (l j))`.
- **`func-LDiv`**: Transports a left-divisibility witness `LDiv a b` along `func`, producing `LDiv (func a) (func b)` with witness `func d.inv`.
- **`func-Inv`**: Transports an invertibility witness, producing `Inv (func a)` from `Inv a` with the image of the inverse.

#### Constructions on Monoid Homomorphisms

- **`MonoidHom.equals`**: Extensionality — pointwise equality `\Pi (x : M) -> f x = g x` implies `f = g`.
- **`MonoidHom.id`**: The identity homomorphism `MonoidHom M M`.
- **`MonoidHom.presInv`**: Builds an `Inv (h e)` whose inverse is `h e.inv`, given that `e` is invertible in the domain.
- **`MonoidHom.presInvElem`**: Uniqueness of inverses under a homomorphism: `h e.inv = e'.inv` whenever `e'` witnesses invertibility of `h e`.

#### Additive Monoid Homomorphisms

- **`AddMonoidHom`**: Record extending `AddPointedHom` with domain and codomain refined to `AddMonoid` and the additivity law `func (x + y) = func x + func y`.
- **`func-BigSum`**: Preservation of the big sum over an array: `func (BigSum l) = BigSum (\lam j => func (l j))`.
- **`func-*n`**: Preservation of natural-number scalar multiplication: `func (n *n x) = n *n func x`.

#### Constructions on Additive Monoid Homomorphisms

- **`AddMonoidHom.toMonoidHom`**: Coercion turning an `AddMonoidHom` into a `MonoidHom` between the underlying multiplicative monoid structures.
- **`AddMonoidHom.id`**: The identity additive monoid homomorphism `AddMonoidHom M M`.
- **`AddMonoidHom.func-FinSum`**: For abelian monoids, an additive homomorphism preserves indexed finite sums: `f (FinSum a) = FinSum (\lam j => f (a j))` over a `FinSet`.
