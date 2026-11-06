### Algebra.Group.Representation.LinRepresCat

The category of linear representations of a group with intertwining maps as morphisms, and the module structure on its hom-sets.

#### Category of representations

- **`RepresentationCat`**: the category whose objects are linear representations `LinRepres R G` of a group `G` over a ring `R` and whose morphisms are intertwining maps `InterwiningMap A B`, with identity and composition of intertwiners; univalence holds because an equivalence of representations is determined by its underlying structure-preserving map.

#### Hom-modules

- **`InterwiningMapLModule`**: For commutative `R`, equips `InterwiningMap A B` with an `R`-module structure (zero, addition, negation, scalar multiplication), making the representation category `R`-linear.
- **`InterwiningMapLModule.zeroInterwining`**: The zero intertwiner, built from the zero linear map.
- **`InterwiningMapLModule.addInterwining`**: Pointwise sum of two intertwiners.
- **`InterwiningMapLModule.negativeInterwining`**: Pointwise negation of an intertwiner.
- **`InterwiningMapLModule.mulconstInterwining`**: Scalar multiplication of an intertwiner by `c : R`.
