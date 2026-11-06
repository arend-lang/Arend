### AG

This directory formalizes algebraic geometry: affine and projective schemes built as locally ringed locales via frame presentations of the Zariski topology.

#### Affine Schemes and General Schemes

- **`Scheme.md`** — Schemes: locally ringed locales whose top element is covered by affine opens (`isLocallyAffine`).

#### Projective Schemes

- **`Projective.md`** — Proj construction of a graded commutative ring as a presented locale with structure sheaf glued from degree-zero parts of localizations at homogeneous elements, exhibited as a scheme via affine charts.

#### Scheme Sites

- **`SchemeSite.md`** — Scheme sites: preorder of opens with ring-valued sections, their site, locale and sheaf, saturated sites giving schemes, saturation `SatSite`, `maxSchemeSite`.

#### Ringed Locales and Affine Schemes

- **`RingedLocale.md`** — Ringed and locally ringed locales with their morphisms, principal and affine opens.
- **`AffineScheme.md`** — The spectrum `Spec R` of a commutative ring as a scheme, and the characterization of affine schemes among locally ringed locales via `specUnit`.

#### Subdirectories

- **`AffineScheme/`** — Spec as a fully faithful functor from commutative rings (opposite) to saturated scheme sites, and its reflectivity into locally ringed locales.
- **`RingedLocale/`** — The precategory and univalent category of ringed locales, with the forgetful functor to locales.
- **`SchemeSite/`** — Precategories of scheme sites and their morphisms with saturation, morphisms from locally ringed locales into scheme sites, and the fully faithful functor to locally ringed locales.
