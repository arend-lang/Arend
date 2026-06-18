### Algebra.Linear.Matrix.CayleyHamilton

The Cayley-Hamilton theorem: every square matrix satisfies its own characteristic polynomial.

This module formalizes the classical theorem that for a matrix `A` over a commutative ring, evaluating the characteristic polynomial `charPoly A` at `A` itself yields the zero matrix. The proof bridges the matrix algebra `MatrixAlgebra R n` with the module-theoretic view of matrices as linear maps on `ArrayFinModule n`, using `polyMapEval` to interpret polynomials over `R` as polynomials over the matrix algebra. The key reduction translates polynomial-in-matrix evaluation to action on standard basis vectors via `polyModule.poly_func`, allowing the result to follow from properties of the linear map associated with `A`.

#### Main Theorem

- **`cayley-hamilton`**: For a commutative ring `R` and an `n × n` matrix `A`, `polyMapEval (CAlgebra.coefHom {MatrixAlgebra R n}) (charPoly A) A = 0`. States that `A` annihilates its own characteristic polynomial when the polynomial is lifted to the matrix algebra and evaluated at `A`.

#### Supporting Constructions

- **`toLinearMap`**: Converts a matrix `A : Matrix R n m` into a `LinearMap (ArrayFinModule n) (ArrayFinModule m)` by the standard rule `u ↦ (j ↦ ∑ᵢ A i j * u i)`. Provides the bridge between matrix algebra and the module-theoretic formulation needed for the proof.
- **`poly_func_matrix`**: Equates the `(i, j)` entry of `polyMapEval (CAlgebra.coefHom) p A` with `polyModule.poly_func p (toLinearMap A) (MatrixRing.ide i) j`, reducing polynomial evaluation on a matrix to polynomial action on the `i`-th standard basis vector via the associated linear map. Key technical lemma reducing the matrix-polynomial identity to a module-level statement.
