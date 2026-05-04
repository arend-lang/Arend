### Homotopy.Loop

Loop spaces of pointed types, with iterated loop spaces and equivalences relating them to suspension and sphere mapping spaces.

#### Loop Space Construction

- **`Loop`**: Loop space of a pointed type `X`: the pointed type with carrier `base = base` and basepoint `idp`. Instance of `Pointed`.
- **`Loop-Func`**: Functorial action of `Loop` on pointed maps: a pointed map `f : X ->* Y` induces `Loop X ->* Loop Y` by conjugation `inv f.2 *> pmap f.1 z *> f.2`.
- **`Omega^`**: Iterated loop space `Ω^n X`, defined as `iterr Loop n X`.
- **`Omega^-Func`**: Functorial action of `Omega^ n` on pointed maps, lifting `f : X ->* Y` to `Omega^ n X ->* Omega^ n Y`.

#### H-Level Lemmas

- **`loop-level`**: If for every `x0 : X` the loop space `x0 = x0` has h-level `n-1`, then `X` has h-level `n`.
- **`loop-level-iter`**: If for every basepoint `x0 : X` the iterated loop space `Ω^n (X, x0)` is contractible, then `X` has h-level `n-1`.
- **`loop-level-iter-inv`**: Converse: if `X` has h-level `n-1`, then for every basepoint `x0` the iterated loop space `Ω^n (X, x0)` is contractible.

#### Loop/Suspension/Sphere Adjunctions

- **`SuspLoopEquiv`**: Loop-suspension adjunction: pointed maps `Susp A ->* B` are equivalent to pointed maps `A ->* Loop B`. Proven by a chain of equivalences via the pushout universal property and sigma manipulations.
- **`SphereLoopEquiv`**: Pointed maps from the `n`-sphere into `B` are equivalent to the iterated loop space `iterl Loop n B`. Proven by induction on `n` using `SuspLoopEquiv`.
