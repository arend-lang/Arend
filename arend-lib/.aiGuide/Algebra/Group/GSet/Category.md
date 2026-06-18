### Algebra.Group.GSet.Category

The category of G-sets (group actions of a fixed group `G`) with equivariant maps as morphisms.

This module packages group actions for a fixed group `G` into a category, where morphisms are functions between underlying sets that commute with the action. The equivariance condition `f (g ** e) = g ** f e` is encoded as an extra field on top of `SetHom`, and the categorical structure (identity, composition, associativity, univalence) follows pointwise from the underlying set category. This provides the standard categorical setting for studying representations and actions of `G`.

#### Equivariant Maps

- **`EquivariantMap`**: Record extending `SetHom` for a fixed group `G`, with both domain and codomain refined to `GroupAction G`. Adds the field `func-**` requiring `func (g ** e) = g ** func e`, i.e. compatibility with the action.
- **`EquivariantMap.IsIso`**: Predicate stating that the underlying function is both surjective and injective, packaged as `\Sigma (IsSurj func) (IsInj func)`.

#### Identity and Composition

- **`id-equivar`**: The identity equivariant map `X -> X` on a `GroupAction G`, given by the identity function with trivial equivariance proof.

#### Category Instance

- **`GSet`**: Instance making `GroupAction G` into a category `Cat`. Morphisms are `EquivariantMap G A B`, identity is `id-equivar`, and composition is the set-theoretic composition with equivariance derived by chaining `func-**` of the two maps via `pmap`. The category laws (`id-left`, `id-right`, `o-assoc`) and `univalence` follow from the underlying set structure.
