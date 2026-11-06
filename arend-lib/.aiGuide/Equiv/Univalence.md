### Equiv.Univalence

Univalence: equivalences between types correspond to identifications of types.

This module establishes the univalence principle by constructing a `QEquiv` between the path type `A = B` and the type of equivalences `Equiv A B`. A path induces an equivalence via `transport`, and an equivalence induces a path via `QEquiv_=` (built on Arend's native `iso` constructor for the path type). The section property is verified using path induction (`Jl`), and the full equivalence is assembled using `pathEquiv`, exploiting the fact that `=_Equiv` already coincides with `coe` along paths.

#### Path-to-Equivalence Direction

- **`=_Equiv`**: Converts a path `p : A = B` into an equivalence `Equiv A B` by transporting the identity equivalence along `p`.

#### Equivalence-to-Path Direction

- **`Equiv_=`**: Converts an equivalence `e : Equiv A B` into a path `A = B`, by passing to its `QEquiv` and applying `QEquiv_=`.

#### Univalence

- **`typeUnivalence`**: The univalence equivalence `QEquiv {A = B} {Equiv A B}` with forward map `=_Equiv` and inverse `Equiv_=`. Constructed via `pathEquiv` with a `Retraction` whose section property reduces by path induction (`Jl`) to the fact that `=_Equiv idp` agrees pointwise with `coe` at the right endpoint.
