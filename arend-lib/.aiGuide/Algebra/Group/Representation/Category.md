### Algebra.Group.Representation.Category

The category of linear representations of a group over a ring, with intertwining maps as morphisms, including kernel/image constructions and the module structure on hom-sets.

#### Morphisms

- **`InterwiningMap`**: Class extending `LinearMap` between two linear representations `Dom Cod : LinRepres R G` of the same group, equipped with the equivariance condition `func-**`: `func (g ** e) = g ** func e`.

#### The Category

- **`RepresentationCat`**: Instance `Cat (LinRepres R G)` whose hom-sets are `InterwiningMap`s, with identity, composition (preserving equivariance via `rewrite` on `func-**`), and `univalence` proven by `sip`.
- **`RepresentationCat.id-interwining`**: Identity intertwining map on a representation, built from `LinearMap.id`.

#### Isomorphisms

- **`repr+module-iso=>repr-iso`**: Promotes an iso `f` in `LModuleCat R` to an iso in `RepresentationCat`, showing that a module-level isomorphism between representations is automatically an intertwining isomorphism.
- **`repr+module-iso=>repr-iso.inverseMap`**: The inverse intertwining map, with equivariance derived from that of `f`.
- **`repr+module-iso=>repr-iso.aux`**, **`aux-2`**: Pointwise reformulations of `f_hinv` and `hinv_f` from the underlying module iso.

#### Kernel

- **`KerLRepres`**: Kernel of an intertwining map `f : A -> B` as a representation; underlying `LModule` is `KerLModule f`, with `G`-action inherited from `A` (preservation uses `f.func-**` and `B.g**-zro`).
- **`KerLRepresHom`**: The canonical inclusion `KerLRepres f -> A` as an intertwining map.

#### Image

- **`ImageLRepres`**: Image of `f : A -> B` as a representation; underlying `LModule` is `ImageLModule f`, with `G`-action inherited from `B` and the existence witness transported through `f.func-**`.
- **`ImageLRepresRightHom`**: The canonical intertwining map `ImageLRepres f -> Cod f`.
- **`ImageLRepresLeftHom`**: The canonical surjection `Dom f -> ImageLRepres f` as an intertwining map.

#### Module Structure on Hom-sets

- **`InterwiningMapLModule`**: For a commutative ring `R`, the `R`-module structure on `InterwiningMap A B`, with all axioms reduced pointwise to those of `B`.
- **`InterwiningMapLModule.zeroInterwining`**: Zero intertwining map; equivariance from `B.g**-zro`.
- **`InterwiningMapLModule.addInterwining`**: Pointwise sum of intertwining maps; equivariance from `B.**-ldistr`.
- **`InterwiningMapLModule.negativeInterwining`**: Pointwise negation; equivariance from `B.g**-negative`.
- **`InterwiningMapLModule.mulconstInterwining`**: Scalar multiplication by `c : R`; equivariance from `B.**-*c`.
