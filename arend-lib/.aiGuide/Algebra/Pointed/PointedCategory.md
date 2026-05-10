### Algebra.Pointed.PointedCategory

Category structures on pointed and additively pointed sets, together with kernel and image constructions for their morphisms.

This module assembles `Pointed` and `AddPointed` into categories using the corresponding pointed homomorphisms as arrows. It also provides the standard factorization data for an additively pointed map `f`: its kernel (fiber over `0`) and its image, each equipped with the appropriate pointed structure and the canonical inclusion / projection morphisms. The accompanying `IsInj` / `IsSurj` lemmas certify that kernels embed and images surject, giving the basic building blocks for an exact-sequence vocabulary in later algebraic developments.

#### Categories

- **`PointedCat`**: The category of pointed types `Pointed` with `PointedHom` as morphisms, identity `PointedHom.id`, and composition `PointedHom.∘`.
- **`AddPointedCat`**: The category of additively pointed types `AddPointed` with `AddPointedHom` as morphisms, identity `AddPointedHom.id`, and composition `AddPointedHom.∘`.

#### Kernels

- **`Kernel`**: For `f : AddPointedHom`, the type `\Sigma (a : f.Dom) (f a = 0)` of elements mapped to zero.
- **`KerAddPointed`**: Equips `Kernel f` with an `AddPointed` structure whose zero is `(0, func-zro)`.
- **`KerPointedHom`**: The canonical inclusion `KerAddPointed f → f.Dom` projecting on the first component.
- **`kernel-inj`**: The kernel inclusion `KerPointedHom f` is injective (`IsInj`).

#### Images

- **`ImageAddPointed`**: Equips `Image f` with an `AddPointed` structure for `f : AddPointedHom`, with zero witnessed by `(0, inP (0, func-zro))`.
- **`ImagePointed`**: Equips `Image f` with a `Pointed` structure for `f : PointedHom`, with unit witnessed by `(ide, inP (ide, func-ide))`.
- **`ImagePointedLeftHom`**: The corestriction `f.Dom → ImagePointed f`, sending `a` to `(f a, inP (a, idp))`, as a `PointedHom`.
- **`ImagePointedRightHom`**: The inclusion `ImagePointed f → f.Cod` projecting on the first component, as a `PointedHom`.
- **`ImageAddPointedLeftHom`**: The corestriction `f.Dom → ImageAddPointed f` as an `AddPointedHom`.
- **`ImageAddPointedRightHom`**: The inclusion `ImageAddPointed f → f.Cod` as an `AddPointedHom`.
- **`image-surj`**: The corestriction `ImageAddPointedLeftHom f` is surjective (`IsSurj`).
- **`image-inj`**: The inclusion `ImageAddPointedRightHom f` is injective (`IsInj`), giving the image as the mono-part of the canonical epi-mono factorization of `f`.
