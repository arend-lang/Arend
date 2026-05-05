### Algebra.Group.Representation.Irreducible

Defines irreducible linear representations of a group and a structure for homomorphisms between them, the setting of Schur's lemma.

#### Irreducibility

- **`Irreducible`**: Predicate stating that a linear representation `E : LinRepres R G` is irreducible, i.e. every sub-representation `A : SubLRepres E` is trivial.

#### Morphisms Between Irreducibles

- **`Hom-between-Irreducible`**: Class bundling a ring `R`, group `G`, two linear `R`-representations `A` and `B` of `G`, together with proofs `p : Irreducible A` and `q : Irreducible B`. Provides the ambient data for stating and proving Schur-type results about homomorphisms between irreducible representations.
