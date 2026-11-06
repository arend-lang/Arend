### Algebra.Group.Representation.Irreducible

Defines irreducible linear representations of a group and proves Schur's Lemma for homomorphisms between them.

A linear representation is irreducible when every sub-representation is trivial (either zero or the whole space). The module formalizes this as a universal property over `SubLRepres` and uses it to derive Schur's Lemma: any intertwining map between irreducible representations is either zero or an isomorphism. The proof analyzes the kernel and image as sub-representations, leveraging the fact that an intertwining map with zero kernel and full image is an isomorphism in both the module and representation categories.

#### Irreducibility

- **`IsIrreducible`**: the proposition that a linear representation `E` is irreducible, i.e. every sub-representation `A : SubLRepres R G E` is trivial (`A.IsTrivial`).

#### Schur's Lemma

- **`SchurLemma`**: Schur's Lemma: if `A` and `B` are irreducible representations, then every intertwining map `f : InterwiningMap A B` is either zero (`f.IsZero`) or an equivalence (`IsEquiv f`).

