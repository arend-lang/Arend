### Equiv.Univalence

Univalence: equivalences between types correspond to identifications of types.

This module establishes the univalence principle by constructing a `QEquiv` between the path type `A = B` and the type of equivalences `Equiv {A} {B}`. The construction goes both directions: a path induces an equivalence via `transport`, and an equivalence induces a path via Arend's native `iso` constructor for the path type. The retraction property is verified using path induction (`Jl`), and the full equivalence is assembled using `pathEquiv`, exploiting the fact that `=-to-Equiv` already coincides with `coe` along paths.

#### Path-to-Equivalence Direction

- **`=-to-Equiv`**: Converts a type equality `p : A = B` into an `Equiv {A} {B}` by rewriting along `p` and using the identity equivalence.
- **`=-to-QEquiv`**: Converts `p : A = B` into a `QEquiv {A} {B}` with explicit components: forward map is `transport (\lam X => X) p`, retraction is `transport` along `inv p`, with both round-trips proven via `transport_*>` and the path inverse laws.

#### Equivalence-to-Path Direction

- **`QEquiv-to-=`**: Builds a path `A = B` from a `QEquiv {A} {B}` using the primitive `iso` constructor (which packages forward map, inverse, and both homotopies into a path).
- **`Equiv-to-=`**: Builds a path `A = B` from a general `Equiv {A} {B}` by first promoting it to a `QEquiv` via `QEquiv.fromEquiv`.

#### Univalence

- **`univalence`**: The univalence equivalence `QEquiv {X = Y} {Equiv {X} {Y}}`. Constructed via `pathEquiv` by providing, for each `A B`, a `Retraction` whose forward map is `=-to-Equiv` and section is `Equiv-to-=`; the section property reduces by path induction to the fact that `=-to-Equiv idp` agrees pointwise with `coe` at the right endpoint.
