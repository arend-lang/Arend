### Algebra Directory Overview
This directory provides the algebraic hierarchy from pointed sets up through fields, modules, and ordered structures, along with solvers and linear algebra.
#### Pointed Sets and Monoids
- **`Pointed.md`** / **`Pointed/`**: Pointed sets with a distinguished element (multiplicative and additive conventions). Subdirectory contains `PointedCategory.md`, `PointedHom.md`, `Sub.md` (substructures).
- **`Monoid.md`** / **`Monoid/`**: Semigroups, monoids, commutative and cancellative variants, divisibility, invertible elements. Subdirectory contains `FreeMonoid.md`, `GCD.md`, `MonoidCategory.md`, `MonoidHom.md`, `MonoidLocalization.md`, `PermSet.md`, `Prime.md`, `Product.md`, `Solver.md`, `Sub.md`.
#### Groups
- **`Group.md`** / **`Group/`**: Groups, abelian groups, additive groups, and decidable/apartness variants. Subdirectory contains `Aut.md` (automorphism groups), `Fin.md` (finite groups), `Free.md` (free groups), `GSet.md` / `GSet/` (group actions), `GroupCategory.md`, `GroupHom.md`, `Lagrange.md`, `Product.md`, `QuotientProperties.md`, `Representation.md` / `Representation/`, `Solver.md`, `Sub.md` (subgroups), `Symmetric.md`.
#### Semirings and Rings
- **`Semiring.md`** / **`Semiring/`**: Semirings (additive commutative monoids + multiplicative monoids with distributivity). Subdirectory contains `Sub.md`.
- **`Ring.md`** / **`Ring/`**: Pseudo-rings and commutative rings extending semirings with additive inverses, with apartness and decidable variants. Subdirectory contains `Boolean.md` / `Boolean/` (Boolean rings), `Factor.md`, `FormalSeries.md`, `Graded.md` / `Graded/` (graded rings), `GrothendieckRing.md`, `Ideal.md`, `Integral.md` / `Integral/` (integral extensions), `Local.md` (local rings), `Localization.md` / `Localization/`, `MPoly.md` (multivariate polynomials), `MonoidRing.md`, `Nakayama.md`, `Noetherian.md`, `Poly.md` / `Poly/` (univariate polynomials), `QPoly.md`.
#### Domains and Fields
- **`Domain.md`** / **`Domain/`**: Integral domains with zero-product properties. Subdirectory contains `Bezout.md`, `Euclidean.md`, `GCD.md`, `IntegrallyClosed.md`, `PID.md`, `Valuation.md`.
- **`Field.md`** / **`Field/`**: Fields and discrete fields. Subdirectory contains `Algebraic.md` (algebraic extensions), `AlgebraicClosure.md`, `Splitting.md` (splitting fields).
#### Modules and Linear Algebra
- **`Module.md`** / **`Module/`**: Left modules over rings, linear combinations, bases, generation. Subdirectory contains `FinModule.md` (finitely generated modules), `LinearMap.md`, `ModuleCategory.md`, `PowerLModule.md`, `Sub.md` (submodules), `Trace.md`.
- **`QModule.md`**: Q-modules (rational vector spaces) and Q-algebras as divisible torsion-free abelian groups.
- **`Linear/`**: Linear algebra — `Matrix.md` / `Matrix/` (matrices), `Solver.md`, `VectorSpace.md`.
#### Algebras
- **`Algebra.md`**: Algebras over commutative rings — modules with compatible ring structure (`PseudoAlgebra`, `AAlgebra`, `CAlgebra`), and `homAlgebra` construction.
- **`FinSuppFunc.md`**: Finitely supported functions with pointwise algebraic operations.
#### Ordered Structures
- **`Ordered.md`** / **`Ordered/`**: Algebraic structures compatible with partial order (ordered monoids, groups, semirings, rings, lattice-groups, absolute value). Subdirectory contains `OrderedLocalization.md`, `RieszSpace.md`.
- **`MulOrdered.md`**: Monoids with order compatible with multiplication.
- **`StrictlyOrdered.md`**: Strictly ordered algebraic hierarchy (strict `<` with positivity predicates), from ordered additive monoids through ordered fields and algebras.
#### Colimits
- **`LatticeColimit.md`**: Colimits of algebraic structures (monoids, groups, rings, fields) indexed over bottom-join-semilattices.
#### Solvers
- **`Solver.md`** / **`Solver/`**: Normalization-based equation solvers. Subdirectory contains solvers for `Monoid`, `CMonoid`, `Group`, `CGroup`, `Semiring`, `CSemiring`, `Ring`, `CRing`, `BooleanRing`.
