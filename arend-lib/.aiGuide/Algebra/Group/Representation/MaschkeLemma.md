### Algebra.Group.Representation.MaschkeLemma

Maschke's lemma: in characteristic coprime to `|G|`, every subrepresentation of a finite-group linear representation is a direct summand, proved via averaging over the group.

#### Setup

- **`Maschke'sLemma`**: Class bundling the hypotheses of Maschke's lemma — a commutative ring `R`, a finite group `G`, an inverse `|G|^-1` of the order of `G` in `R` witnessed by `q : natCoef |G| * |G|^-1 = 1`, a linear representation `E` over `R[G]`, and a subrepresentation `S` of `E`.

#### Averaging Construction

- **`SumOverGroup`**: Given a linear map `f : A -> B` between linear `G`-representations over a commutative ring `R`, builds the `G`-equivariant interwining map `(1/|G|) Σ_{g ∈ G} g · f(g^{-1} · -)` (here without the normalization factor — just the sum). The resulting map satisfies `func-**` proving equivariance with respect to the `G`-action.

#### Components of `SumOverGroup`

- **`Ab`**: The abelian group of linear maps `A -> B`, obtained from the pre-additive structure on `R`-modules.
- **`adjust`**: Conjugates a linear map by `g`: `adjust g f := a ↦ g · f(g^{-1} · a)`. Inherits linearity from `f` and the module structure.
- **`adjust'`**: The function `g ↦ adjust g f` viewed as a map `G -> LinearMap A B`.
- **`int`**: The averaged map `Σ_{g ∈ G} adjust g f`, computed as a finite sum in `Ab`.
- **`group_prop`**: Key identity `adjust g f (h · a) = h · adjust (h^{-1} g) f a`, used to commute the action past the averaging.

#### Equivariance and Sum Lemmas

- **`FinSum-equivariance`**: The `G`-action commutes with finite sums in a representation: `h · Σ x = Σ (h · x)`.
  - **`act_array`**, **`BigSum-equivariance`**: Helpers extending equivariance to `BigSum` over arrays by induction.
- **`FinSumEquality`**: Two finite sums agree pointwise implies they are equal in any abelian monoid.
  - **`BigSumEquality`**, **`ArrayEquality`**: Underlying array-level equalities used to derive `FinSumEquality`.
- **`FinSumRewrite`**: Evaluation commutes with finite sums of linear maps: `(Σ x) a = Σ (x_g a)`.
  - **`Ab_Helper`**, **`ap_BigSum_el_wise`**, **`BigSumRewrite`**: Pointwise evaluation lemmas for sums in the linear-map abelian group.
- **`PermutationInvariance`**: Finite sums are invariant under permutation of the index set: `Σ x = Σ (x ∘ p)` for any equivalence `p`.

#### Equivariance of the Averaged Map

- **`rearrange`**: Reindexing identity `int = Σ_g adjust (h^{-1} g) f` for any `h ∈ G`, exploiting that left multiplication by `h^{-1}` permutes `G`.
- **`ap-rearrange`**: Pointwise version of `rearrange` evaluated at `a ∈ A`.
- **`bring_h_out`**: Core equivariance computation `int (h · a) = h · (Σ_g adjust (h^{-1} g) f) a`, the heart of the proof that `int` is `G`-equivariant.
  - **`zero-2-step`**: Pushes evaluation at `a` inside the `FinSum`.
  - **`step-4`**: Rewrites the summand `h · adjust (h^{-1} g) f a` into the canonical form `g · f(g^{-1} · (h · a))`.
  - **`helper`**: Per-summand identity used by `step-4`, derived from associativity and inverse cancellation in `G`.

#### Scalar Multiplication of Constant Sums

- **`FinSumEqual-multiply`**: Sum of a constant element `e` over a finite set `A` equals `natCoef(|A|) *c e`. This is what enables division by `|G|` once `|G|^-1 ∈ R`, completing Maschke's averaging argument.
  - **`BigSumEqual`**: Inductive version of `FinSumEqual-multiply` for `BigSum` over a length-`n` constant array.
