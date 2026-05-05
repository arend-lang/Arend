### AG.Scheme

This module defines the Zariski spectrum, ringed locales, and schemes in the locale-theoretic setting.

#### Spectrum Presentation (`SpecPres`)

- **`SpecPres`**: `FramePres R` for a commutative ring `R`, with `conj = *` and basic covers from sums `a + b`.
  - **`cover_ideal`**: A cover implies the element is in the radical of the closure of generators.
  - **`ideal_cover`**: Membership in the radical implies a cover.
  - **`cover_ide`**: Every element is covered by `1`.
  - **`cover_pow`**: Every element is covered by its powers.
  - **`cover_bigSum`**: `BigSum l` is covered by the array `l`.
- **`Spec`**: `Locale` defined as `PresentedFrame (SpecPres R)`.

#### Ringed Locales

- **`RingedLocale`**: Class with fields `L : Locale` and `R : VSheaf CRingCat L`.
  - **`fromSheaf`**: Coercion from a sheaf on a locale.
  - **`equality1`** / **`equality2`** / **`equality3`**: Equivalent characterizations of equality between ringed locales (via transport, direct image, and isomorphisms).
- **`RingedLocaleHom`**: Morphism class with `f : FrameHom Cod Dom` and `f# : NatTrans` (structure sheaf pullback).
  - **`ext`**: Extensionality for ringed locale morphisms.
- **`RingedLocalePrecat`**: `Precat RingedLocale` with composition and identity.
  - **`forget`**: Forgetful functor to `LocaleCat`.
  - **`homMap`** / **`homMap-lem`** / **`homMap-lem2`**: Transport of ring homomorphisms along equality of morphisms.
  - **`homMap-natural`**: Natural transformation from `homMap`.
  - **`natTrans-inv`**: Inverse natural transformation from an isomorphism.
  - **`homMap_o`** / **`homMap_o-right`** / **`homMap_o-left`**: Composition lemmas for `homMap`.
  - **`fromIso`**: Extracts a sheaf isomorphism from a ringed locale isomorphism.
  - **`isotoid`**: Univalence for `RingedLocalePrecat`.

#### Locally Ringed Locales

- **`LocallyRingedLocale`**: Extends `RingedLocale` with `isNonTrivial` (stalks are nontrivial) and `isLocallyRinged` (stalks are local rings).
  - **`EitherInv`**: Either `x` or `x + 1` is invertible in the restricted ring.
- **`RingedFramePres`** / **`LocallyRingedFramePres`**: Presentation-level versions with `isNonTrivialPres` and `isLocallyRingedPres`.
  - **`limInv`**: Invertibility lifts through limits.
  - **`toPresented`**: Converts presentation-level local data to presented locale data.

#### Affine Ringed Locale

- **`ringedLocaleFromPres`**: Constructs `RingedLocale` from `RingedFramePres`.
- **`locallyRingedLocaleFromPres`**: Constructs `LocallyRingedLocale` from `LocallyRingedFramePres`.
- **`affineRingedPres`**: `LocallyRingedFramePres` for `SpecPres R`, with structure sheaf given by localizations `LocRing (powers a)`.
  - **`functor`**: Functor from `(SpecPres R)^op` to `CRingBicat` sending `a` to `LocRing (powers a)`.
  - **`functorial`**: Constructs ring homomorphisms between localizations from radical containment.
  - **`functorial-lem`** / **`functorial-lem-div`**: Explicit formulas for the functorial map.
  - **`emptyMatchingFamily`**: Matching families over empty covers are contractible.
  - **`common-denom`** / **`pow-lin-comb`**: Technical lemmas for common denominators and power linear combinations.
  - **`localization-lem`**: Equivalence characterization of matching families via localizations.
- **`affineRingedLocale`**: `LocallyRingedLocale` for a commutative ring `R`.

#### Schemes

- **`Scheme`**: Class extending `LocallyRingedLocale` with `isScheme` (locally affine cover).
- **`affineScheme`**: `Scheme` instance for any `CRing R`, with `locale_iso`, `restrict_map`, and `restrict_iso` helpers.
