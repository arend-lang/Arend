### Algebra.Group.GSet.GSetCat

The category of `G`-sets and equivariant maps.

- **`GSetCategory`**: Instance making `GSet G` into a category `Cat`. Morphisms are `EquivariantMap A B`, identity is `EquivariantMap.id`, and composition is `EquivariantMap.∘`; the category laws hold by `idp` and `univalence` is proved with `sip` from `func-**` (the action-preservation of an equivariant equivalence).
