### AG.Projective

Constructs the Proj scheme of a graded commutative ring as the scheme of a saturated site on the homogeneous elements of positive degree, with a structure sheaf given by degree-zero localizations.

This module realizes the classical Proj construction in the site-theoretic style: the basic opens are homogeneous elements of positive degree, ordered by divisibility (`b <= a` when `a` divides `b`). The structure sheaf sends `a` to `HomogenLocRing (powers a.1)` — the degree-zero part of the localization at powers of a homogeneous element — and is obtained as a subsheaf of the pullback of the affine structure sheaf along the canonical map `ProjPreorder.toSpec` into `Spec.satSite R`. The resulting site is shown to be a `SatSchemeSite`, which gives the scheme `Proj R`.

#### The Site of Homogeneous Elements

- **`ProjCarrier`**: The elements of a graded ring `R` that are homogeneous of positive degree (`isHomogen a (suc n)` for some `n`), the basic opens of `Proj R`.
- **`ProjPreorder`**: The preorder on `ProjCarrier R` where `a <= b` means that `b.1` divides `a.1`.
- **`ProjPreorder.toSpec`**: The monotone map `ProjPreorder R -> Spec.satSite R` sending a homogeneous element to its underlying ring element.

#### The Proj Scheme

- **`Proj`**: The scheme `Proj R` of a graded commutative ring, defined as the scheme of `Proj.site R`.

#### Structure Sheaf and Scheme Structure

- **`Proj.SpecStructureSheaf`**: The pullback of the affine structure sheaf of `Spec.satSite R` along `ProjPreorder.toSpec`.
- **`Proj.StructureSheaf`**: The structure sheaf of `Proj R`: `a ↦ HomogenLocRing (powers a.1)`, with restriction maps induced from `SpecStructureSheaf` (via `Proj.Func-homogen`).
