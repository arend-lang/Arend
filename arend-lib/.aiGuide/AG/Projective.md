### AG.Projective

This module defines the projective spectrum (`Proj`) for graded commutative rings.

#### Projective Spectrum Presentation (`ProjPres`)

- **`ProjPres`**: `FramePres (Carrier R)` for a `GradedCRing R`, where carriers are homogeneous elements of positive degree.
  - **`Carrier`**: Type of triples `(a, n, isHomogen a (suc n))` — homogeneous elements of degree `≥ 1`.
  - **`ext`**: Extensionality for `Carrier`: equal first components imply equal carriers or zero.
  - **`h*`**: Homogeneous multiplication of carriers.
  - **`cover_spec`**: A `ProjPres` cover implies a `SpecPres` cover on the underlying elements.
  - **`cover_factor`**: A `Cover1` in `ProjPres` yields `∃ (n) (c) (pow x.1 n = y.1 * c)`.
  - **`cover_zro`**: Zero elements are covered by anything.
  - **`cover-eq`**: Covers transfer along equal first components.
  - **`cover-proj1`** / **`cover-proj2`**: Cover lemmas for products with homogeneous factors.
  - **`toSpec`**: `FramePresPrehom` from `ProjPres R` to `SpecPres R` (forgetful map on carriers).
  - **`cover_BigSum`** / **`cover_FinSum`**: Homogeneous sums are covered by their summands.
  - **`ideal_cover`**: Radical membership implies a `ProjPres` cover.
  - **`hpow`**: Homogeneous power of a carrier.
  - **`hpow_pow`**: `(hpow x n).1 = pow x.1 (suc n)`.
  - **`cover_hpow`**: Every carrier is covered by its homogeneous powers.

#### Proj and Projective Scheme

- **`Proj`**: `PresentedFrame (ProjPres R)` — the projective spectrum as a locale.
- **`projRingedPres`**: `Scheme (Proj R)` — the projective spectrum is a scheme.
  - **`functor`**: Functor from `(ProjPres R)^op` to `CRingBicat` sending a homogeneous element `a` to `HomogenLocRing (powers a.1)`.
  - **`natTrans`**: Natural transformation relating the projective functor to the affine functor composed with `toSpec`.
  - **`natTrans-isEmb`**: The natural transformation components are embeddings.
  - **`subSheaf-lem`**: Sub-sheaf condition for the projective structure sheaf.
  - **`functorial`**: Functoriality of the homogeneous localization map.
  - **`sfunc`** / **`shom`** / **`smap`** / **`shom-coh`**: Helpers for constructing and verifying the functorial ring homomorphisms between homogeneous localizations.
  - **`fromSubRing`** / **`toSubRing`**: Conversion between sub-ring membership and existence of lifts.
