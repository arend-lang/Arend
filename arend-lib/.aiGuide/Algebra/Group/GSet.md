### Algebra.Group.GSet

Group actions (G-sets): sets equipped with a compatible group action, including transitive actions and the canonical action on cosets of a subgroup.

#### Group Action Classes

- **`GroupAction`**: Class extending `BaseSet` for a left action of a group `G` on the carrier `E`. Provides the action operator `**` along with associativity (`**-assoc`: `m ** (n ** e) = (m * n) ** e`) and identity (`id-action`: `ide ** e = e`) laws.
- **`G`**: The acting group.
- **`**`**: The action operator `G -> E -> E` (infix level 8).
- **`**-assoc`**: Compatibility of the action with group multiplication.
- **`id-action`**: The identity element acts trivially.
- **`TransitiveGroupAction`**: Class extending `GroupAction` with the transitivity axiom `isTransAction`: for any two points `v v' : E`, there merely exists `g : G` with `g ** v = v'`.

#### Action on Cosets

- **`ActionBySubgroup`**: Given a subgroup `H : SubGroup G`, constructs the canonical `GroupAction G` on the set of cosets `H.Cosets` via left multiplication.
- **`ActionBySubgroup.trivialRelation`**: Algebraic lemma `g * x * (inverse x * y) = g * y` used to verify well-definedness of the action on cosets.
- **`TransitiveActionBySubgroup`**: Instance promoting `ActionBySubgroup H` to a `TransitiveGroupAction`, since `G` acts transitively on its coset space.
