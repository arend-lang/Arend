### Algebra.Group.GSet.ExampleActions

Concrete examples of group actions: left translation, action on subsets, conjugation, and the trivial action.

#### Group Actions

- **`TranslationAction`**: The left-translation action of a group `G` on itself, given by `g ** v = g * v`. Constructed as a `TransitiveGroupAction` since for any `v, v'` the element `v' * inverse v` sends `v` to `v'`.
- **`TranslationActionOnSubsets`**: The induced action of `G` on `SubSet G`, where `g ** p` is the subset `{ h | inverse g * h ∈ p }` (i.e. left-translation of subsets).
- **`conjAction`**: The conjugation action of `G` on itself: `g ** h = conjugate g h`. Reuses the group's own `**-assoc` and `id-action` proofs.
- **`trivialAction`**: The trivial action of `G` on any `BaseSet E`, where every group element acts as the identity (`g ** e = e`).

#### Subset Operations

- **`conjugate-subset`**: Given `g : G` and `S : SubSet G`, produces the conjugated subset `{ h | conjugate (inverse g) h ∈ S }`.
