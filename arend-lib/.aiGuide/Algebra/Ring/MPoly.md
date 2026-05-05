### Algebra.Ring.MPoly

Multivariate polynomial rings, defined as the monoid algebra over the free commutative monoid (permutation set) on a variable set, with constructors, evaluation, and degree theory.

#### Core Definition

- **`MPoly`**: Multivariate polynomials over `J` with coefficients in `R`, defined as `MonoidAlgebra (PermSetMonoid J) R`.
- **`mPolyHom`**: Canonical ring embedding `R -> MPoly J R` of constants.

#### Maps and Functoriality

- **`mPoly-map`**: Functorial action on coefficients along an `AddMonoidHom R S`, sending `MPoly J R` to `MPoly J S`.
- **`mPoly-map-comp`**: Composition law: `mPoly-map (g ∘ f) p = mPoly-map g (mPoly-map f p)`.
- **`mPoly-mapHom`**: Ring homomorphism version of `mPoly-map` for a `RingHom R S`.
- **`mPoly-map_permSet-univ`**: Naturality of `mPoly-map` with respect to the universal property of permutation sets.
- **`MPoly-surj`**: Surjectivity of `mPoly-map f` whenever the underlying coefficient map `f` is surjective.

#### Evaluation

- **`mPolyEval`**: Ring homomorphism `MPoly J R -> R` evaluating variables via `f : J -> R`.
- **`mPolyMapEval`**: Combined operation: map coefficients along `f : R -> S` and then evaluate at `a : J -> S`.
- **`mPolyMapEvalRingHom`**: Ring homomorphism version of `mPolyMapEval`.
- **`mPolyMapEval-var`**: Computes `mPolyMapEvalRingHom f a (mVar j) = a j`.
- **`mPolyRingHom-unique`**: Uniqueness: two ring homomorphisms `MPoly J R -> S` agreeing on constants and variables are equal.

#### Generators

- **`mVar`**: The variable polynomial `mVar j : MPoly J R` for `j : J`, as the monomial `1 · [j]`.
- **`mVar_pow`**: `pow (mVar j) n = msMonomial 1 (pow [j] n)`.
- **`mConst`**: The constant polynomial `mConst a : MPoly J R` for `a : R`.

#### Coefficient Lemmas

- **`msCoef_mVar=0`**: Coefficient of `pow (mVar j) n` in `pow (mVar j) m * p` is zero when `n < m`.
- **`msCoef_mMap`**: Coefficient extraction commutes with coefficient mapping: `msCoef (mPoly-map g p) m = g (msCoef p m)`.
- **`mLastCoef`**: Extracts the constant (zero-degree) coefficient via `monoidSet-coefs permSet-zro-dec`.

#### Special Cases of the Variable Set

- **`MPoly_Empty`**: Equivalence `MPoly J R ≃ R` when `J` is empty, via `mLastCoef`.
- **`MPoly_Fin-suc`**: Equivalence `MPoly (Fin (suc n)) R ≃ Poly (MPoly (Fin n) R)`, presenting one variable as the outer polynomial indeterminate; comes with internal `fHom`, `retHom`, and lemmas `f-monomial_+`, `ret-aux`, `ret_eval`, `f_eval`, `f_polyMap` relating evaluation and coefficient maps across the equivalence.
- **`MPoly_Fin-suc'`**: Alternative equivalence `MPoly (Fin (suc n)) R ≃ MPoly (Fin n) (PolyAlgebra R)`, splitting one variable out as a polynomial-algebra coefficient; includes `map-comm`, `eval-comm`, `mapEval-comm` for compatibility with maps and evaluation.

#### Degree

- **`mdegree<`**: Propositional predicate stating that some representative of `p` has all monomials of length `< d`.
- **`mdegree<_map`**: Degree bound is preserved by `mPoly-map`.
- **`mdegree<-exists`**: Every multivariate polynomial has some degree bound.
- **`mdegree<0`**: A polynomial with `mdegree< p 0` is zero.
- **`decMdegree`**: Computable maximum monomial length on a `DecMonoidSet` representative.
- **`mdegree`**: Total degree function `MPoly J R -> Nat` over decidable coefficient rings.
- **`mdegree_mdegree<`**: `mdegree< p (suc (mdegree p))`, witnessing that `mdegree` is a valid bound.
