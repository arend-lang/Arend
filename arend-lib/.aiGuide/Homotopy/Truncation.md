### Homotopy.Truncation

Definitions for n-truncated types and the higher inductive truncation operator.

This module formalizes the notion of (-1+n)-truncated types — those whose homotopy structure is trivial above level n — together with a higher inductive type `Trunc_-1+` that freely truncates an arbitrary type to a given level. The truncation is built using `hubT`/`spokeT` constructors indexed over spheres, following the standard HoTT construction that fills any sphere of dimension ≥ n. The induction principle is established by reducing the spoke case to the contractibility of based loop spaces in n-truncated targets, and the encode-decode method is used to characterize the path space `inT a = inT a'` of the (n+1)-truncation as the n-truncation of `a = a'`.

#### Truncation Predicate

- **`Truncated_-1+`**: Class of types `A` equipped with a proof `isTruncated : A ofHLevel_-1+ n` that `A` is (-1+n)-truncated.
- **`Truncated_-1+.ext`**: Equality of `Truncated_-1+` records reduces to equality of the underlying types (the truncation proof is propositional).
- **`Truncated_-1+.equiv`**: Promotes `ext` to a `QEquiv` between `t = t'` and `t.A = t'.A`.
- **`Truncated_-1+.up`**: Raises the truncation level: an n-truncated type is also (n+1)-truncated.

#### Truncation Instances

- **`truncatedEquiv`**: The type of equivalences between two n-truncated types is itself n-truncated.
- **`truncatedTypesLevel`**: The universe of n-truncated types is (n+1)-truncated.

#### Truncation HIT

- **`Trunc_-1+`**: Higher inductive type freely n-truncating a type `A`. Constructors:
  - **`inT`**: Inclusion `A -> Trunc_-1+ n A`.
  - **`hubT`**: Hub for any map `Sphere n -> Trunc_-1+ n A`, used to fill spheres.
  - **`spokeT`**: Spoke connecting each point of the sphere to the hub, witnessing n-truncation.

#### Truncation Eliminators and Path Space

- **`Trunc_-1+.level`**: Instance witnessing that `Trunc_-1+ n A` is itself n-truncated.
- **`Trunc_-1+.elim`**: Dependent eliminator into a family of n-truncated types: extends a function on `inT` to all of `Trunc_-1+ n A`, with the spoke case discharged via contractibility of based loop spaces (`SphereLoopEquiv`, `loop-level-iter-inv`).
- **`Trunc_-1+.elim2`**: Two-argument version of `elim`, eliminating into a binary family of n-truncated types.
- **`Trunc_-1+.equality`**: Encode-decode characterization of paths in the (n+1)-truncation: `inT a = inT a'` in `Trunc_-1+ (suc n) A` is equivalent to `Trunc_-1+ n (a = a')`. Uses `elim2` to construct the code family, encode/decode maps, and a section proof via path induction.
