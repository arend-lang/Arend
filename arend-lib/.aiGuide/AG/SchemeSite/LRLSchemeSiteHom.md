### AG.SchemeSite.LRLSchemeSiteHom

Morphisms from a locally ringed locale to (the scheme of) a saturated scheme site, described on the level of the site, and the equivalence with morphisms of locally ringed locales.

An `LRLSchemeSiteHom L S` from a locally ringed locale `L` to a saturated scheme site `S` is a monotone map `f*` from the opens of `S` to the opens of `L` together with a natural transformation `f#` of rings of sections, such that the images of the opens cover `L`, meets are covered, and an element of `S.R a` that is invertible on `c <= f* a` and is a localization element for `b <= a` forces `c <= f* b`. Such data induces a morphism of sites (`toPreorderSiteHom`) and a morphism of locally ringed locales `L -> S.toScheme` (`toLRLHom`), and is an isomorphism under conditions on `f*` and `f#` (`toLRLHom-iso`). The module shows that `LRLSchemeSiteHom L S` is equivalent to the morphisms of locally ringed locales `L -> S.toScheme` (`LRL-equiv`), and that `SchemeSiteHom T S` is equivalent to `LRLSchemeSiteHom T.toScheme S` (`SchemeSiteHom-equiv`); it also defines composition with morphisms of locally ringed locales and with prehomomorphisms of sites, and proves naturality.

#### The record and its morphism of locally ringed locales

- **`LRLSchemeSiteHom`**: Record of a monotone map `f* : PreorderHom S L` and a natural transformation `f# : S.R -> L.R.F ∘ f*.op`, such that `top <= IJoin f*` (`isLRLSchemeSiteHom-top`), `f* a ∧ f* b <= SJoin f* {c | c <= a, c <= b}` (`isLRLSchemeSiteHom-meet`), and, if `S.R b` localizes `S.R a` at `x`, `c <= f* a` and the image of `x` is invertible on `c`, then `c <= f* b` (`isLRLSchemeSiteHom-loc`).
- **`LRLSchemeSiteHom.toPreorderSiteHom`**: The morphism of preorder sites from `S.toSite` to the site of the locale `L` induced by `f*`.
- **`LRLSchemeSiteHom.toLRLHom`**: The associated morphism of locally ringed locales `L -> S.toScheme`: the frame map is the adjoint of `toPreorderSiteHom`, and `f#` is obtained by gluing the maps `f# a` over covers.
- **`LRLSchemeSiteHom.toLRLHom_f#-equiv`**: If every `f# a` is an equivalence of rings, then so is the sheaf component `toLRLHom.f# U` at every open `U` of the locale of `S`.
- **`LRLSchemeSiteHom.toLRLHom-iso`**: `toLRLHom` is an isomorphism provided the opens of `S` are determined by covers (`f*i`), every open of `L` is a join of images of `f*` (`f*s`), and every `f# a` is an equivalence.
- **`LRLSchemeSiteHom.fromLRLHom`**: Converts a morphism of locally ringed locales `L -> S.toScheme` to an `LRLSchemeSiteHom` by restricting along the embedding of the site into its locale.
- **`LRLSchemeSiteHom.fromSchemeSiteHom`**: Converts a `SchemeSiteHom X Y` between saturated scheme sites to an `LRLSchemeSiteHom X.toScheme Y`.
- **`LRLSchemeSiteHom.toSchemeSiteHom`**: Converts an `LRLSchemeSiteHom T.toScheme S` back to a `SchemeSiteHom T S`.

#### Equivalences and naturality

- **`LRLSchemeSiteHom.SchemeSiteHom-equiv`**: `fromSchemeSiteHom` and `toSchemeSiteHom` form a quasi-equivalence between `SchemeSiteHom T S` and `LRLSchemeSiteHom T.toScheme S`.
- **`LRLSchemeSiteHom.LRL-equiv`**: `toLRLHom` is a quasi-equivalence between `LRLSchemeSiteHom L S` and `LocallyRingedLocaleHom L S.toScheme`, with inverse `fromLRLHom`.
- **`LRLSchemeSiteHom.id_toLRL`**: `fromSchemeSiteHom` of the identity morphism of `S` is `fromLRLHom` of the identity of `S.toScheme`.
- **`LRLSchemeSiteHom.toSchemeSiteHom-natural`**: For `f : LRLSchemeSiteHom S.toScheme T` and a site morphism `g : T -> U`, `g ∘ toSchemeSiteHom f = toSchemeSiteHom (g ∘r f)`.
- **`LRLSchemeSiteHom.fromSchemeSiteHom-natural`**: `fromSchemeSiteHom` takes a composite of site morphisms `g ∘ f` to `g ∘r fromSchemeSiteHom f`.
- **`LRLSchemeSiteHom.toLRLHom_toSchemeSiteHom`**: For `f : LRLSchemeSiteHom X T` and `g : LRLSchemeSiteHom T.toScheme U`, `g ∘l f.toLRLHom = toSchemeSiteHom g ∘r f`.
- **`LRLSchemeSiteHom.fromSchemeSiteHom_toLRLHom`**: For `f : LRLSchemeSiteHom X T` and a site morphism `g : T -> U`, `fromSchemeSiteHom g ∘l f.toLRLHom = g ∘r f`.
- **`LRLSchemeSiteHom.equals`**: Two `LRLSchemeSiteHom`s `f g` are equal if `f a <= g a` and `g a <= f a` for all `a` (so `f*` agree) and the sections agree after the restriction `L.R.F.Func (p1 a)`.
- **`LRLSchemeSiteHom.equals.byRingHom`**: The same as `equals`, with the section condition stated as an equality of ring homomorphisms `L.R.F.Func (p1 a) ∘ g.f# a = f.f# a`.

#### Composition

- **`LRLSchemeSiteHom.compose-left`**: Composition `g ∘l f` of a morphism of locally ringed locales `f : L -> M` with an `LRLSchemeSiteHom g : M -> U`.
- **`LRLSchemeSiteHom.compose-right`**: Composition `g ∘r f` of an `LRLSchemeSiteHom f : L -> T` with a prehomomorphism of scheme sites `g : T -> U`.
- **`LRLSchemeSiteHom.toLRLHom-natural`**: `toLRLHom` is natural: `g.toLRLHom ∘ f = (g ∘l f).toLRLHom`.
- **`satPrehom_compose-right`**: Saturating the prehomomorphism changes nothing under `∘r`: `satPrehom g ∘r f = g ∘r f`.
