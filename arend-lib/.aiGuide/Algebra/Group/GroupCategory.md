### Algebra.Group.GroupCategory

Category-theoretic structure on groups, abelian groups, and additive groups, together with kernels, images, and quotients as functorial constructions.

This module assembles the categories `GroupCat`, `AddGroupCat`, and `AbGroupCat`, with `AbGroupCat` realized as a subcategory of `AddGroupCat` via an embedding. It characterizes isomorphisms in `GroupCat` in two equivalent algebraic forms (injective+surjective, or trivial-kernel+surjective) by transferring to set-isomorphisms in `SetCat`. Kernels, images, and quotients of group homomorphisms are packaged as group instances together with the canonical projection/inclusion homomorphisms, providing the building blocks for diagram chasing and homological reasoning. Forgetful functors to `SetCat`, `AddMonoidCat`, and `AddGroupCat` are also provided.

#### Group Category

- **`GroupCat`**: The category of groups with `GroupHom` as morphisms; instance of `Cat Group`.
- **`GroupCat.ForgetSet`**: The forgetful functor `GroupCat -> SetCat`, shown to be faithful.

#### Characterizations of Isomorphisms in `GroupCat`

- **`GroupCat.Iso<->Inj+Surj`**: A group homomorphism `f : G -> H` is an iso in `GroupCat` iff it is both injective and surjective (`f.IsIsomorphism`).
- **`GroupCat.Iso<->TrivialKer+Surj`**: A group homomorphism is an iso iff it has trivial kernel and is surjective.
- **`Iso<->Inj+Surj.this_iso`**, **`inve`**: Builds the inverse set-isomorphism from the injective+surjective data.
- **`Iso<->Inj+Surj.this_inv_ap`**, **`inv_this_ap`**: The inverse equation `h = f (inve p h)` and `g = inve p (f g)`.
- **`Iso<->Inj+Surj.SetIso->Inj+Surj`**, **`Inj+Surj->SetIso`**: Conversion between set-isomorphism data and injectivity+surjectivity.
- **`Iso<->Inj+Surj.Equiv->SetIso`**, **`SetIso->Equiv`**: Equivalence between `Equiv` and `Iso {SetCat}` for maps of sets.
- **`Iso<->TrivialKer+Surj.helper`**: Equivalence between `(IsInj f, IsSurj f)` and `(f.TrivialKernel, IsSurj f)`.

#### Quotients, Images, and Subgroups

- **`quotient-map`**: The canonical projection `GroupHom S H.quotient` for a normal subgroup `H ◁ S`.
- **`ImageGroup`**: The image of a group homomorphism `f` as a `Group`, extending `ImageMonoid f` with inverses.
- **`ImageGroupLeftHom`**: The corestriction `f.Dom -> ImageGroup f`.
- **`ImageGroupRightHom`**: The inclusion `ImageGroup f -> f.Cod`.
- **`ImageSubGroup`**: The image of `f` packaged as a `SubGroup f.Cod`, with elements of `f.Cod` admitting a preimage under `f`.

#### Additive Group Category

- **`AddGroupCat`**: The category of additive groups (`AddGroup`) with `AddGroupHom` morphisms.
- **`AddGroupCat.forgetToAddMonoid`**: Forgetful functor `AddGroupCat -> AddMonoidCat`.
- **`AddGroupCat.forget`**: Forgetful functor `AddGroupCat -> SetCat`.

#### Abelian Group Category

- **`AbGroupCat`**: The category of abelian groups, defined as a `subCat` of `AddGroupCat` via the obvious embedding.
- **`AbGroupCat.forgetToAddGroup`**: Forgetful functor `AbGroupCat -> AddGroupCat`.
- **`AbGroupCat.forget`**: Forgetful functor `AbGroupCat -> SetCat`.

#### Kernels of Additive Homomorphisms

- **`KerAddGroup`**: The kernel of an `AddGroupHom f` as an `AddGroup`, extending `KerAddMonoid` with negation.
- **`KerGroupHom`**: The canonical inclusion `KerAddGroup f -> f.Dom` as an `AddGroupHom`.
- **`KerAbGroup`**: The kernel of `f : A -> _` as an `AbGroup` when `A` is abelian.
- **`kernel=0<->inj`**: An additive homomorphism is injective iff every kernel element equals zero.

#### Images of Additive Homomorphisms

- **`ImageAddGroup`**: The image of an `AddGroupHom` as an `AddGroup`, extending `ImageAddMonoid` with negation.
- **`ImageAddGroupLeftHom`**: The corestriction `f.Dom -> ImageAddGroup f`.
- **`ImageAddGroupRightHom`**: The inclusion `ImageAddGroup f -> f.Cod`.
- **`ImageAbGroup`**: The image as an `AbGroup` when the codomain is abelian.

#### Conjugation

- **`conjugateHom`**: For `g : E` in a group, the inner automorphism `GroupHom E E` given by `x |-> g x g^{-1}`.
