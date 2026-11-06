### Algebra.Ring.SubRing

- **`SubPseudoRing`**: Subobject of a `PseudoRing`, extending `SubPseudoSemiring` and `SubAddGroup`. Overrides `S` to a `PseudoRing` and packages the closure conditions for `0`, `+`, `*`, and negation.
- **`SubRing`**: Subobject of a `Ring`, extending `SubPseudoRing` and `SubSemiring`. Overrides `S` to a `Ring`, additionally requiring closure under the multiplicative identity.
- **`SubRing.max`**: The maximal subring of a `Ring R`, containing every element; built from `SubSemiring.max` and `SubAddGroup.max`.

#### Induced Ring Structures

- **`IPseudoRing`**: The instance making a `SubPseudoRing` into a `PseudoRing` on its carrier, from `IPseudoSemiring` and the abelian-group structure `IAbGroup`.
- **`IPseudoCRing`**: The induced pseudo-ring of a sub-pseudo-ring of a commutative pseudo-ring is again commutative (`PseudoCRing`).
- **`IRing`**: The instance making a `SubRing` into a `Ring` on its carrier, from `ISemiring` and `IAbGroup`.
- **`ICRing`**: The induced ring of a subring of a commutative ring is again commutative (`CRing`).

#### Images of Ring Homomorphisms

- **`ImageRing`**: The image of a `RingHom f` as a `SubRing f.Cod`, built from `ImageSemiring` and `ImageAddGroup`.
- **`ImRing`**: The image of `f` regarded as a ring, `IRing (ImageRing f)`.
- **`ImRingLeftHom`**: The corestriction `RingHom f.Dom (ImRing f)` of `f` onto its image, built from `ImSemiringLeftHom` and `ImAddGroupLeftHom`.

#### Further definitions

- **`IRing.corestrict`**: Given `f : RingHom R S.S` whose image lies in the subring (`\Pi (x : R) -> S (f x)`), produces the corestricted `RingHom R (IRing S)`.
- **`IRing.embed`**: The canonical inclusion `RingHom (IRing S) S.S` sending `(x, _)` to `x`.
