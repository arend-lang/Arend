### Algebra.Module.ModuleIso

- **`change-basis_M~`**: Two matrix representations of the same linear map in different bases are matrix-equivalent (`M~`).
- **`ModuleIso`**: Record of an isomorphism of `R`-modules: a linear map `f : U -> V` together with a linear inverse `finv` and the laws `finv_f`, `f_finv`. Being an iso is a proposition once `f` is fixed.
- **`ModuleIso.reverse`**: The inverse isomorphism, swapping `f` and `finv`.
- **`ModuleIso.isInj`**, **`ModuleIso.isSurj`**: The underlying map of an iso is injective and surjective.
- **`iso<->inj+surj`**: A linear map is an iso iff it is both injective and surjective.
- **`toLinearMapIso`**: Given bases `lu`, `lv` of `U` and `V` of equal length and an invertible matrix `A : MatrixRing R n`, produces the corresponding linear iso `U ≃ V`.
- **`toMatrixInv`**: Conversely, an iso `U ≃ V` between `n`-dimensional free modules yields an invertible `n × n` matrix.
- **`basis-iso`**: The iso `U ≃ V` obtained from two same-length bases via `extend` (sending one basis to the other).
- **`iso-basis`**: An iso `f : U ≃ V` carries a basis of `U` to a basis of `V` via `map f.f`.
- **`change-basis-right`**: Composing `toLinearMap bv lw A` with an iso `f : U ≃ V` equals `toLinearMap (iso-basis f.reverse bv) lw A` — precomposition by an iso reindexes the source basis.
- **`change-basis_matrix-left`**: Multiplying a map's matrix by the change-of-source-basis matrix recovers the matrix in the new source basis.
- **`change-basis_matrix-right`**: Dual statement on the right (target basis change).
