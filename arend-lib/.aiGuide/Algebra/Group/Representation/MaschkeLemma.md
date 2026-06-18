### Algebra.Group.Representation.MaschkeLemma

Maschke's lemma: over a commutative ring where the group order is invertible, every short exact sequence of finite-group representations splits.

The module formalizes the classical averaging argument: given a `LModule`-level retraction `p` of a subrepresentation inclusion, one averages `p` over the finite group `G` (dividing by `|G|`) to produce a `G`-equivariant retraction. The construction is built in two layers — `SumOverGroup` packages the unweighted sum `Σ_g g·f(g⁻¹·-)` as an intertwining map between any two linear representations, and `Maschke'sLemma` then scales it by `|G|⁻¹` and verifies the retraction property. The hypothesis `q : natCoef |G| * |G|⁻¹ = 1` makes `|G|` formally invertible in `R`, replacing the usual characteristic-coprime-to-`|G|` condition.

#### Main Class

- **`Maschke'sLemma`**: Parameterized by a commutative ring `R`, a finite group `G`, an inverse `|G|^-1 : R` of the group order (witnessed by `q`), a representation `E`, and a subrepresentation `S`. Provides the splitting of `S ↪ E` in the representation category whenever it splits as `R`-modules.
  - **`mean_func`**: Averaging operator on linear maps: scales `SumOverGroup f` by `|G|^-1` to produce an intertwining map `E → W`. This is the core symmetrization that turns a module map into an equivariant one.
  - **`retracts`**: Upgrades a `LModuleCat`-level split monomorphism `p : SplitMono S.in` to a split monomorphism in `RepresentationCat R G`, with `mean_func p.hinv` as the equivariant retraction.
  - **`mean-func-preserve`**: Key lemma — if `f` already retracts `S.in` at the module level (i.e. `f (S.in s) = s`), then `mean_func f` also retracts it on `S`. The proof reduces averaging to summing `t` over `G` and then multiplying by `natCoef |G| * |G|^-1 = 1`.

#### SumOverGroup Construction

- **`SumOverGroup`**: Given `f : LinearMap A B` between two linear representations, builds the intertwining map `Σ_{g∈G} g · f(g⁻¹ · -)`. The averaged sum is automatically `G`-equivariant even though `f` need not be.
  - **`Ab`**: The abelian group of `R`-linear maps `A → B`, obtained from the pre-additive structure on `LModule R`. Hosts the finite sum used to define `int`.
  - **`adjust`**: The `g`-th conjugated map `a ↦ g · f(g⁻¹ · a) : LinearMap A B`.
  - **`adjust'`**: Family `g ↦ adjust g f` viewed as a function `G → LinearMap A B`.
  - **`int`**: The underlying linear map of `SumOverGroup`, defined as `Ab.FinSum (adjust g f)`.
  - **`group_prop`**: `adjust g f (h · a) = h · adjust (h⁻¹ * g) f a`. Encodes how conjugation interacts with the action and is the algebraic core of equivariance.
  - **`bring_h_out`**: Pulls a `G`-action out of `int`: `int (h · a) = h · int a` after re-indexing the sum. This is what makes `int` an intertwining map.

#### Sum Manipulation Lemmas

- **`FinSum-equivariance`**: A `G`-action commutes with `FinSum` in the target module: `h · Σ x_i = Σ (h · x_i)`. Reduces to `BigSum-equivariance` via the `FinSum_char` characterization.
  - **`BigSum-equivariance`**: The same statement at the level of finite arrays, proved by induction on the array.
- **`FinSumEquality`**: Pointwise equal families have equal `FinSum`s. Shows up whenever the summand is rewritten under the sum.
- **`FinSumRewrite`**: Evaluation commutes with summation in `Ab`: `(Σ_g x_g) a = Σ_g (x_g a)`. Lets one move from sums of linear maps to sums of vectors.
- **`PermutationInvariance`**: `FinSum` is invariant under permutation of the index set. Used to reindex the sum over `G` by `g ↦ h⁻¹ * g`.
- **`rearrange`**, **`ap-rearrange`**: Apply `PermutationInvariance` with the permutation `g ↦ h⁻¹ * g` to rewrite `int` (resp. `int a`) in the form needed by `bring_h_out`.
- **`FinSumEqual-multiply`**: Sum of a constant family `(_ : A) ↦ e` in an `R`-module equals `natCoef |A| *c e`. The numerical identity that, combined with `q`, collapses the average back to the original element in `mean-func-preserve`.
