### Algebra.Group.Representation.Characters

Characters of finite-dimensional linear representations of a group.

The character of a representation `E` of a group `G` over a ring `R` is the function `G → R` sending each group element to the trace of the matrix representing its action in a chosen basis. This module fixes a basis `bv` of `E` and defines the character via `LinearMap.toMatrix` followed by `Trace`. The basic identity `χ(1) = dim(E)` is established by reducing the identity action to the identity matrix, whose trace is the natural-number coefficient of the basis length.

#### Definitions

- **`Character`**: For a linear representation `E : LinRepres R G` with basis `bv` of `lv : Array E`, the character `Character E bv g : R` is `Trace (LinearMap.toMatrix lv bv (E.toLinearMap g))` — the trace of the matrix of the `G`-action of `g` in the chosen basis.

#### Basic Properties (in `\where`)

- **`chi`**: Protected shorthand `chi g = Character E bv g`, providing the standard character notation `χ : G → R`.
- **`of-1`**: The character at the identity equals the dimension: `chi G.ide = R.natCoef lv.len`, i.e. `χ(1) = dim(E)` as an element of `R`.
- **`Matrix-of-1`**: Auxiliary lemma stating that the matrix of the identity action is the identity matrix: `LinearMap.toMatrix lv bv (E.toLinearMap G.ide) = MatrixRing.ide`.
