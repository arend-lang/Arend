### Algebra.Group.Representation.Characters

Defines the character of a finite-dimensional linear representation as the trace of the matrix representing each group element.

#### Character

- **`Character`**: Given a linear representation `E : LinRepres R G` with basis `bv` for `lv : Array E`, computes the character `G -> R` at `g` as the trace of the matrix of the linear map `E.toLinearMap g` in basis `bv`.

#### Auxiliary Definitions

- **`chi`**: Convenience alias `chi g = Character E bv g`, fixing the representation and basis.
- **`of-1`**: Value at the identity: `chi G.ide = R.natCoef lv.len`, i.e. the character of the identity equals the dimension cast into `R`.
- **`Matrix-of-1`**: The matrix of the identity action `E.toLinearMap G.ide` in basis `bv` is the identity matrix of `MatrixRing`.
