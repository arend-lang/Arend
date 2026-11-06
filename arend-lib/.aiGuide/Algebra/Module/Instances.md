### Algebra.Module.Instances

- **`LinearMapLModule`**: For a commutative ring `R`, `LinearMap A B` carries an `R`-module structure with scalar multiplication `(c · f) a := c ·_B f a`.
- **`LinearMapAbGroup`**: `LinearMap A B` is an abelian group under pointwise operations (zero, sum, negation).
- **`LinearMapRing`**: For any ring `R` and module `U`, the endomorphism set `LinearMap U U` forms a ring under pointwise addition and (reversed) composition `f * g := g ∘ f`.
- **`LinearMapRing.*c-hom`**: When `R` is commutative, the ring homomorphism `R → LinearMap U U` sending `r` to scalar multiplication by `r`.
