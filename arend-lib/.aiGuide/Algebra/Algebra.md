### Algebra.Algebra

Algebras over commutative rings: modules equipped with a compatible ring structure.

This module builds the standard hierarchy of associative algebras over a commutative ring `R`, layering ring multiplication on top of the `LModule` scalar action. The key compatibility axioms (`*c-comm-left`, `*c-comm-right`) require that the `R`-action commutes with internal multiplication on both sides, which makes the scalar map `coefMap : R -> E` a ring homomorphism landing in the center. Commutative variants (`PseudoCAlgebra`, `CAlgebra`) collapse the two-sided compatibility into one axiom via `*-comm`, and the `homAlgebra` construction shows that any ring homomorphism `R -> E` exhibits `E` as an `R`-algebra — the canonical way to produce algebra instances.

#### Algebra Classes

- **`PseudoAlgebra`**: Extends `LModule` and `PseudoRing` over a commutative ring `R`. Adds the bilinearity axioms `*c-comm-left` (`r *c (a * b) = (r *c a) * b`) and `*c-comm-right` (`r *c (a * b) = a * (r *c b)`) tying the scalar action to internal multiplication.
- **`AAlgebra`**: Associative algebra. Extends `PseudoAlgebra` and `Ring` (so a unit `1` is present) and adds the structure map `coefMap : R -> E` with the defining equation `coefMap_*c : coefMap x = x *c 1`. Default implementations are provided.
- **`PseudoCAlgebra`**: Commutative pseudo-algebra. Extends `PseudoAlgebra` and `CRing`; `*c-comm-right` is derived from `*c-comm-left` using `*-comm`.
- **`CAlgebra`**: Commutative associative algebra. Extends `AAlgebra` and `PseudoCAlgebra`.

#### Coefficient Map

- **`AAlgebra.coefHom`**: Packages `coefMap` as a `RingHom R \this`, recording that the scalar embedding preserves `+`, `1`, and `*`.
- **`AAlgebra.coefMap_natCoef`**: Compatibility with natural number coefficients: `coefMap (R.natCoef n) = natCoef n`, so `coefMap` agrees with the canonical `Nat`-action.

#### Constructions

- **`homAlgebra`**: Given a ring homomorphism `f : RingHom R E` between commutative rings, builds a `CAlgebra` structure on `E` whose scalar action is `r *c x = f r * x`. The standard recipe for producing algebra instances from ring maps.
- **`ringAlgebra`**: Every commutative ring `R` is an algebra over itself, obtained as `homAlgebra RingHom.id`.

#### Scalar Action on Modules

- **`*a`** (infixl 7): Restriction of scalars. For an `R`-algebra `A` and `A`-module `X`, defines `a *a x = coefMap a *c x`, giving `X` an underlying `R`-module structure.
- **`*a-assoc`**: Associativity of the restricted action: `a * b *a x = a *a (b *a x)`.
