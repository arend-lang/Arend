### Algebra.Pointed.Sub

Sub-pointed-sets: subsets of pointed (or additively pointed) sets that are closed under the distinguished element.

This module defines the notion of a subset of a pointed set that contains the basepoint, yielding an induced pointed structure on the subset itself. The multiplicative variant (`SubPointed`) carries `1`, while the additive variant (`SubAddPointed`) carries `0`; each provides a constructor (`IPointed` / `IAddPointed`) that promotes the underlying `ISet` of the `SubSet` to a `Pointed`/`AddPointed` by pairing the basepoint with its closure proof. The `max` constructions give the trivial maximal sub-pointed-set (the whole ambient set), and `DecSubPointed` adds decidability of membership.

#### Multiplicative Sub-Pointed Sets

- **`SubPointed`**: Class extending `SubSet` with `S : Pointed`, requiring `contains_ide : contains 1`. Represents a subset of a pointed set that is closed under the unit element.
- **`SubPointed.IPointed`**: Promotes the induced `ISet` of the subset to a `Pointed` structure, with unit `(1, contains_ide)`.
- **`SubPointed.max`**: The maximal `SubPointed` over a `Pointed A`, built on top of `DecSubSet.max` (the full subset), with the basepoint trivially contained.
- **`DecSubPointed`**: Class extending both `SubPointed` and `DecSubSet`; a sub-pointed-set with decidable membership.

#### Additive Sub-Pointed Sets

- **`SubAddPointed`**: Class extending `SubSet` with `S : AddPointed`, requiring `contains_zro : contains 0`. The additive analogue of `SubPointed`, for subsets closed under the zero element.
- **`SubAddPointed.IAddPointed`**: Promotes the induced `ISet` to an `AddPointed` structure, with zero `(0, contains_zro)`.
- **`SubAddPointed.max`**: The maximal `SubAddPointed` over an `AddPointed A`, built on `DecSubSet.max`, with zero trivially contained.
