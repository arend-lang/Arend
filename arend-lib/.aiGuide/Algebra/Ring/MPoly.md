### Algebra.Ring.MPoly

Multivariate polynomial rings over a commutative ring, indexed by an arbitrary set of variables.

`MPoly J R` is constructed as the monoid algebra `R[PermSet J]` where `PermSet J` is the free commutative monoid on `J` (i.e., multisets of variables forming monomials). This presentation makes multivariate polynomials a special case of monoid rings, so ring structure, homomorphisms, and evaluation are inherited from `MonoidAlgebra`. The module provides the universal property (a ring hom out of `MPoly J R` is determined by its action on constants and variables), evaluation at a tuple, isomorphisms relating `MPoly (Fin (suc n)) R` to `Poly (MPoly (Fin n) R)` (iterated univariate polynomials) and to `MPoly (Fin n) (Poly R)`, and a notion of bounded total degree.

#### Core Type and Basic Constructions

- **`MPoly`**: The multivariate polynomial ring `MPoly J R := MonoidAlgebra (PermSetMonoid J) R`, polynomials over `R` in variables indexed by `J`.
- **`mPolyHom`**: The canonical ring homomorphism `R -> MPoly J R` embedding constants.
- **`mVar`**: The variable `j : J` as a polynomial — the monomial `1 · {j}` in `MPoly J R`.
- **`mConst`**: The constant polynomial `a : R` as an element of `MPoly J R`.

#### Functoriality and Maps

- **`mPoly-map`**: Lifts an additive monoid hom `R -> S` to a function `MPoly J R -> MPoly J S` by acting on coefficients.
- **`mPoly-map-comp`**: Functoriality of `mPoly-map` under composition.
- **`mPoly-mapHom`**: Promotes a ring hom `R -> S` to a ring hom `MPoly J R -> MPoly J S`.
- **`mPoly-map_permSet-univ`**: `mPoly-map` commutes with the universal map out of a `PermSet`.
- **`MPoly-surj`**: If `f : R -> S` is a surjective additive hom, so is `mPoly-map f`.

#### Evaluation

- **`mPolyEval`**: Given an assignment `f : J -> R`, the evaluation ring hom `MPoly J R -> R`.
- **`mPolyMapEval`**: Evaluate `p : MPoly J R` at `a : J -> S` after applying `f : R -> S` on coefficients.
- **`mPolyMapEvalRingHom`**: The ring hom version `MPoly J R -> S` combining coefficient map and evaluation.
- **`mPolyMapEval-var`**: Evaluation sends `mVar j` to `a j`.

#### Universal Property

- **`mPolyRingHom-unique`**: Two ring homs `MPoly J R -> S` agree if they agree on constants `mConst r` and on variables `mVar j`. This is the key tool for proving equalities of homs out of `MPoly`.

#### Coefficient Lemmas

- **`mLastCoef`**: The constant-term coefficient (coefficient of the empty monomial), used for the `J = Empty` isomorphism.
- **`msCoef_mVar=0`**: The coefficient of `(mVar j)^n` in `(mVar j)^m * p` vanishes when `n < m`.
- **`msCoef_mMap`**: Coefficient extraction commutes with `mPoly-map`: `msCoef (mPoly-map g p) m = g (msCoef p m)`.
- **`mVar_pow`**: `(mVar j)^n` equals the monomial `1 · {j,...,j}` (the `n`-fold multiset).

#### Structural Isomorphisms

- **`MPoly_Empty`**: Equivalence `MPoly J R ≃ R` when `J` is empty, via `mLastCoef`.
- **`MPoly_Fin-suc`**: Equivalence `MPoly (Fin (suc n)) R ≃ Poly (MPoly (Fin n) R)`, exhibiting multivariate polynomials as iterated univariate polynomials. The `\where` block defines:
  - **`var-func`**, **`f-monomial`**, **`f-monomial_+`**: Mapping monomials in `Fin (suc n)` to `Poly (MPoly (Fin n) R)`.
  - **`m-func`**, **`m-func-coh`**: The forward direction on monoid-set representatives, with respect for the equivalence relation.
  - **`fHom`**, **`retHom`**: The forward and reverse maps as ring homomorphisms.
  - **`ret-aux`**, **`ret_eval`**, **`f_eval`**: Compatibility of `ret`/`f` with scalar action and polynomial evaluation.
  - **`f_polyMap`**: `f` commutes with coefficient maps.
- **`MPoly_Fin-suc'`**: Equivalence `MPoly (Fin (suc n)) R ≃ MPoly (Fin n) (Poly R)`, "splitting off" one variable into the coefficient ring. The `\where` block defines:
  - **`fHom`**, **`retHom`**: The two ring homs implementing the equivalence, built via `mPolyMapEvalRingHom`.
  - **`retHom_mConst`**, **`fHom_mVar`**: Action on the distinguished variable/constant.
  - **`map-comm`**, **`eval-comm`**, **`mapEval-comm`**: Compatibilities of the equivalence with coefficient maps and evaluation.

#### Total Degree

- **`mdegree<`**: Proposition that `p : MPoly J R` has a representation in which every monomial has length (total degree) strictly less than `d`.
- **`mdegree<_map`**: Degree bound is preserved under `mPoly-map`.
- **`mdegree<-exists`**: Every `p` has some degree bound `d`.
- **`mdegree<0`**: `mdegree< p 0` implies `p = 0`.
- **`decMdegree`**: Computable total degree on the decidable monoid-set representation, via the max of monomial lengths.
- **`mdegree`**: Total degree of `p : MPoly J R` over a decidable variable set and decidable ring.
- **`mdegree_mdegree<`**: `mdegree p` is a strict upper bound: `mdegree< p (suc (mdegree p))`.
