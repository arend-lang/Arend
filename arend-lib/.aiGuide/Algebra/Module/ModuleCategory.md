### Algebra.Module.ModuleCategory

The category of left modules over a ring, together with kernel/image submodules, basis-change matrices, and the equivalence between isomorphisms and injective+surjective linear maps.

This module assembles the categorical structure of `LModule R`: it packages `LinearMap`s as the morphisms of a category `LModuleCat R`, lifts the abelian-group structure on hom-sets to make it pre-additive, and shows that `LinearMap A B` is itself an `R`-module when `R` is commutative. Kernel and image of a linear map are constructed as concrete submodules (built on top of their abelian-group counterparts), and the standard correspondence between iso, injective+surjective, and "zero kernel + full image" is established. The final section connects bases of free modules to matrices, exhibiting how a change of basis acts on linear maps via conjugation/multiplication by an invertible matrix and proving that any two matrix representations of the same map are matrix-equivalent (`M~`).

#### Category Structure

- **`LModuleCat`**: The category of left `R`-modules with `LinearMap`s as morphisms; a `Cat` instance using identity and composition of linear maps.
- **`LModuleCat.in`**: The canonical inclusion `LinearMap S.IModule N` of a submodule `S : SubLModule R N` into its ambient module.
- **`LModuleCat.in-mono`**: The submodule inclusion is injective.
- **`LModulePreAdditive`**: Pre-additive structure on `LModuleCat R`: hom-sets `LinearMap A B` form an abelian group under pointwise addition (`linearMap_+`, `linearMap_zro`, `linearMap_negative`), and composition is bilinear.

#### Modules from Linear Maps

- **`LinearMapLModule`**: For a commutative ring `R`, `LinearMap A B` carries an `R`-module structure with scalar multiplication `(c · f) a := c ·_B f a`.
- **`LinearMapRing`**: For any ring `R` and module `U`, the endomorphism set `LinearMap U U` forms a ring under pointwise addition and (reversed) composition `f * g := g ∘ f`.
- **`LinearMapRing.*c-hom`**: When `R` is commutative, the ring homomorphism `R → LinearMap U U` sending `r` to scalar multiplication by `r`.

#### Zero Predicates

- **`IsZeroMod`**: Predicate stating that every element of `M` equals `0`, i.e., `M` is the zero module.
- **`IsZeroMap`**: Predicate stating that a linear map is identically zero.

#### Kernel

- **`KerLModule`**: The kernel of `f : LinearMap A B` as an `R`-module, extending `KerAbGroup f` with scalar multiplication inherited from `A` (using `B.*c_zro-right` to preserve the kernel condition).
- **`KerLModuleHom`**: The inclusion `KerLModule f → A` as a linear map, extending `KerGroupHom f`.

#### Image

- **`ImageLModule`**: The image of `f : LinearMap A B` as an `R`-module, extending `ImageAbGroup f`; scalar multiplication on `(b, ∃ a. f a = b)` lifts via `TruncP.map`.
- **`ImageLModuleLeftHom`**: The corestriction `A → ImageLModule f` as a linear map.
- **`ImageLModuleRightHom`**: The inclusion `ImageLModule f → B` as a linear map.

#### Iso vs. Injective+Surjective

- **`iso<->inj+surj`**: A linear map is an iso iff it is both injective and surjective.
- **`ZeroIm=>Zero-func`**: If the image module of `f` is zero, then `f` is the zero map.
- **`FullKer=>Zero-func`**: If the kernel inclusion is surjective (so the kernel is everything), then `f` is the zero map.
- **`zeroKer-FullIm<->inj+surj`**: `(IsInj f, IsSurj f)` is equivalent to `(IsZeroMod (KerLModule f), IsSurj (ImageLModuleRightHom f))`, with the four directions packaged as `inj->zeroKer`, `surj->FullIm`, `zeroKer->inj`, `FullIm->surj`.
- **`iso<->zeroKer-FullIm`**: `Iso f` iff the kernel is the zero module and the image inclusion is surjective.

#### Bases, Matrices, and Change of Basis

- **`toLinearMapIso`**: Given bases `lu`, `lv` of `U` and `V` of equal length and an invertible matrix `A : MatrixRing R n`, produces the corresponding linear iso `U ≃ V`.
- **`toMatrixInv`**: Conversely, an iso `U ≃ V` between `n`-dimensional free modules yields an invertible `n × n` matrix.
- **`basis-iso`**: The iso `U ≃ V` obtained from two same-length bases via `extend` (sending one basis to the other).
- **`iso-basis`**: An iso `f : U ≃ V` carries a basis of `U` to a basis of `V` via `map f.f`.
- **`change-basis-right`**: Composing `toLinearMap bv lw A` with an iso `f : U ≃ V` equals `toLinearMap (iso-basis f.reverse bv) lw A` — precomposition by an iso reindexes the source basis.
- **`change-basis_matrix-left`**: Multiplying a map's matrix by the change-of-source-basis matrix recovers the matrix in the new source basis.
- **`change-basis_matrix-right`**: Dual statement on the right (target basis change).
- **`change-basis_M~`**: Two matrix representations of the same linear map in different bases are matrix-equivalent (`M~`).
