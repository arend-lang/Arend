### Algebra.Ring.MonoidRing

The monoid ring `R[M]` of a monoid `M` over a (semi)ring `R`, constructed as formal `R`-linear combinations of monoid elements.

Elements are represented as quotients of arrays of `(coefficient, monomial)` pairs by the smallest equivalence relation that identifies permutations, drops zero-coefficient terms, and merges like terms (`(a+b, m) ~ (a, m) :: (b, m)`). This presentation avoids requiring decidable equality on `M`, with arithmetic defined directly on representatives and lifted through the quotient. A separate `DecMonoidSet` gives a normalized canonical form when both `M` and `R` have decidable equality, and a universal-property-style evaluation map sends a monoid map `M -> R` to the induced ring homomorphism out of `R[M]`.

#### Core Type and Equivalence

- **`MonoidSet`**: `Quotient (Array (\Sigma R M)) ~` — formal sums of monomials with `R`-coefficients indexed by `M`.
- **`~`**: The generating equivalence: permutations, symmetry, dropping zero coefficients, and splitting/merging like terms.
- **`inMS`**, **`inMS~`**: Inject an array of `(r, m)` pairs into `MonoidSet`.
- **`~-msequiv`**: Promote `l ~ l'` to equality `inMS l = inMS l'`.
- **`monoidSet-ext`**: Transfer equality on the underlying quotient to equality in `MonoidSet`.
- **`toClosure`** / **`fromClosure`**: Convert between equality of `inMS` values and the transitive closure of `~`.

#### Closure Lemmas for `~`

- **`~_++-left`**, **`~_++-right`**, **`~_++`**: Concatenation respects `~` (and its closure) on each side and both sides.
- **`~_Big++`**: Pointwise `~`-related arrays of arrays give `~`-related big concatenations.
- **`~_map`**: Applying `(f, g)` componentwise (with `f` an `AddMonoidHom`) preserves `~`.
- **`unique-sum`**: An array all sharing monomial `m` collapses to the single term `(BigSum coefs, m)`.

#### Monomials

- **`msMonomial`**: `(r, m) :: nil` as a `MonoidSet` — a single term `r · m`.
- **`msMonomial_+`**, **`msMonomial_BigSum`**, **`msMonomial_0`**: Coefficient additivity of `msMonomial _ m`.
- **`msMonomial_*`**, **`msMonomial_pow`**, **`msMonomial-split`**: Multiplicativity and the splitting `r·1 * 1·m = r·m`.
- **`msMonomial_BigSum-split`**: Any `inMS l` is the sum of its monomials.
- **`msMonomial_BigProd`**, **`msMonomial_BigProd_ide`**: Products of monomials combine componentwise.

#### Algebraic Structure

- **`MonoidAbMonoid`**: Additive abelian monoid on `MonoidSet M R` with `+ = ++` and `0 = nil`.
- **`MonoidAbGroup`**: Adds negation by negating each coefficient (requires `R : AddGroup`).
- **`MonoidSemiring`**: Multiplication via `pairs func`, with `1 = (1, 1) :: nil` (requires `M : Monoid`, `R : Semiring`).
- **`MonoidRing`**: Combines the group and semiring structure for `R : Ring`.
- **`MonoidCSemiring`**: Commutative version when both `M` and `R` are commutative.
- **`MonoidAlgebra`**: `R[M]` as a commutative `R`-algebra via `monoidRingHom`.

#### Homomorphisms

- **`monoidRingHom`**: `RingHom R (MonoidRing M R)` sending `a` to `a · 1`.
- **`monoidSet-map`**: Functorial action: lift `f : M -> N` and `g : AddMonoidHom` to `MonoidSet M _ -> MonoidSet N _`.
- **`monoidSet-hom`**, **`monoidSet-semiringHom`**, **`monoidSet-ringHom`**: Upgrade `monoidSet-map` to `AddMonoidHom`/`SemiringHom`/`RingHom`.

#### Coefficient Extraction

- **`monoidSet-coefs`**: Sum of coefficients of monomials satisfying a decidable predicate `P` (over an `AbMonoid` `R`).
- **`msCoef`**: Coefficient of a specific monomial `m` (requires `M : DecSet`).
- **`msCoef_monomial`**: `msCoef (msMonomial r m) m = r`.
- **`msCoef_map`**: Coefficient extraction commutes with `monoidSet-map` along an injective `f`.
- **`monoidSet-coefs_+`**, **`monoidSet-coefs_BigSum`**: Additivity of coefficient extraction.
- **`msCoef-split`**: Every `p` is a finite sum `∑ msMonomial (msCoef p m) m` over an injective list of monomials.
- **`msCoefs-fin`**: The set of monomials with nonzero coefficient is finite (decidable `R`).
- **`msCoef/=0`**: A nonzero element has at least one nonzero coefficient.
- **`msCoef/=0_Index`**: A nonzero coefficient at `m` implies `m` appears in the underlying representative.

#### Decidable Normal Form

- **`DecMonoidSet`**: Quotient of arrays with distinct monomials and nonzero coefficients, modulo permutation.
- **`DecMonoidSet.Type`**: The underlying record carrying injectivity and nonzero-coefficient witnesses.
- **`inDMS`**, **`~-dmsequiv`**, **`unext`**: Quotient introduction and reflection of equality back to a permutation.
- **`decToMonoidSet`**: Forget the canonical form to get a `MonoidSet`.
- **`monoidSetDec-equiv`**: `decToMonoidSet` is an equivalence when `M` and `R` have decidable equality — provides a canonical normal form via internal helpers `msNorm`, `msNormVars`, and their `~`-invariance lemmas.

#### Evaluation (Universal Property)

- **`evalMS`**: Evaluate `x : MonoidSet M R` at a function `f : M -> R` by `∑ s.1 * f s.2`.
- **`evalMSMonoidHom`**: `evalMS _ f` as an `AddMonoidHom`.
- **`evalMSSemiringHom`**, **`evalMSRingHom`**: Upgrades when `f` is a `MonoidHom` into a (commutative) (semi)ring.
- **`evalMS_monomial`**: `evalMS (msMonomial a 0) f = a`.
- **`evalMS_map`**, **`evalMS_map2`**: Naturality of evaluation along `monoidSet-map`.
- **`monoidRingHom-unique`**: Two ring homs out of `MonoidRing M R` agreeing on coefficient and monomial monomials are equal — the universal property of `R[M]`.

#### Bridge to Polynomials

- **`polyToMonoidSet`**: Substitute a monoid element `m` for the indeterminate of `Poly R` to produce an element of `MonoidSet M R`.
- **`polyToMonoidSetHom`**: The above as an `AddMonoidHom`.
- **`polyToMonoidSetRingHom`**: Upgrade to a `RingHom` when `M` is commutative.
- **`polyToMonoidSet-eval`**: Evaluating the resulting monoid-set element at a monoid hom `f` agrees with `polyEval p (f m)`.
