### Algebra.Group.GSet.ExampleActions

Concrete examples of group actions used throughout the library.

This module collects standard constructions of `GroupAction` instances that appear repeatedly in group theory: a group acting on itself by left translation, on its subsets, and on itself by conjugation, plus the trivial action. These examples serve as the canonical building blocks for proofs about transitive actions, orbits, stabilizers, and conjugacy classes, instantiating the abstract `GroupAction`/`TransitiveGroupAction` interfaces with the most common concrete cases.

#### Standard Actions

- **`TranslationAction`**: Left translation action of a group `G` on itself, where `g ** h = g * h`. Constructs a `TransitiveGroupAction G` since any element can be sent to any other via left multiplication.
- **`TranslationActionOnSubsets`**: Action of `G` on `SubSet G` defined by `(g ** p).contains h = p.contains (g⁻¹ * h)`, i.e., translating a subset by `g`. Yields a `GroupAction G`.
- **`conjAction`**: Action of `G` on itself by conjugation: `g ** e = conjugate g e = g * e * g⁻¹`. Yields a `GroupAction G` whose orbits are conjugacy classes.
- **`trivialAction`**: Trivial action of `G` on any `BaseSet E`, where every group element acts as the identity. Useful as a default instance and for constructing fixed-point sets.

#### Subset Operations

- **`conjugate-subset`**: Conjugation of a subset `S : SubSet G` by `g : G`, defined by `h ∈ conjugate-subset g S` iff `g⁻¹ * h * g ∈ S`. Used in normalizer/centralizer constructions and reasoning about conjugacy of subgroups.
