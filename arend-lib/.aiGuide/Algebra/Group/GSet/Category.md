### Algebra.Group.GSet.Category

The category of G-sets (group actions of a fixed group G) with equivariant maps as morphisms.

#### Equivariant Maps

- **`EquivariantMap`**: Class extending `SetHom` for maps between `GroupAction G` instances that commute with the action: `func (g ** e) = g ** func e`. Carries the group `G` and overrides the domain/codomain to be `GroupAction G`.
- **`id-equivar`**: The identity equivariant map `X -> X` on a `GroupAction G`.

#### Category Structure

- **`GSet`**: Instance of `Cat` whose objects are `GroupAction G` and whose morphisms are `EquivariantMap G`. Identity is `id-equivar`; composition is built by composing underlying functions and chaining the equivariance proofs via `pmap` and `func-**`. Satisfies `id-left`, `id-right`, and `o-assoc` definitionally, and `univalence` via `sip` (structure identity principle), where equality of group actions reduces to the underlying set equality preserving `**`.
