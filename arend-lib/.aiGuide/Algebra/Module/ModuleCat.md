### Algebra.Module.ModuleCat

- **`LModuleCat`**: The category of left `R`-modules with `LinearMap`s as morphisms; a `Cat` instance using identity and composition of linear maps.
- **`LModulePreAdditive`**: Pre-additive structure on `LModuleCat R`: hom-sets `LinearMap A B` form an abelian group under pointwise addition (`linearMap_+`, `linearMap_zro`, `linearMap_negative`), and composition is bilinear.
