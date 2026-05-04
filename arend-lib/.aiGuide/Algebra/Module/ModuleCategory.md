### Algebra.Module.ModuleCategory

The category of left modules over a ring, with linear maps as morphisms, plus kernel/image constructions, basis-change isomorphisms, and matrix representations.

#### Category Structure

- **`LModuleCat`**: The category `Cat (LModule R)` of left `R`-modules with `LinearMap` as `Hom`, identity and composition from `LinearMap`, and univalence via `sip`.
- **`LModuleCat.in`**: Inclusion `LinearMap S.IModule N` of a submodule `S : SubLModule R N` into its ambient module.
- **`LModuleCat.in-mono`**: The submodule inclusion `in S` is injective.
- **`LModulePreAdditive`**: Pre-additive structure on `LModuleCat R`, with hom-set abelian group given by pointwise zero, addition, and negation of linear maps, and bilinear composition.
- **`LinearMapLModule`**: For a commutative ring `R`, the `R`-module structure on `LinearMap A B` (pointwise scalar multiplication), extending the abelian group from `LModulePreAdditive`.

#### Zero Predicates

- **`IsZeroMod`**: Predicate stating that every element of a module is zero.
- **`IsZeroMap`**: Predicate stating that a linear map sends every element to zero.

#### Kernel and Image

- **`KerLModule`**: The kernel of `f : LinearMap A B` as an `R`-module, extending `KerAbGroup` with scalar multiplication on the underlying element.
- **`KerLModuleHom`**: The canonical inclusion `LinearMap (KerLModule f) A`, extending `KerGroupHom`.
- **`ImageLModule`**: The image of `f : LinearMap A B` as an `R`-module, extending `ImageAbGroup` with scalar multiplication lifted through the truncated preimage.
- **`ImageLModuleLeftHom`**: The corestriction `LinearMap A (ImageLModule f)`, extending `ImageAddGroupLeftHom`.
- **`ImageLModuleRightHom`**: The inclusion `LinearMap (ImageLModule f) B`, extending `ImageAddGroupRightHom`.

#### Endomorphism Ring

- **`LinearMapRing`**: The ring `Ring (LinearMap U U)` of endomorphisms, with addition pointwise and multiplication by reverse composition (`f * g = g ∘ f`).
- **`LinearMapRing.*c-hom`**: For commutative `R`, the ring homomorphism `RingHom R (LinearMapRing U)` sending a scalar to scalar multiplication on `U`.

#### Isomorphisms via Bases and Matrices

- **`toLinearMapIso`**: Builds an `Iso U V` from an invertible matrix `A : Monoid.Inv {MatrixRing R n}` together with bases of `U` and `V` of length `n`.
- **`toMatrixInv`**: Converse: given an iso `e : U ≃ V` and bases of equal length, produces an invertible matrix.
- **`basis-iso`**: The iso `U ≃ V` induced by two bases of the same length, sending `lu_i` to `lv_i` via `extend`.
- **`iso-basis`**: An iso `f : U ≃ V` carries a basis of `U` to a basis of `V` (`map f.f l`).

#### Iso Characterizations

- **`iso<->inj+surj`**: A linear map is an iso iff it is both injective and surjective.
- **`ZeroIm=>Zero-func`**: If the image module is zero, the map itself is zero (with helpers `aux-1`, `aux-2` constructing the image element of `m`).
- **`FullKer=>Zero-func`**: If the kernel inclusion is surjective (i.e., everything is in the kernel), the map is zero.
- **`zeroKer-FullIm<->inj+surj`**: Injectivity + surjectivity is equivalent to zero kernel + surjective image inclusion. Helpers `inj->zeroKer`, `surj->FullIm`, `zeroKer->inj`, `FullIm->surj` give the four directions.
- **`iso<->zeroKer-FullIm`**: Combined characterization: iso ↔ zero kernel ∧ surjective image inclusion.

#### Change of Basis

- **`change-basis-right`**: Precomposing `toLinearMap bv lw A` with an iso `f : U ≃ V` equals `toLinearMap` over the transported basis `iso-basis f.reverse bv`.
- **`change-basis_matrix-left`**: Multiplying on the left by the basis-change matrix `Iso.f {basis-iso bu' bu}` converts `toMatrix lu' bv f` into `toMatrix lu bv f`.
- **`change-basis_matrix-right`**: Right multiplication analogue: multiplying `toMatrix lu bv' f` by `toMatrix lv bv (Iso.f {basis-iso bv bv'})` yields `toMatrix lu bv f`.
- **`change-basis_M~`**: Matrix representations of the same linear map under different bases are matrix-equivalent (`M~`).
