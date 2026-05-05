### Topology.BanachAlgebra

Banach algebras: complete normed algebras combining the structures of Banach spaces and normed rings, with constructions over the reals and a completion functor.

#### Banach Algebra Classes

- **`BanachAlgebra`**: A Banach algebra, extending `BanachSpace`, `QAlgebra`, and `CompleteExNormedRing`. The general notion of a complete normed algebra over a base field.
- **`RealPreBanachAlgebra`**: A pre-Banach algebra over the reals, extending `RealPreBanachSpace` and `ExPseudoNormedRing`. The unfinished/non-complete version used as input to completion.
- **`RealBanachPseudoAlgebra`**: A real Banach pseudo-algebra, extending `RealBanachSpace`, `QPseudoAlgebra`, `ExPseudoNormedPseudoRing`, and `CompleteExNormedAbGroup`. Allows the multiplication to be a pseudo-operation (without strict associativity/unit constraints at the algebraic level).
- **`RealBanachAlgebra`**: A Banach algebra over the reals, extending `RealBanachPseudoAlgebra`, `BanachAlgebra`, and `RealPreBanachAlgebra`. The full structure with a complete real-valued norm.

#### Auxiliary Sequence for Square Root Construction

Defined inside `RealBanachAlgebra` for use in proofs (e.g. holomorphic calculus, square root of `1 - x`):

- **`rfunc`**: Rational sequence defined by `rfunc 0 = 0` and `rfunc (suc n) = 1/2 * (1 + rfunc n * rfunc n)`. Approximates `1` from below via the standard recursion for `sqrt(1-x)`-style iterations.
- **`rfunc>=0`**: `0 <= rfunc n` for all `n`.
- **`rfunc<=1`**: `rfunc n <= 1` for all `n`.
- **`rfunc-inc`**: Monotonicity: `rfunc n <= rfunc (suc n)`.
- **`rfunc-rec`**: Recurrence relation `rfunc (suc (suc n)) - rfunc (suc n) = 1/2 * ((rfunc (suc n) + rfunc n) * (rfunc (suc n) - rfunc n))`, expressing successive differences.
- **`rfunc-bound`**: Geometric error bound: if `rfunc n <= 1 - eps` with `eps > 0`, then `1 - rfunc n <= (1 - eps/2) ^ n`.
- **`rfunc-limit`**: `rfunc` converges to `1` in the topological sense (`TopSpace.IsLimit rfunc 1`).

#### Completion

- **`BanachAlgebraCompletion`**: Instance promoting any `RealPreBanachAlgebra X` to a `RealBanachAlgebra` by completing it. Inherits the underlying Banach space from `BanachCompletion X`, and lifts multiplication to the completion.
  - **`*-cover`**: The multiplication on the completion as a cover map `Completion X ⨯ Completion X -> Completion X`, obtained by lifting the locally uniform multiplication via `lift2 *-locally-uniform`.
  - **`*-func`**: The product of two regular Cauchy filters, defined as `*-cover (x, y)`. Used as the multiplication operation in the resulting Banach algebra structure.
