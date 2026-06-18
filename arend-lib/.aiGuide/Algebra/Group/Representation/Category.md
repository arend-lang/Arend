### Algebra.Group.Representation.Category

The category of linear representations of a group `G` over a ring `R`, with intertwining maps as morphisms.

This module assembles the categorical structure on `LinRepres R G`: morphisms are `R`-linear maps that commute with the `G`-action (intertwiners), and the category inherits identity and composition from `LinearMap`. Kernels and images of intertwining maps carry induced representation structures, lifting the corresponding `LModule`-level constructions to the equivariant setting. When `R` is commutative, the hom-sets `InterwiningMap A B` themselves form an `R`-module, giving the category an enriched structure suitable for representation-theoretic calculations.

#### Morphisms and Category

- **`InterwiningMap`**: Record extending `LinearMap` with the equivariance condition `func (g ** e) = g ** func e`; the morphisms of the representation category. Domain and codomain are overridden to be `LinRepres R G`.
- **`RepresentationCat`**: Instance of `Cat (LinRepres R G)` with `InterwiningMap` as homs, identity `id-interwining`, and composition built from `LinearMap` composition extended with the equivariance proof.
- **`id-interwining`**: The identity intertwining map on a representation `X`, built from `LinearMap.id`.

#### Isomorphisms

- **`repr+module-iso=>repr-iso`**: Promotes a module-level isomorphism `Iso {LModuleCat R} f` to an isomorphism in the representation category, showing that the inverse linear map automatically intertwines the `G`-action.
- **`inverseMap`** (in `\where`): Constructs the inverse intertwiner from the module-level inverse, deriving equivariance by transporting the equivariance of `f` across the iso witnesses.
- **`aux`**, **`aux-2`**: Pointwise extraction of the iso identities `f ∘ p.hinv = id` and `p.hinv ∘ f = id`.

#### Kernels

- **`KerLRepres`**: The kernel of an intertwining map `f : A → B` as a representation; underlying module is `KerLModule f`, and the `G`-action restricts because `f` is equivariant and the action preserves zero.
- **`KerLRepresHom`**: The canonical inclusion `KerLRepres f → A` as an intertwining map.

#### Images

- **`ImageLRepres`**: The image of an intertwining map `f : A → B` as a representation; underlying module is `ImageLModule f`, and the `G`-action on `B` preserves the image because preimages can be transported by the equivariance of `f`.
- **`ImageLRepresRightHom`**: The canonical inclusion `ImageLRepres f → Cod f` as an intertwining map.
- **`ImageLRepresLeftHom`**: The canonical surjection `Dom f → ImageLRepres f` as an intertwining map.

#### Module Structure on Hom-Sets

- **`InterwiningMapLModule`**: For commutative `R`, equips `InterwiningMap A B` with an `R`-module structure (zero, addition, negation, scalar multiplication), making the representation category `R`-linear.
- **`zeroInterwining`**: The zero intertwiner, built from the zero linear map.
- **`addInterwining`**: Pointwise sum of two intertwiners.
- **`negativeInterwining`**: Pointwise negation of an intertwiner.
- **`mulconstInterwining`**: Scalar multiplication of an intertwiner by `c : R`.
