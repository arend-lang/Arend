### Algebra.Group.Representation.MaschkeLemma

Maschke's lemma: over a commutative ring where the group order is invertible, every short exact sequence of finite-group representations splits.

The module formalizes the classical averaging argument: given a `LModule`-level retraction `p` of a subrepresentation inclusion, one averages `p` over the finite group `G` (dividing by `|G|`) to produce a `G`-equivariant retraction. The construction is built in two layers — `SumOverGroup` packages the unweighted sum `Σ_g g·f(g⁻¹·-)` as an intertwining map between any two linear representations, and the class `MaschkeLemma` then scales it by `|G|⁻¹` and verifies the retraction property. The hypothesis `cardInv : Monoid.Inv (natCoef |G|)` makes `|G|` formally invertible in `R`, over a commutative ring `R` and finite group `G`, replacing the usual characteristic-coprime-to-`|G|` condition.

#### Main Class

- **`MaschkeLemma`**: Context for Maschke's argument over a commutative ring `R` and finite group `G`, parametrized by an inverse `cardInv` of `natCoef |G|` in `R`, a representation `E` and a sub-representation `S` of it; its members average module-level maps into equivariant ones.
  - **`mean_func`**: Averaging operator on linear maps: scales `SumOverGroup f` by `cardInv.inv` (i.e. `|G|^-1`) to produce an intertwining map `E → W`. This is the core symmetrization that turns a module map into an equivariant one.
  - **`mean-func-preserve`**: Key lemma — if `f` already retracts `S.in` at the module level (i.e. `f (S.in s) = s`), then `mean_func f` also retracts it on `S`. The proof reduces averaging to summing `t` over `G` and then multiplying by `natCoef |G| * cardInv.inv = 1`.

#### SumOverGroup Construction

- **`SumOverGroup`**: Given `f : LinearMap A B` between two linear representations, builds the intertwining map `Σ_{g∈G} g · f(g⁻¹ · -)`. The averaged sum is automatically `G`-equivariant even though `f` need not be.
  - **`Ab`**: The abelian group of `R`-linear maps `A → B`, given by `LinearMapAbGroup A B`. Hosts the finite sum used to define `int`.
  - **`adjust`**: The `g`-th conjugated map `a ↦ g · f(g⁻¹ · a) : LinearMap A B`.
  - **`int`**: The underlying linear map of `SumOverGroup`, defined as `Ab.FinSum (adjust g f)`.
  - **`group_prop`**: `adjust g f (h · a) = h · adjust (h⁻¹ * g) f a`. Encodes how conjugation interacts with the action and is the algebraic core of equivariance.
  - **`bring_h_out`**: Pulls a `G`-action out of `int`: `int (h · a) = h · int a` after re-indexing the sum. This is what makes `int` an intertwining map.

#### Sum Manipulation Lemmas

- **`FinSum-equivariance`**: A `G`-action commutes with `FinSum` in the target module: `h · Σ x_i = Σ (h · x_i)`. Reduces to `BigSum-equivariance` via the `FinSum_char` characterization (a `FinSum` is a `BigSum` of an array).
  - **`BigSum-equivariance`**: The same statement at the level of finite arrays, proved by induction on the array.
- **`FinSumRewrite`**: Evaluation commutes with summation in `Ab`: `(Σ_g x_g) a = Σ_g (x_g a)`. Lets one move from sums of linear maps to sums of vectors.
- **`PermutationInvariance`**: `FinSum` is invariant under permutation of the index set. Used to reindex the sum over `G` by `g ↦ h⁻¹ * g`.
- **`rearrange`**, **`ap-rearrange`**: Apply `PermutationInvariance` with the permutation `g ↦ h⁻¹ * g` to rewrite `int` (resp. `int a`) in the form needed by `bring_h_out`.
- **`FinSumEqual-multiply`**: Sum of a constant family `(_ : A) ↦ e` in an `R`-module equals `natCoef |A| *c e`, where `|A|` is `A.card`. The numerical identity that, combined with `cardInv`, collapses the average back to the original element in `mean-func-preserve`.
