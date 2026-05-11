### Algebra.Ring.Sub

Subrings of (pseudo) rings, packaged as predicates closed under the ring operations, together with the induced ring structure on the carrier.

A `SubPseudoRing` is the predicate-style subobject of a `PseudoRing`, obtained by combining `SubPseudoSemiring` with `SubAddGroup` so that closure under addition, multiplication, and negation is required. `SubRing` adds closure under the multiplicative identity. The induced ring structure is built by reusing the `SubAddGroup` abelian-group structure for `+` and the `SubSemiring` data for `*`, and the commutative variants (`cStruct`, `CSubRing`) lift commutativity from the ambient ring. Ring homomorphisms factor through subrings via `corestrict`/`embed`, and `ringHomImage` exhibits the image of a `RingHom` as a subring of the codomain.

#### Subring Classes

- **`SubPseudoRing`**: Subobject of a `PseudoRing`, extending `SubPseudoSemiring` and `SubAddGroup`. Overrides `S` to a `PseudoRing` and packages the closure conditions for `0`, `+`, `*`, and negation.
- **`SubPseudoRing.IPseudoRing`**: The induced `PseudoRing` on the carrier subset, combining `IPseudoSemiring` with the abelian-group structure from `SubAddGroup.abStruct`.
- **`SubPseudoRing.cStruct`**: Given a `SubPseudoRing` of a `PseudoCRing`, produces a `PseudoCRing` on the carrier by transferring `*-comm` from the ambient ring.
- **`SubRing`**: Subobject of a `Ring`, extending `SubPseudoRing` and `SubSemiring`. Overrides `S` to a `Ring`, additionally requiring closure under the multiplicative identity.
- **`SubRing.IRing`**: The induced `Ring` structure on the carrier, combining `ISemiring` with `SubAddGroup.abStruct`.
- **`CSubRing`**: A `SubRing` of a `CRing`, exposing the induced commutative ring `ICRing` on the carrier with `*-comm` lifted from `S`.
- **`CSubRing.ICRing`**: The induced commutative ring on a `CSubRing`'s carrier.

#### Homomorphisms To and From Subrings

- **`SubRing.corestrict`**: Given `f : RingHom R S` whose image lies in the subring (`\Pi (x : R) -> contains (f x)`), produces the corestricted `RingHom R IRing`.
- **`SubRing.embed`**: The canonical inclusion `RingHom IRing S` sending `(x, _)` to `x`.

#### Constructions on `SubRing`

- **`SubRing.cStruct`**: Given a `SubRing` of a `CRing`, produces a `CRing` on the carrier by combining `IRing` with `SubPseudoRing.cStruct`.
- **`SubRing.max`**: The maximal subring of a `Ring R`, containing every element; built from `SubSemiring.max` and `SubAddGroup.max`.
- **`SubRing.isContr`**: If the ambient ring `R` is contractible, so is the induced ring `S.IRing` of any subring.
- **`SubRing.comm-isContr`**: Commutative analogue: if `R : CRing` is contractible, so is the induced commutative ring `cStruct S`.

#### Image of a Ring Homomorphism

- **`ringHomImage`**: For `f : RingHom`, the image of `f` packaged as a `SubRing f.Cod`, with `contains y := ∃ (x : f.Dom) (f x = y)` and closure under `0`, `+`, `1`, `*`, and negation derived from `f` being a ring homomorphism.
