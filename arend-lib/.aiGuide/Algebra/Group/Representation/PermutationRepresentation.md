### Algebra.Group.Representation.PermutationRepresentation

Constructs the permutation representation of a group from a G-set and shows when it fails to be irreducible.

This module turns any G-set `X` into a linear representation by letting `G` act on the free `R`-module `R^X` via permutation of basis indices: `(g ** f) j = f (g⁻¹ · j)`. The construction is the standard "linearization" of a group action, providing the bridge between combinatorial G-sets and linear representations. The second part of the module exhibits a canonical proper sub-representation — the diagonal copy of the trivial representation spanned by the all-ones vector — and uses it together with a non-constant indicator vector to witness reducibility whenever `X` has at least two distinct points with decidable equality to one of them; the resulting contradiction is `0 = 1` in `R`, so `PermRepr X` is not irreducible over a non-trivial ring.

#### Permutation Representation

- **`PermRepr`**: Given a ring `R`, group `G`, and G-set `X : GSet G`, builds the linear representation `R^X` with action `(g ** f) j = f (g⁻¹ ** j)`. The underlying module is `PowerLModule X (RingLModule R)`.

#### Reducibility Witness

- **`PermutationRepresReducible`**: Class collecting the data needed to prove that `PermRepr X` is reducible: a G-set `X` with two distinct points `somepoint`, `somepoint'` (`different-points`) and decidable equality with `somepoint` (`decideEq-somepoint`).

#### The Diagonal Sub-representation

- **`invSubRepr`**: The sub-representation of `PermRepr X` consisting of the constant vectors (`f x = f y` for all `x, y`), i.e. the diagonal copy of the trivial representation; it is closed under zero, sum, negation, scalars and the `G`-action.

- **`nonConstantVector`**: The indicator vector of `somepoint` in `PermRepr X`: `R.ide` at `somepoint` and `R.zro` elsewhere (using `decideEq-somepoint`); used to show the diagonal is not everything.

#### Non-Triviality and Non-Irreducibility

- **`SubRepr-non-trivial`**: If the diagonal sub-representation `invSubRepr` is trivial (zero or everything), then `0 = 1` in `R`: zero forces `1 = 0` directly, and being everything forces `nonConstantVector` to take equal values `1` and `0` at `somepoint` and `somepoint'`.
- **`Not-Irreducible`**: If `PermRepr X` were irreducible then `0 = 1` in `R`, obtained by applying the hypothesis to `invSubRepr` and using `SubRepr-non-trivial`; so it is not irreducible when `R` is non-trivial.
