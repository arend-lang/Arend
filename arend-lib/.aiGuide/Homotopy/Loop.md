### Homotopy.Loop

Loop spaces of pointed types and their iterations, with connections to suspensions and spheres.

This module defines the loop space construction `Loop X = (base = base)` as a pointed type, providing the foundational building block for higher homotopy groups. The key results connect loop spaces to suspensions via the adjunction `(Susp A ->* B) = (A ->* Loop B)` and use this to derive that pointed maps from spheres correspond to iterated loops. Loop-level lemmas relate the h-level of a type to contractibility of its iterated loop spaces, mirroring the standard characterization of n-types via homotopy groups. The iterated loop space `Omega^ n` is defined by recursion on `n` using the generic iteration combinator.

#### Loop Space Construction

- **`Loop`**: Instance making `(base = base)` a `Pointed` type with `idp` as basepoint.
- **`Loop-Func`**: Functorial action on pointed maps: lifts `f : X ->* Y` to `Loop X ->* Loop Y` by conjugation `\lam z => inv f.2 *> pmap f.1 z *> f.2`.

#### H-Level Characterization

- **`loop-level`**: If every loop space `x0 = x0` has h-level `-1+n`, then `X` has h-level `-1+suc n`.
- **`loop-level-iter`**: If the n-fold iterated loop space at every basepoint is contractible, then `X` has h-level `-1+n`.
- **`loop-level-iter-inv`**: Converse: from h-level `-1+n` of `X` derives contractibility of the n-fold iterated loop space at any basepoint.

#### Suspension–Loop Adjunction

- **`SuspLoopEquiv`**: The fundamental loop-suspension adjunction as a path: `(Susp.pointed A ->* B) = (A ->* Loop B)`. Proved by a chain of equivalences using pushout universal property, sigma manipulations, and contractibility of based path spaces.

#### Sphere Maps

- **`SphereLoopEquiv`**: Pointed maps from the n-sphere are equivalent to n-fold iterated loops: `(Sphere.pointed n ->* B) = iterl Loop n B`. Base case constructs the equivalence directly via case analysis on `Sphere 0`; inductive step composes `SuspLoopEquiv` with the recursive equivalence.

#### Iterated Loop Spaces

- **`Omega^`**: The n-fold loop space `Omega^ n X` defined as `iterr Loop n X`, packaged as a `Pointed`.
- **`Omega^-Func`**: Functorial action of `Omega^ n` on pointed maps, defined recursively by iterating `Loop-Func`.
