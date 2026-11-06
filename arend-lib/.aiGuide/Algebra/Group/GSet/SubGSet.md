### Algebra.Group.GSet.SubGSet

Sub-G-sets (invariant subsets of a G-set), the induced action on them, and the image of an equivariant map as a G-set.

A `SubGSet` is a subset of a `GSet` closed under the group action. Such a subset is itself a G-set (`IGSet`), with an equivariant inclusion back into the ambient set. The module then applies this to the image of an equivariant map: the image is an invariant subset of the codomain, and the map factors equivariantly through it as a map onto the image followed by the inclusion.

#### Invariant Subsets

- **`SubGSet`**: Class of subsets of a G-set `S` (for a group `G`) that are closed under the action: `contains a` implies `contains (x ** a)`.
- **`IGSet`**: Instance making the subset of a `SubGSet` into a `GSet` over the same group, with the action acting on the underlying element and the closure proof carried along.

#### Image of an Equivariant Map

- **`ImageGSet`**: The image of an equivariant map `f` as a `SubGSet` of its codomain: `y` belongs to it when `∃ x, f x = y`.
- **`ImGSet`**: The G-set underlying `ImageGSet f`, i.e. the image of `f` with the induced action.
- **`ImGSetRightHom`**: The inclusion of the image `ImGSet f` into the codomain of `f`, as an equivariant map.
- **`ImGSetLeftHom`**: The equivariant map from the domain of `f` onto its image `ImGSet f`, sending `x` to `f x`.
