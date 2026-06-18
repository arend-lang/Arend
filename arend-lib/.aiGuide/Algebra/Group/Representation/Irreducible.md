### Algebra.Group.Representation.Irreducible

Defines irreducible linear representations of a group and proves Schur's Lemma for homomorphisms between them.

A linear representation is irreducible when every sub-representation is trivial (either zero or the whole space). The module formalizes this as a universal property over `SubLRepres` and uses it to derive Schur's Lemma: any intertwining map between irreducible representations is either zero or an isomorphism. The proof analyzes the kernel and image as sub-representations, leveraging the fact that an intertwining map with zero kernel and full image is an isomorphism in both the module and representation categories.

#### Irreducibility

- **`Irreducible`**: Predicate `\Pi (A : SubLRepres E) -> A.isTrivial` asserting that every sub-representation of a linear representation `E : LinRepres R G` is trivial. Captures the mathematical notion of an irreducible (simple) representation.

#### Schur's Lemma

- **`Hom-between-Irreducible`**: Class parameterized by two irreducible linear representations `A` and `B` (with proofs `p : Irreducible A` and `q : Irreducible B`), packaging the context for reasoning about intertwining maps between them.
- **`Schur's-Lemma`**: For any intertwining map `f : InterwiningMap A B`, produces `IsZeroMap f || Iso f`. Proceeds by case analysis on the image sub-representation of `B` (trivial via `q`): if the image is zero, `f` is the zero map (`ZeroIm=>Zero-func`); if the image is full, case-splits on the kernel sub-representation of `A` (trivial via `p`) — a zero kernel combined with full image yields a representation isomorphism via `iso<->zeroKer-FullIm` and `repr+module-iso=>repr-iso`, while a full kernel forces `f` to be zero (`FullKer=>Zero-func`).
