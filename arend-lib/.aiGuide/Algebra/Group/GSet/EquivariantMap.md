### Algebra.Group.GSet.EquivariantMap

- **`EquivariantMap`**: Record extending `SetHom` for a fixed group `G`, with both domain and codomain refined to `GSet G`. Adds the field `func-**` requiring `func (g ** e) = g ** func e`, i.e. compatibility with the action.
