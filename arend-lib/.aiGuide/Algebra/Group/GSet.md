### Algebra.Group.GSet

Group actions on sets (G-sets) and their basic structural theory.

A `GroupAction` equips a set `E` with a left action `**` of a group `G` satisfying associativity and identity laws, making `E` a G-set. The module establishes the standard correspondence between G-sets and subgroups: every point has a `Stabilizer` subgroup, and conversely every subgroup `H` yields a canonical G-set on its coset space `H.Cosets` via left multiplication. Transitive actions are singled out as those where any two points are connected by some group element, and the coset action is shown to always be transitive — reflecting the fact that transitive G-sets are classified (up to choice of basepoint) by subgroups of `G`.

#### Group Actions

- **`GroupAction`**: Class extending `BaseSet` with a group `G` acting on the carrier `E`. Provides the action operator `**`, associativity `**-assoc` (`m ** (n ** e) = (m * n) ** e`), and identity law `id-action` (`ide ** e = e`).
- **`**`**: Infix action operator `G -> E -> E` (precedence 8).
- **`Stabilizer`**: Given a point `s : E`, the subgroup `{m : G | m ** s = s}` of group elements fixing `s`.
- **`choosePoint`**: Realizes a G-set via cosets of a stabilizer — given a point `e`, returns the coset action of `Stabilizer e`.

#### Transitive Actions

- **`TransitiveGroupAction`**: Class extending `GroupAction` with the transitivity axiom `isTransAction`: for any two points `v v' : E`, there merely exists `g : G` with `g ** v = v'`.

#### Coset Actions

- **`ActionBySubgroup`**: For any subgroup `H ≤ G`, constructs the canonical G-action on the coset space `H.Cosets` by left multiplication `g ** [a] = [g * a]`. Well-definedness on equivalence classes uses `H`'s right-invariance.
- **`ActionBySubgroup.trivialRelation`**: Helper identity `g * x * (inverse x * y) = g * y`, used to show the coset action respects the coset equivalence relation.
- **`TransitiveActionBySubgroup`**: Instance promoting `ActionBySubgroup H` to a `TransitiveGroupAction` — the action of `G` on `H.Cosets` is always transitive, since any two cosets differ by a group element.
