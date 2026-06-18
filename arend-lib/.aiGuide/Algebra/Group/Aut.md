### Algebra.Group.Aut

Automorphism and endomorphism structures arising from identity types and category-theoretic hom-sets.

This module packages two canonical algebraic structures induced by identification: the group of (set-truncated) self-identifications of a point in a type, and the monoid of endomorphisms of an object in a precategory. The `Aut` instance uses `Trunc0` to set-truncate the loop space `a = a`, ensuring the resulting structure is a (1-)group rather than a higher groupoid, with composition given by path concatenation lifted through the truncation. The `End` construction reuses categorical composition `∘` and identity `id` to expose `Hom c c` as a monoid, providing a uniform bridge between category theory and monoid theory.

#### Group and Monoid Structures

- **`Aut`**: Instance giving `Trunc0 (a = a)` the structure of a `Group` for any point `a : A`. The unit is `in0 idp`, multiplication is induced from path concatenation `*>` lifted through `Trunc0.map`, and inversion comes from path inversion `inv`. Models the fundamental group / automorphism group of a point in a type.
- **`End`**: Function producing a `Monoid` on `Hom c c` for an object `c` in a precategory `C`. Identity is `id c` and multiplication is categorical composition `∘`, exhibiting endomorphisms of any object as a monoid.
