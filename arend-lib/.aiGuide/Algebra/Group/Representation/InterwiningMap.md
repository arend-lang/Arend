### Algebra.Group.Representation.InterwiningMap

- **`InterwiningMap`**: Record extending both `LinearMap` and `EquivariantMap`, i.e. an `R`-linear map commuting with the `G`-action (`func (g ** e) = g ** func e`); the morphisms of the representation category. Domain and codomain are overridden to be `LinRepres R G`.
