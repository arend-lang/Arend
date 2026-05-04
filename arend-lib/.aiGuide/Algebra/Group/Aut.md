### Algebra.Group.Aut

Automorphism groups: the group of self-paths at a point and the monoid of endomorphisms in a precategory.

#### Group of Self-Paths

- **`Aut`**: `Group` instance on `Trunc0 (a = a)`, the set-truncation of self-identifications of a point `a : A`. Identity is `in0 idp`, multiplication is path concatenation `*>` lifted through the truncation, and inversion is path inversion `inv`. Provides the fundamental group at `a` (as a set).

#### Endomorphism Monoid

- **`End`**: `Monoid` instance on `Hom c c` for an object `c` of a `Precat`. Identity is the categorical identity `id c`, multiplication is composition `∘`, with unit and associativity laws inherited from the precategory structure.
