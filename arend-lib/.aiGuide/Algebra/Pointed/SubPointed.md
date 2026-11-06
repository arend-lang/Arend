### Algebra.Pointed.SubPointed

Sub-pointed-sets: subsets of pointed (or additively pointed) sets that are closed under the distinguished element.

This module defines the notion of a subset of a pointed set that contains the basepoint, yielding an induced pointed structure on the subset itself. The multiplicative variant (`SubPointed`) carries `1`, while the additive variant (`SubAddPointed`) carries `0`; each is promoted by an instance (`IPointed` / `IAddPointed`) that promotes the underlying `ISet` of the `SubSet` to a `Pointed`/`AddPointed` by pairing the basepoint with its closure proof. The `max` constructions give the trivial maximal sub-pointed-set (the whole ambient set), and `DecSubPointed` adds decidability of membership.

#### Multiplicative Sub-Pointed Sets

- **`SubPointed`**: Class extending `SubSet` with `S : Pointed`, requiring `contains_ide : contains 1`. Represents a subset of a pointed set that is closed under the unit element.
- **`SubPointed.max`**: The maximal `SubPointed` over a `Pointed X`, built on top of `DecSubSet.max` (the full subset), with the basepoint trivially contained.
- **`IPointed`**: Promotes the induced `ISet` of a `SubPointed` `S` to a `Pointed` structure, with unit `(1, contains_ide)`.
- **`DecSubPointed`**: Class extending both `SubPointed` and `DecSubSet`; a sub-pointed-set with decidable membership.

#### Additive Sub-Pointed Sets

- **`SubAddPointed`**: Class extending `SubSet` with `S : AddPointed`, requiring `contains_zro : contains 0`. The additive analogue of `SubPointed`, for subsets closed under the zero element.
- **`SubAddPointed.max`**: The maximal `SubAddPointed` over an `AddPointed A`, built on `DecSubSet.max`, with zero trivially contained.
- **`IAddPointed`**: Promotes the induced `ISet` of a `SubAddPointed` `S` to an `AddPointed` structure, with zero `(0, contains_zro)`.

#### Kernels

- **`Kernel`**: For `f : PointedHom`, the `SubPointed` of `f.Dom` consisting of the elements `x` with `f x = 1`.
- **`IsKernelTrivial`**: The proposition that the kernel of `f : PointedHom` is trivial: every `x` in `Kernel f` equals `ide`.
- **`AddKernel`**: For `f : AddPointedHom`, the `SubAddPointed` of `f.Dom` consisting of the elements `x` with `f x = 0`.
- **`IsAddKernelTrivial`**: The proposition that the kernel of `f : AddPointedHom` is trivial: every `x` in `AddKernel f` equals `zro`.

#### Images

- **`ImagePointed`**: The image of `f : PointedHom` as a `SubPointed` of `f.Cod`: the elements `y` for which some `x` has `f x = y`, with unit witnessed by `(1, inP (1, func-ide))`.
- **`ImPointed`**: The image of `f : PointedHom` as a `Pointed`, namely `IPointed (ImagePointed f)`.
- **`ImPointedLeftHom`**: The corestriction `f.Dom -> ImPointed f` of `f` to its image, as a `PointedHom`.
- **`image-surj`**: The corestriction `ImPointedLeftHom f` is surjective (`IsSurj`).
- **`ImageAddPointed`**: The image of `f : AddPointedHom` as a `SubAddPointed` of `f.Cod`, with zero witnessed by `(0, inP (0, func-zro))`.
- **`ImAddPointed`**: The image of `f : AddPointedHom` as an `AddPointed`, namely `IAddPointed (ImageAddPointed f)`.
- **`ImAddPointedLeftHom`**: The corestriction `f.Dom -> ImAddPointed f` of `f` to its image, as an `AddPointedHom`.
- **`addImage-surj`**: The corestriction `ImAddPointedLeftHom f` is surjective (`IsSurj`).
