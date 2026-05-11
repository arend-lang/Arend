### Algebra.Group.Representation.PermutationRepresentation

Constructs the permutation representation of a group from a G-set and shows when it fails to be irreducible.

This module turns any G-set `X` into a linear representation by letting `G` act on the free `R`-module `R^X` via permutation of basis indices: `(g ** f) j = f (g⁻¹ · j)`. The construction is the standard "linearization" of a group action, providing the bridge between combinatorial G-sets and linear representations. The second part of the module exhibits a canonical proper sub-representation — the diagonal copy of the trivial representation spanned by the all-ones vector — and uses it together with a non-constant indicator vector to witness reducibility whenever `X` has at least two distinct points and `R` is non-trivial.

#### Permutation Representation

- **`PermRepr`**: Given a ring `R`, group `G`, and G-set `X`, builds the linear representation `R^X` with action `(g ** f) j = f (g⁻¹ ** j)`. The underlying module is `PowerLModule X (RingLModule R)`.
- **`PermRepr.inverse_prod`**: Auxiliary lemma `inverse (m * n) = inverse n * inverse m` used to verify the action is associative.
- **`PermRepr.R_m`**: The ring `R` viewed as a module over itself.
- **`PermRepr.Module`**: The underlying `R`-module `R^X` of the permutation representation.

#### Reducibility Witness

- **`PermutationRepresReducible`**: Class collecting the data needed to prove that `PermRepr X` is reducible: two distinct points `somepoint`, `somepoint'` of `X`, a non-trivial ring (`0 ≠ 1`), and decidable equality with `somepoint`.
- **`somepoints-equality-1`**, **`somepoints-equality-2`**: Compute the decidable-equality check at `somepoint` and `somepoint'` to `yes idp` and `no different-points` respectively, used for reducing case splits.
- **`R_m-triv`**: The trivial representation of `G` on `R` (each `g` acts as identity).
- **`Repr`**: Shorthand for `PermRepr X`, the representation under analysis.

#### The Diagonal Sub-representation

- **`vectorOnes`**: The all-ones vector in `R^X`, sending every index to `R.ide`.
- **`invSubMod`**: Linear map `R → R^X` sending `r` to the constant vector `λ _ → r`; embeds the ring as the diagonal submodule.
- **`inv-rev`**: Verifies `(invSubMod r) somepoint = r` (the embedding is a section at `somepoint`).
- **`fixed-submodule`**: Key invariance lemma: every constant vector `invSubMod r` is fixed by the action, i.e. `g ** invSubMod r = invSubMod r`.
- **`fixed-submodule.aux`**: Auxiliary identity `invSubMod r j = invSubMod r j'` reflecting that constant vectors are independent of index.
- **`invSubRepr`**: The diagonal embedding packaged as a `SubLRepres Repr` with source `R_m-triv`, using `fixed-submodule` to verify the intertwining property.

#### Non-Triviality and Non-Irreducibility

- **`Non-constant-vector`**: The indicator vector of `somepoint` in `R^X` (value `R.ide` at `somepoint`, `R.zro` elsewhere); used as a witness vector that does not lie in the diagonal sub-representation.
- **`Non-constant-vector.equation-1`**, **`Non-constant-vector.equation-2`**: Evaluate the indicator at `somepoint` (gives `R.ide`) and `somepoint'` (gives `R.zro`).
- **`SubRepr-non-trivial`**: Proves the diagonal sub-representation is neither the zero module nor surjective onto `Repr`: zero would force `0 = 1`, and surjectivity would force the constant value of any preimage of `Non-constant-vector` to equal both `R.ide` and `R.zro`.
- **`Not-Irreducible`**: Concludes that `PermRepr X` is not irreducible by feeding `invSubRepr` to the irreducibility hypothesis and contradicting `SubRepr-non-trivial`.
