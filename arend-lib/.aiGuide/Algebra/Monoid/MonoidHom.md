### Algebra.Monoid.MonoidHom

Homomorphisms between monoids and additive monoids, preserving the unit and the binary operation.

This module defines `SemigroupHom` (a set map preserving `*`), the records `MonoidHom` (extending `PointedHom` and `SemigroupHom`) and `AddMonoidHom` (extending `AddPointedHom` with additivity of the underlying function). From these basic preservation laws, the module derives the standard consequences a monoid homomorphism must satisfy: it preserves powers, big products (or sums), divisibility witnesses, and invertible elements. The `AddMonoidHom` record is connected to `MonoidHom` via a coercion, allowing additive homomorphisms to be reused wherever multiplicative ones are expected.

#### Multiplicative Monoid Homomorphisms

- **`SemigroupHom`**: Record extending `SetHom` with domain and codomain refined to `Semigroup` and the multiplicativity law `func-*`: `func (x * y) = func x * func y`.
- **`MonoidHom`**: Record extending `PointedHom` and `SemigroupHom` with domain and codomain refined to `Monoid`; it preserves the unit (`func-ide`) and multiplication (`func-*`).
- **`func-pow`**: A monoid homomorphism preserves natural-number powers: `func (x ^ n) = (func x) ^ n`.
- **`func-BigProd`**: Preservation of the big product over an array: `func (BigProd l) = BigProd (\lam j => func (l j))`.
- **`func-LDiv`**: Transports a left-divisibility witness `LDiv a b` along `func`, producing `LDiv (func a) (func b)` with witness `func d.inv`.
- **`func-Inv`**: Transports an invertibility witness, producing `Inv (func a)` from `Inv a` with `func d.inv` as its inverse.

#### Constructions on Monoid Homomorphisms

- **`MonoidHom.equals`**: Extensionality — pointwise equality `\Pi (x : M) -> f x = g x` implies `f = g`.
- **`MonoidHom.id`**: The identity homomorphism `MonoidHom M M`.
- **`MonoidHom.func-InvElem`**: If `func e = e'` for invertibles `e` of `Dom` and `e'` of `Cod`, then `func e.inv = e'.inv`.
- **`MonoidHom.equiv-hom`**: If `func` is an equivalence, its inverse `IsEquiv.ret e` is a `MonoidHom Cod Dom`.
- **`MonoidHom.equiv-Inv`**: If `func` is an equivalence and `func a` is invertible, then `a` is invertible.

#### Additive Monoid Homomorphisms

- **`AddMonoidHom`**: Record extending `AddPointedHom` with domain and codomain refined to `AddMonoid` and the additivity law `func (x + y) = func x + func y`.
- **`func-BigSum`**: Preservation of the big sum over an array: `func (BigSum l) = BigSum (\lam j => func (l j))`.
- **`func-*n`**: Preservation of natural-number scalar multiplication: `func (n *n x) = n *n func x`.

#### Constructions on Additive Monoid Homomorphisms

- **`AddMonoidHom.toMonoidHom`**: Coercion turning an `AddMonoidHom` into a `MonoidHom` between the underlying multiplicative monoid structures.
- **`AddMonoidHom.id`**: The identity additive monoid homomorphism `AddMonoidHom M M`.
- **`AddMonoidHom.func-FinSum`**: For abelian monoids, an additive homomorphism preserves indexed finite sums: `f (FinSum a) = FinSum (\lam j => f (a j))` over a `FinSet`.
