### Algebra.Group.GroupCat

Category-theoretic structure on groups, abelian groups, and additive groups.

This module assembles the categories `GroupCat`, `AddGroupCat`, and `AbGroupCat`, with `AbGroupCat` realized as a subcategory of `AddGroupCat` via an embedding. It characterizes isomorphisms in `GroupCat` as injective+surjective homomorphisms by transferring to set-isomorphisms in `SetCat`. Forgetful functors to `SetCat`, `AddMonoidCat`, and `AddGroupCat` are also provided.

#### Group Category

- **`GroupCat`**: The category of groups with `GroupHom` as morphisms; instance of `Cat Group`.
- **`GroupCat.ForgetSet`**: The forgetful functor `GroupCat -> SetCat`, shown to be faithful.

#### Characterizations of Isomorphisms in `GroupCat`

- **`GroupCat.Iso<->Inj+Surj`**: A group homomorphism `f : G -> H` is an iso in `GroupCat` iff it is both injective and surjective (`f.IsIsomorphism`).
- **`Iso<->Inj+Surj.this_iso`**, **`inve`**: Builds the inverse set-isomorphism from the injective+surjective data.
- **`Iso<->Inj+Surj.this_inv_ap`**, **`inv_this_ap`**: The inverse equation `h = f (inve p h)` and `g = inve p (f g)`.
- **`Iso<->Inj+Surj.SetIso->Inj+Surj`**: Conversion between set-isomorphism data and injectivity+surjectivity.

#### Additive Group Category

- **`AddGroupCat`**: The category of additive groups (`AddGroup`) with `AddGroupHom` morphisms.
- **`AddGroupCat.forgetToAddMonoid`**: Forgetful functor `AddGroupCat -> AddMonoidCat`.
- **`AddGroupCat.forget`**: Forgetful functor `AddGroupCat -> SetCat`.

#### Abelian Group Category

- **`AbGroupCat`**: The category of abelian groups, defined as a `subCat` of `AddGroupCat` via the obvious embedding.
- **`AbGroupCat.forgetToAddGroup`**: Forgetful functor `AbGroupCat -> AddGroupCat`.
- **`AbGroupCat.forget`**: Forgetful functor `AbGroupCat -> SetCat`.
