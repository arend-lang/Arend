### Algebra.Module.BilinearMap

- **`BilinearMap`**: Record of bilinear maps `A → B → C` requiring linearity in each argument separately (`linear-left`, `linear-right`).
- **`*-bilinear`**: Multiplication of a commutative ring `R` is a bilinear map `R × R → R` on the module `RingLModule R`.
- **`BilinearMapModule`**: Instance making `BilinearMap A B C` an `R`-module over a `CRing R`, with zero, addition, negation and scalar multiplication defined pointwise.
