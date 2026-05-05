### Algebra.QModule

Q-modules (vector spaces over the rationals) and Q-algebras, characterized as divisible torsion-free abelian groups equipped with rational scalar action.

#### Group Structures

- **`DivisibleGroup`**: Extends `AbGroup` with `isDivisible`: for any element `a` and nonzero `n : Nat`, there exists `b` such that `n *n b = a`.
- **`TorsionFreeGroup`**: Extends `AbGroup` with `noTorsion`: if `n *n a = 0` for nonzero `n`, then `a = 0`.

#### Q-Modules

- **`QModule`**: Extends `DivisibleGroup` and `TorsionFreeGroup`; equivalent to a module over the rationals since divisibility provides division by naturals and torsion-freeness makes it unique.
- **`QModule.fromRatModule`**: Constructs a `QModule` from any `LModule` over `RatField`, using `RatField.finv` to witness divisibility and torsion-freeness.
- **`func-*q`**: Any additive group homomorphism between Q-modules automatically commutes with rational scalar multiplication: `f (q *q a) = q *q f a`.

#### Q-Algebras

- **`QPseudoAlgebra`**: Extends `QModule` and `PseudoRing`; a (possibly non-unital) ring whose underlying additive group is a Q-module.
- **`QPseudoAlgebra.fromRatAlgebra`**: Builds a `QPseudoAlgebra` from a `PseudoAlgebra` over `RatField`.
- **`QAlgebra`**: Extends `QPseudoAlgebra` and `Ring`; a unital ring that is also a Q-module.
- **`QAlgebra.fromRatAlgebra`**: Builds a `QAlgebra` from an associative algebra `AAlgebra` over `RatField`.

#### Instances

- **`RatQAlgebra`**: The canonical `QAlgebra` instance on `Rat`, where `Rat` acts on itself by multiplication.
