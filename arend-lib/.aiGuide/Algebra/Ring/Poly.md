### Algebra.Ring.Poly

Univariate polynomials over a ring as a higher inductive type, with evaluation, coefficients, degree, division, and ring/algebra/module instances.

#### Core Type

- **`Poly`**: HIT of polynomials over an `AddPointed` `R`, with constructors `pzero`, `padd p e` (append constant `e` and shift `p`), and path constructor `peq : padd pzero 0 = pzero` identifying trailing zeros.
- **`Poly.toQPoly`**: Conversion from `Poly R` to the quotient-of-arrays representation `QPoly R`.
- **`Poly.fromArray`**: Builds a `Poly R` from an `Array R` of coefficients.
- **`Poly.cfunc`**, **`Poly.gfunc`**: Round-trip lemmas between `toQPoly` and `fromArray`.
- **`Poly.toQPoly-equiv`**: `toQPoly` is an equivalence `Poly R ≃ QPoly R`.
- **`Poly.levelSet`**: `Poly R` is a set (transported from `QPoly`).
- **`Poly.poly-countable`**: Countability of `Poly R` from countability of `R`.
- **`Poly.poly-trivial`**: If every element of `R` is zero then every polynomial is `pzero`.
- **`Poly.toQPoly-nil`**: A polynomial mapping to the empty array is `pzero`.

#### Maps Between Polynomial Rings

- **`polyMap`**: Functorial action of an `AddPointedHom` on polynomials.
- **`polyMap-comp`**: Composition law `polyMap g ∘ polyMap f = polyMap (g ∘ f)`.
- **`polyMap-inj`**: Injectivity of `polyMap f` from injectivity of `f`.
- **`polyMap_fromArray`**: `polyMap f` commutes with `fromArray`.
- **`polyMapRingHom`**: `polyMap f` packaged as a `RingHom (PolyRing R) (PolyRing S)` for a `RingHom f`.
- **`polyHom`**: The constant-embedding `RingHom R (PolyRing R)`, sending `r` to `padd pzero r`.

#### Evaluation

- **`polyEval`**: Evaluate `p : Poly R` at `a : R` in a ring, computed by Horner's scheme.
- **`polyMapEval`**: Map then evaluate: `polyEval (polyMap f p) a` for `f : R -> S`.
- **`polyEval_polyMap`**: Compatibility of `polyEval` with ring homs: `polyEval (polyMap f p) (f a) = f (polyEval p a)`.
- **`polyEval_fromArray`**, **`polyMapEval_fromArray`**: `BigSum`-formulas for evaluation of an array-built polynomial.
- **`polyEvalRingHom`**, **`polyMapEvalRingHom`**: Evaluation at `a` as a `RingHom` (commutative target).
- **`polyEvalRingHom.polyEval_*c`**, **`polyMapEvalRingHom.polyMapEval_*c`**: Evaluation distributes over scalar multiplication.
- **`polyHom_polyMapEval`**: Evaluating `polyHom p` at `padd 1 0` recovers `p` (universal property of the indeterminate).
- **`polyMapEval-unique`**: Two ring homs out of `PolyRing R` agree if they agree on constants and on `padd 1 0`.
- **`factorHom_polyMapEval`**: Evaluation through `factorHom ∘ polyHom` at `[X]` recovers `[p]`.

#### Coefficients

- **`polyCoef`**: `n`-th coefficient of `p`.
- **`lastCoef`**: Constant term `polyCoef p 0`.
- **`polyShift`**: Drop the constant term, shifting indices down.
- **`polyCoefHom`**: `polyCoef · n` as an `AddGroupHom (PolyRing R) R`.
- **`polyCoef=0`**: Higher coefficients of a constant polynomial vanish.
- **`polyCoef_polyMap`**, **`polyCoef_fromArray`**: Compatibility with `polyMap` and `fromArray`.
- **`polyCoef_+`**, **`polyCoef_negative`**, **`polyCoef_*c`**, **`polyCoef_*-right`**, **`polyCoef_*`**, **`polyCoef_BigSum`**: Coefficient formulas under ring/module operations.
- **`polyCoef-linear-*`**: Coefficient formula for multiplication by a linear factor.
- **`lastCoef_*`**, **`lastCoef_pow`**: Constant term is multiplicative.
- **`leadCoef-product`**, **`leadCoef_BigProd`**, **`leadCoef_BigProd1`**, **`leadCoef_pow`**: Leading-coefficient formulas under products and powers.

#### Roots and Linear Factors

- **`poly-root-div`**: If `polyEval p a = 0` then `a` divides `polyCoef p 0` (with explicit witness via `polyShift`).
- **`padd=pzero`**: `padd p a = pzero` forces `p = pzero` and `a = 0`.

#### Monomials

- **`monomial`**: `c X^n` as a `Poly R`.
- **`polyCoef_monomial`**, **`polyMap_monomial`**, **`polyEval_monomial`**, **`polyMapEval_monomial`**, **`monomial_zro`**, **`*c_monomial`**: Standard equations for monomials.
- **`monomial-isMonic`**: `X^n` is monic.
- **`degree<=_monomial`**: `monomial c n` has degree at most `n`.

#### Degree Predicates

- **`degree<`**: `degree< p n` means all coefficients at positions `≥ n` vanish.
- **`degree<=`**: `degree<= p n` is the strict-prop version (`p` has degree at most `n`).
- **`degree<=.toCoefs`**, **`degree<=.fromCoefs`**: Equivalence between `degree<= p n` and the coefficient condition.
- **`degree<=.trivialPoly`**: A polynomial whose coefficients all vanish (in any bound) is `pzero`.
- **`degree<=_degree<`**, **`degree<_degree<=`**: Conversions between strict and non-strict bounds.
- **`degree<0`**: `degree< p 0` implies `p = pzero`.
- **`degree<_padd`** (and `.conv`): Adding a constant raises the degree bound by one.
- **`degree<_polyMap`**, **`degree<=_polyMap`**: Degree bounds preserved by `polyMap`.
- **`degree-exists`**, **`degree<-exists`**: Every polynomial has some degree bound.
- **`degree<=-trans`**, **`degree-reduce`**, **`degree<=0`**: Manipulating bounds; a degree-0 polynomial is a constant.
- **`polyEval_polyCoef`**, **`polyMapEval_polyCoef`**: Evaluation as a finite sum of `coef · a^i` for bounded-degree polynomials.
- **`fromArray_polyCoef`**, **`fromArray_polyCoef.fromArray0`**, **`fromArray_degree<`**: Reconstructing a bounded polynomial from its coefficient array.
- **`degree<_+`**, **`degree<_negative`**, **`degree<=_+`**, **`degree<=_negative`**, **`degree<=_*c`**, **`degree<=_*`**, **`degree<=_BigSum`**, **`degree<=_FinSum`**, **`degree<=_BigProd`**, **`degree<=_BigProd1`**, **`degree<=_pow`**: Closure of degree bounds under arithmetic.
- **`degree<=0_*-conv`** (and `.aux`): In a strict domain, `pq` of degree 0 forces one factor to have degree 0.
- **`degree-monic-reduce`** (and `.aux`): Reduce a degree bound across multiplication by a linear monic factor.

#### Monic Polynomials

- **`isMonic`**: Predicate "leading coefficient is `1` for some degree bound".
- **`polyMap_isMonic`**: `polyMap f` preserves monicity.
- **`monic/=0`**, **`monic-unique`**: A monic polynomial is non-zero (in non-trivial rings) and its leading degree is unique.

#### Ring/Algebra Structure

- **`PolyRing`**: `Ring (Poly R)` instance, with the scalar action `*c`, helper lemmas `zro_*c`, `ide_*c`, `*c-rdistr`, `*c-ldistr`, `*c-assoc`, `*c-comm-left`, `padd0_*-comm`, `*c_*`, `*c_negative`.
- **`padd-expand`**: `padd p e = p · X + e` decomposition.
- **`PolyAlgebra`**: `CAlgebra R (Poly R)` instance for commutative `R`.
- **`PolyRingWith#`**: `Ring.With#` instance with apartness lifted coefficient-wise; helpers `#0_*c-left`, `#0_*c-right`.
- **`PolyCRingWith#`**, **`PolyDomain`**, **`PolyIntegralDomain`**, **`PolyStrictDomain`**, **`PolyStrictIntegralDomain`**, **`PolyDecRing`**, **`PolyDecCRing`**, **`PolyDecDomain`**, **`PolyDecIntegralDomain`**: Polynomial-ring instances inheriting commutativity / domain / decidability properties from `R`.

#### Invertibility and Divisibility

- **`polyInv`** (and `.coefInv`): A unit in `PolyRing R` over a strict domain is a constant unit.
- **`polyDiv`**: Mutual divisibility of `p, q` forces them either both zero or constant unit multiples of each other.
- **`polyDiv-linear`**: If `p · q` factors as a product of linear factors `(X - aⱼ)`, then either `q` has a root among the `aⱼ` or `q` is a unit.
- **`monomial-factor`**: Factoring `padd s 0 = p · q` exposes a `padd r 0` factor on one side.
- **`monomial_div`**: A divisor of `X^n` is a non-zero scalar monomial.
- **`monomial_div2`**: If `monomial c (suc n)` divides `p`, then the constant term of `p` is `0`.

#### Decidable Degree (over `Ring.Dec`)

- **`degree`**: Computable degree (in `Nat`).
- **`leadCoef`**: Computable leading coefficient.
- **`degree_degree<`**, **`degree<_degree`**, **`degree_degree<=`**, **`degree<=_degree`**: Equivalences between `degree` and the degree predicates.
- **`degree_polyMap`**: `degree (polyMap f p) = degree p` for injective `f`.
- **`monic-coef`**, **`leadCoef_polyCoef`**: Leading-coefficient identities.
- **`leadCoef=0-lem`**, **`degree=0-lem`**: A polynomial with zero leading coefficient is zero; `degree p = 0` puts `p` in canonical constant form.
- **`leadCoef_monomial`**, **`degree_monomial`**, **`degree_padd`**, **`degree_+`**, **`leadCoef_+`**, **`degree_*c`** (and `.*c-nonZero`), **`degree_*`** (and `.degree_<`), **`degree_*_=`**, **`leadCoef_*c`**, **`leadCoef_*`**: Degree and leading-coefficient under operations.
- **`field-toMonic`**: Over a discrete field, scaling by `1/leadCoef` produces a monic polynomial.

#### Polynomial Division

- **`polyDivision`**: Pseudo-division `(c)^k · f = q · g + r` with `degree< r n`, where `c = polyCoef g n`.
- **`monicPolyDivisionContr`** (and `.aux`), **`monicPolyDivision-unique`**, **`monicPolyDivision`**: Existence and uniqueness of `(q, r)` with `f = q g + r`, `degree< r n` when `g` is monic of degree ≤ `n`.
- **`degree1PolyDivision`**, **`degree1PolyDivision-unique`**: Division by `X - a` with remainder `polyEval f a`.
- **`rootPolyDivision-unique`**: Uniqueness of an `(X - a)` divisor when `a` is a root.
- **`rootDiv`**: Quotient `f / (X - a)`.
- **`rootDiv_*`**: `f = rootDiv f a · (X - a)` whenever `a` is a root.
- **`rootPolyDivision_degree`**: Dividing by `(X - a)` decreases degree by one (over a decidable integral domain).

#### Monomial Factorization

- **`poly-factor-monomial`**: Decompose any non-zero polynomial uniquely as `X^n · q` with `lastCoef q ≠ 0`.
- **`poly-factor-monomial-unique`**: Uniqueness of that decomposition.
- **`poly-monomial-char`**: `monomial 1 n · p = iterr (padd __ 0) n p` (multiplying by `X^n` shifts).

#### Module of `R[X]`-Actions on an Endomorphism

- **`polyModule`**: Given a linear map `f : U -> U`, the `R`-module `U` becomes a `PolyRing R`-module where `X` acts as `f`. Helpers: `poly_func` (the underlying action), `poly_func_zro`, `poly_func_poly-*c`, `finitelyGenerated` (preservation of finite generation).
