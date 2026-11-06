### AG.RingedLocale

Ringed locales (a locale with a sheaf of commutative rings), their morphisms, and locally ringed locales, together with the algebraic-geometry notions defined on them: principal opens, affine opens and affine schemes. It also defines ringed and locally ringed sites, whose associated locale (`LocallyRingedSite.toLocale`) is a locally ringed locale.

#### Ringed locales

- **`RingedLocale`**: Record bundling a locale `L` with a sheaf of commutative rings `R : VSheaf CRingCat L`. Coercible to its underlying locale.
- **`RingedLocale.fromSheaf`**: Coerces a `VSheaf CRingCat L` to a `RingedLocale`.
- **`RingedLocale.GlobalSections`**: The ring `R top` of global sections.
- **`RingedLocale.R-equals`**: The sheaf `R` is separated: if `a <= Join U` and two sections `x y : R a` have equal restrictions to every `b` below `a` and below some member of `U`, then `x = y`.
- **`RingedLocale.Inv-restrict`**: If the restriction of a global section to `a` is invertible, then so is its restriction to any `b <= a`.
- **`RingedLocale.restrict`**: The ringed locale obtained by restricting to the open `a` (the locale `L.restrict a` with the restricted sheaf).
- **`RingedLocaleHom`**: A morphism `Dom -> Cod` consists of a frame map `f* : FrameHom Cod Dom` together with a natural transformation `f# : Cod.R -> direct_image_locale f* Dom.R` of sheaves.
- **`RingedLocaleHom.IsIso`**: The morphism is an isomorphism: `f*` is an equivalence and every component `f# b` is an equivalence of rings.
- **`RingedLocaleHom.fromHomEq`**: From `f = g` derives the inequality `f a <= g a` used to compare sheaf actions.

#### Principal and affine opens

- **`RingedLocale.IsPrincipalOpenAt`**: `IsPrincipalOpenAt (ba : b <= a) x` says that `b` is the principal open of the section `x : R a`: `x` is invertible over `b`, and `b` contains every `c <= a` over which `x` is invertible.
- **`RingedLocale.principal-id`**: The unit `1 : R a` has `a` itself as its principal open.
- **`RingedLocale.principal-o`**: In an affine open `a`, if `b <= a` is the principal open of `x` and `c <= b` is the principal open of `y : R b`, then `c` is the principal open of some section `z : R a`.
- **`RingedLocale.principal-factor-right`**: If `c` is the principal open of `x : R a` through `c <= b <= a`, then it is the principal open of the restriction of `x` to `b`.
- **`RingedLocale.principal_meet-left`**: If `c` is the principal open of `y : R a`, then `b ∧ c` (as `b ∧ c <= b`) is the principal open of the restriction of `y` to `b`.
- **`RingedLocale.principal_meet-right`**: If `b` is the principal open of `x : R a`, then `b ∧ c` (as `b ∧ c <= c`) is the principal open of the restriction of `x` to `c`.
- **`RingedLocale.IsPrincipalOpenAt-Inv`**: If `b` is the principal open of `x`, then `x` restricts to an invertible element over every `c <= b`.
- **`RingedLocale.principalOpen`**: The principal open of `x : R a`, the join of all `c <= a` over which `x` is invertible.
- **`RingedLocale.principalOpen_<=`**: `principalOpen x <= a`.
- **`RingedLocale.principalOpen-principal`**: `principalOpen x` is the principal open of `x` (`IsPrincipalOpenAt`).
- **`RingedLocale.IsPrincipalOpenAt-char`**: `IsPrincipalOpenAt ba x` holds iff `b = principalOpen x`.
- **`RingedLocale.IsAffineOpen`**: `a` is an affine open: (1) for every principal open `b <= a` of `x : R a`, `R b` is the localization of `R a` at the powers of `x`; (2) a radical condition for covers of principal opens by opens that are localizations of principal opens; (3) every `b <= a` is covered by principal opens of sections of `R a`.
- **`RingedLocale.IsAffineScheme`**: The whole locale is an affine open (`IsAffineOpen top`).
- **`RingedLocale.principal-localization`**: In an affine open `a`, `R b` is the localization of `R a` at the powers of `x` whenever `b` is the principal open of `x`.
- **`RingedLocale.principal-affine`**: A principal open of an affine open is affine.
- **`RingedLocale.restrict-affine`**: The restriction `restrict a` is an affine scheme iff `a` is an affine open.

#### Locally ringed locales

- **`LocallyRingedLocale`**: Extends `RingedLocale` with two conditions: `isLRLNonTrivial` (if `0 = 1` over `a`, then `a` is bottom) and `isLocallyRinged` (every section is, on a covering family, either invertible or has invertible `+1`-shift, encoding the local-ring stalk condition).
- **`LocallyRingedLocale.locallyRinged-split2_ide`**: If `x + y = 1` in `R a`, then `a` is covered by the opens over which `x` or `y` is invertible.
- **`LocallyRingedLocale.locallyRinged-split2_Inv`**: The same when `x + y` is merely invertible.
- **`LocallyRingedLocale.locallyRinged-split_Inv`**: If a finite sum `BigSum l` of sections of `R a` is invertible, then `a` is covered by the opens over which some summand is invertible.
- **`LocallyRingedLocale.locallyRinged-split_ide`**: The same when the finite sum equals `1`.
- **`LocallyRingedLocale.restrict`**: The restriction of a locally ringed locale to the open `a`.
- **`LocallyRingedLocaleHom`**: A morphism of locally ringed locales: a `RingedLocaleHom` satisfying `isLRLHomLocal`, i.e. if the image of a section `x` of `Cod.R a` is invertible over `c <= f* a`, then `c` is covered by the `f*`-images of opens `b <= a` over which `x` is invertible.

#### Ringed and locally ringed sites

- **`RingedSite`**: A preorder site `P` with a functor `R : P.op -> CRingBicat` (rings) that is a sheaf (`R-sheaf`).
- **`RingedSite.R-equals`**: Sections of `R a` that agree on the restrictions to all members below a cover `U` of `a` are equal (separatedness).
- **`LocallyRingedSite`**: A ringed site satisfying the local-ring conditions on covers: `isNonTrivialPres` (`0 = 1` over `a` implies the empty family covers `a`) and `isLocallyRingedPres` (for `x : R a`, `a` is covered by the `b` over which `x` or `x + 1` is invertible).
- **`LocallyRingedSite.toLocale`**: The associated locally ringed locale, on `SiteLocale P` with the sheaf `SiteLocaleSheaf R R-sheaf`.
- **`LocallyRingedSite.IsPrincipalOpenAt`**: Site version of principal opens: `x` is invertible over `b` and every `c <= a` over which `x` is invertible is covered by `b` (`Cover1 c b`).
- **`LocallyRingedSite.IsPrincipalOpenAt_toLocale`**: A principal open of the site, evaluated on `embedProj a y`, gives a principal open of the section `y` in `toLocale`.
- **`LocallyRingedSite.affineOpen`**: Criterion for `embed a` to be an affine open of `toLocale`, in terms of site-level versions of the three conditions in `RingedLocale.IsAffineOpen` (`ae`, `ap`, `aq`).
