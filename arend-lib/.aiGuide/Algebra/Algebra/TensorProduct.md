### Algebra.Algebra.TensorProduct

Algebra structures on the tensor product `TensorProduct A B` of two algebras over a commutative ring `R`.

The module takes the tensor product of modules from `Algebra.Module.TensorProduct` and equips it with a multiplication determined by `tensor a b * tensor a' b' = tensor (a * a') (b * b')`, built through the bilinear lift `tensorLift`. The result is an associative unital algebra for arbitrary `AAlgebra`s, and a commutative algebra when both factors are `CAlgebra`s.

#### Tensor product of algebras

- **`TensorProductAAlgebra`**: for `R : CRing` and `A B : AAlgebra R`, an `AAlgebra R` structure on `TensorProduct A B`, with unit `tensor 1 1` and multiplication induced by the componentwise product.
- **`TensorProductCAlgebra`**: for `A B : CAlgebra R`, the tensor product is a `CAlgebra R`, i.e. its multiplication is commutative.
