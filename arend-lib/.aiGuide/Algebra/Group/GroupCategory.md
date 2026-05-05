### Algebra.Group.GroupCategory

Category structures on groups, abelian groups, and additive groups, along with kernel/image constructions and characterizations of isomorphisms.

#### Group Category

- **`GroupCat`**: The category `Cat Group` with `GroupHom` morphisms, identity and composition from `GroupHom`, and univalence via `sip`.
- **`GroupCat.ForgetSet`**: The faithful forgetful functor from `GroupCat` to `SetCat`.
- **`GroupCat.Iso<->Inj+Surj`**: A group homomorphism is a categorical iso iff it is injective and surjective (i.e., `f.IsIsomorphism`).
- **`GroupCat.Iso<->TrivialKer+Surj`**: A group homomorphism is a categorical iso iff it has trivial kernel and is surjective.

#### Quotient and Image Constructions (Group)

- **`quotient-map`**: The canonical projection `GroupHom S H.quotient` for a normal subgroup `H` of `S`.
- **`ImageGroup`**: The image of a `GroupHom` as a `Group`, extending `ImageMonoid` with inverses.
- **`ImageGroupLeftHom`**: The corestriction `f.Dom -> ImageGroup f`.
- **`ImageGroupRightHom`**: The inclusion `ImageGroup f -> f.Cod`.
- **`ImageSubGroup`**: The image of a `GroupHom` as a `SubGroup` of the codomain, with membership predicate `∃ g, f g = h`.

#### Additive Group Category

- **`AddGroupCat`**: The category `Cat AddGroup` with `AddGroupHom` morphisms and univalence via `sip`.
- **`AddGroupCat.forgetToAddMonoid`**: The forgetful functor `AddGroupCat -> AddMonoidCat`.
- **`AddGroupCat.forget`**: The forgetful functor `AddGroupCat -> SetCat`.

#### Abelian Group Category

- **`AbGroupCat`**: The category `Cat AbGroup`, defined as a full subcategory of `AddGroupCat` via an embedding.
- **`AbGroupCat.forgetToAddGroup`**: The forgetful functor `AbGroupCat -> AddGroupCat`.
- **`AbGroupCat.forget`**: The forgetful functor `AbGroupCat -> SetCat`.

#### Kernel Constructions (Additive)

- **`KerAddGroup`**: The kernel of an `AddGroupHom` as an `AddGroup`, extending `KerAddMonoid` with negation.
- **`KerGroupHom`**: The canonical inclusion `KerAddGroup f -> f.Dom`.
- **`KerAbGroup`**: The kernel of a homomorphism into an abelian group, as an `AbGroup`.
- **`kernel=0<->inj`**: An additive group homomorphism is injective iff its kernel is trivial.

#### Image Constructions (Additive)

- **`ImageAddGroup`**: The image of an `AddGroupHom` as an `AddGroup`, extending `ImageAddMonoid` with negation.
- **`ImageAddGroupLeftHom`**: The corestriction `f.Dom -> ImageAddGroup f`.
- **`ImageAddGroupRightHom`**: The inclusion `ImageAddGroup f -> f.Cod`.
- **`ImageAbGroup`**: The image of a homomorphism into an abelian group, as an `AbGroup`.

#### Conjugation

- **`conjugateHom`**: For `g : E`, the inner automorphism `GroupHom E E` given by `x |-> g * x * g^{-1}` (via `conjugate g`).
