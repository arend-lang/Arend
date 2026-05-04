### Algebra.Linear.Matrix.CayleyHamilton

The Cayley-Hamilton theorem: every square matrix over a commutative ring satisfies its own characteristic polynomial.

#### Main Theorem

- **`cayley-hamilton`**: For a square matrix `A : Matrix R n n` over a commutative ring `R`, evaluating the characteristic polynomial `charPoly A` at `A` (via the coefficient embedding into the matrix algebra) yields the zero matrix: `polyMapEval (coefHom) (charPoly A) A = 0`.

#### Supporting Constructions

- **`toLinearMap`**: Converts a matrix `A : Matrix R n m` into a `LinearMap` between free finite modules `ArrayFinModule n -> ArrayFinModule m`, acting by the standard matrix-vector product `u |-> \j. \sum_i A i j * u i`. Includes proofs of additivity (`func-+`) and scalar compatibility (`func-*c`).
- **`poly_func_matrix`**: Compatibility lemma relating polynomial evaluation in the matrix algebra to polynomial evaluation as a module endomorphism: the `(i,j)` entry of `polyMapEval (coefHom) p A` equals the `j`-th coordinate of `polyModule.poly_func p (toLinearMap A)` applied to the `i`-th standard basis vector. Bridges the matrix-algebra and linear-map formulations needed in the Cayley-Hamilton proof.
