### Topology.CStarAlgebra.CompleteStoneCStarAlgebra

Homomorphisms between ordered/Stone C*-algebras and the completion of an ordered C*-algebra into a Stone C*-algebra.

#### Homomorphism Classes

- **`OrderedC*AlgebraHom`**: Class extending `RingHom` and `PosetHom` between `OrderedC*Algebra` instances; a ring homomorphism that also preserves the order.
- **`OrderedC*AlgebraHom.fromNormed`**: Constructs an `OrderedC*AlgebraHom` from `X : OrderedC*Algebra` to `Y : StoneC*Algebra` given a `RingHom` whose action is norm-bounded by the source's Banach norm.
- **`StoneC*AlgebraHom`**: Class extending `OrderedC*AlgebraHom` and `BoundedLinearMap` between `StoneC*Algebra` instances; the `func-<=` field is derived from ring-homomorphism properties, and `isBounded` is witnessed by constant `1` using the C*-norm characterization.
- **`StoneC*AlgebraHom.fromRingHom`**: Promotes any `RingHom` between Stone C*-algebras to a `StoneC*AlgebraHom` (boundedness and order preservation are automatic).

#### Dense Lifting

- **`dense-c*-lift`**: Given an `OrderedC*AlgebraHom f : X -> Y` that is dense (`fd`) and isometric (`fi`), and any `OrderedC*AlgebraHom g : X -> Z` into a Stone C*-algebra `Z`, produces the unique extending `OrderedC*AlgebraHom Y Z`. Uses `dense-normed-lift` for the underlying continuous map and verifies that multiplication is preserved via `dense-lift-unique` on the product space, with locally uniform multiplication.
- **`dense-c*-lift.char`**: Characterizing equation: the lifted map agrees with `g` on the image of `f`, i.e. `dense-c*-lift f fd fi g (f x) = g x`.

#### Completion

- **`StoneC*AlgebraCompletion`**: Instance making the Banach-algebra completion of any `OrderedC*Algebra X` into a `StoneC*Algebra`. Inherits `RealBanachAlgebra` structure from `BanachAlgebraCompletion X.toBanach` and provides commutativity of multiplication (`*-comm`), the C*-sum identity (`c*-sum`), and the C*-square identity (`c*-square`) by extending the corresponding identities of `X` along the dense isometric inclusion.
