### Algebra.Pointed.PointedCategory

Categories of pointed and additively-pointed sets, together with kernel and image constructions for their morphisms.

#### Categories

- **`PointedCat`**: The category `Cat Pointed` of pointed sets and `PointedHom` morphisms, with identity, composition, and univalence via `sip`.
- **`AddPointedCat`**: The category `Cat AddPointed` of additively-pointed sets and `AddPointedHom` morphisms, with identity, composition, and univalence via `sip`.

#### Kernels

- **`Kernel`**: The kernel of an `AddPointedHom f` as the type `\Sigma (a : f.Dom) (f a = 0)`.
- **`KerAddPointed`**: `AddPointed` instance on `Kernel f`, with zero `(0, func-zro)`.
- **`KerPointedHom`**: The canonical inclusion `AddPointedHom (KerAddPointed f) f.Dom` projecting on the first component.
- **`kernel-inj`**: The kernel inclusion `KerPointedHom f` is injective (`IsInj`).

#### Images

- **`ImageAddPointed`**: `AddPointed` instance on `Image f` for an `AddPointedHom`, with zero witnessed by `func-zro`.
- **`ImagePointed`**: `Pointed` instance on `Image f` for a `PointedHom`, with unit witnessed by `func-ide`.
- **`ImagePointedLeftHom`**: The corestriction `PointedHom f.Dom (ImagePointed f)` sending `a` to `(f a, inP (a, idp))`.
- **`ImagePointedRightHom`**: The inclusion `PointedHom (ImagePointed f) f.Cod` projecting on the first component.
- **`ImageAddPointedLeftHom`**: Additive version of the corestriction `AddPointedHom f.Dom (ImageAddPointed f)`.
- **`ImageAddPointedRightHom`**: Additive version of the inclusion `AddPointedHom (ImageAddPointed f) f.Cod`.
- **`image-surj`**: The corestriction `ImageAddPointedLeftHom f` is surjective (`IsSurj`).
- **`image-inj`**: The image inclusion `ImageAddPointedRightHom f` is injective (`IsInj`).
