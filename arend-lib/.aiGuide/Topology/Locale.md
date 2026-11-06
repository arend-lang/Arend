### Topology.Locale

Locales (point-free topological spaces): complete Heyting algebras (frames) with their morphisms, nuclei (sublocales), and compactness, regularity and overtness notions.

A **Locale** is a complete lattice that is also a Heyting algebra (so finite meets distribute over arbitrary joins) and a `Site`; morphisms (`FrameHom`) preserve top, finite meets, and arbitrary joins. `LocaleHom L M` is `FrameHom M L`, so that morphisms point in the geometric (continuous-map) direction. Sublocales are encoded as **nuclei**: monotone, inflationary, idempotent, meet-preserving operators on a frame, whose fixpoints form a sublocale. Arbitrary joins come in three forms: `Join C` over a subset `C`, `SJoin f C` over the image of a subset under `f`, and `IJoin f` over an indexed family. The file develops compactness, regularity, overtness, open/closed sublocales, the way-below relation `<<` and the rather-below relation `<=<`. The categories (`FrameCat`, `LocaleCat`), presentations by sites and Hausdorff conditions live in the modules `Topology.Locale.LocaleCat`, `Topology.Locale.PreorderSite` and `Topology.Locale.HausdorffLocale`.

#### Locale Class and Frame Structure

- **`Locale`**: Class extending `CompleteLattice`, `HeytingAlebra`, and `Site`. Binary joins are derived from arbitrary `Join`, meets of arbitrary families are joins of all lower bounds, `x --> y` is the join of all `z` with `z ∧ x <= y`, and the site cover is `isCover x C := x <= Join` of the elements below `x` that `C` contains. The infinite distributive law is the field `Join-ldistr>=`.
- **`Locale.Join-ldistr>=`**: The field `e ∧ Join C <= SJoin (e ∧) C`, one half of the infinite distributive law.
- **`Locale.Join-ldistr`**, **`Locale.Join-rdistr`**, **`Locale.Join-rdistr>=`**, **`Locale.Join-distr`**, **`Locale.Join-distr>=`**: Meet distributes over `Join C` on the left, on the right, and in both variables at once (the joint form joins over pairs `(a,b)` with `a` in `C` and `b` in `D`); the `>=` versions are the inequalities.
- **`Locale.SJoin-ldistr>=`**, **`Locale.SJoin-ldistr`**, **`Locale.SJoin-rdistr>=`**, **`Locale.SJoin-rdistr`**, **`Locale.SJoin-distr>=`**, **`Locale.SJoin-distr`**: The same distributive laws for `SJoin f P`.
- **`Locale.IJoin-ldistr>=`**, **`Locale.IJoin-ldistr`**, **`Locale.IJoin-rdistr>=`**, **`Locale.IJoin-rdistr`**, **`Locale.IJoin-distr>=`**, **`Locale.IJoin-distr`**: The same distributive laws for indexed joins `IJoin f`.

#### Way-Below, Compactness, Local Compactness

- **`Locale.<<`**: Way-below relation: `x << y` iff every cover of `y` admits a finite subcover of `x`.
- **`Locale.<<-char`**: `x << y` iff every subset `U` whose join is above `y` has a finite subset `V` with `x <= Join V`.
- **`Locale.<<_join`**: If `a << b` and `c << d` then `a ∨ c << b ∨ d`.
- **`Locale.<<_IJoin`**: If `x << y` and `y <= IJoin g`, then finitely many members of the family `g` already cover `x` (an array of indices `l` with `x <= IJoin (g ∘ l)`).
- **`Locale.IsCompactOpen`**: `a` is a compact open, i.e. `a << a`.
- **`Locale.compact_bottom`**, **`Locale.compact_join`**, **`Locale.compact_IJoin`**: `bottom`, binary joins, and joins of finite arrays of compact opens are compact opens.
- **`Locale.IsCompact`**: The locale is compact: `top` is a compact open.
- **`Locale.IsLocallyCompact`**: Every `x` is the join of the elements way below it.
- **`Locale.IsAlgebraic`**: Every `a` is the join of the compact opens below it.
- **`Locale.IsCoherent`**: The locale is algebraic and compact opens are closed under binary meets.

#### Heyting Implication and Negation

The implication `-->`, its basic lemmas (`exponent-monotone`, `<=-eval`, `top_exponent`) and negation come from `HeytingAlebra` in `Order.HeytingAlgebra`.

- **`discreteLocale.exponent`**: Heyting implication in `discreteLocale X` is pointwise: `P --> Q = \lam x => P x -> Q x`.

#### Rather-Below and Regularity

- **`Locale.<=<`**: Rather-below `x <=< y := top <= neg x ∨ y`, where `neg x` is the Heyting negation `x --> bottom`.
- **`Locale.make<=<`**: Constructor for `x <=< y` from `top <= neg x ∨ y`.
- **`Locale.compact_<=<`**: In a compact locale, `x <=< y` implies `x << y`.
- **`Locale.<=<w`**: Weak rather-below: the weakly-closed image of the open nucleus at `x` lies below the open nucleus at `y` in `NucleusFrame`.
- **`Locale.<=<w_<=`**: `x <=<w y` implies `x <= y`.
- **`Locale.IsRegularLocale`**: Every element is the join of the elements rather-below it.
- **`Locale.IsWeaklyRegularLocale`**: Every element is the join of the elements weakly rather-below it.
- **`Locale.compact-regular`**: A compact regular locale is locally compact.

#### Open and Closed Sublocales

- **`Locale.open`**: Nucleus `a --> __` defining the open sublocale at `a`.
- **`Nucleus.open_exp`**: Implication in the sublocale `(open a).locale` is computed as in `L` on underlying elements.
- **`Nucleus.open-isOpen`**: The open-sublocale embedding `(open a).map` is open in the morphism sense (`FrameHom.IsOpen`).
- **`Locale.closed`**: Nucleus `a ∨ __` defining the closed sublocale at `a`.
- **`Locale.closed-isClosed`**: The closed-sublocale nucleus satisfies `Nucleus.isClosed`.

#### Positivity and Overtness

- **`Locale.pHat`**: `Join (\lam _ => P)`: the proposition `P` reified as an element of the frame (`top` if `P` holds, `bottom` otherwise).
- **`Locale.pHat-impl`**: Monotonicity of `pHat` along implication.
- **`Locale.pHat_meet>=`**, **`Locale.pHat_meet`**: `pHat P ∧ pHat Q` equals (is below) `pHat (\Sigma P Q)`.
- **`Locale.pHat_meet-left`**: If `P` implies `W <= V`, then `pHat P ∧ W <= V`.
- **`Locale.pHat-char`**: `pHat P <= a` iff `P` implies `top <= a`.
- **`Locale.pHat-left`**: If `a <= pHat P` then `a` is covered by `P`-many copies of itself: `a <= IJoin (\lam (_ : P) => a)`.
- **`Locale.IsPositive`**: `a` is positive when every `pHat`-cover of `a` proves the proposition.
- **`Locale.positive_<=`**: Positivity is upward-closed.
- **`Locale.positive_cover`**: If a positive `a` lies below `Join C`, then `C` is inhabited.
- **`Locale.positive_Join`**, **`Locale.positive_Join.conv`**: On overt locales, positivity of `Join P` is equivalent to the existence of a positive member of `P`.
- **`Locale.positive_SJoin`**, **`Locale.positive_SJoin.conv`**: On overt locales, positivity of `SJoin f C` is equivalent to the existence of `a` in `C` with `f a` positive.
- **`Locale.IsOvert`**: Every element is below `pHat` of its own positivity.
- **`Locale.IsOvert_IJoin`**: In an overt locale, `a <= IJoin (\lam (_ : IsPositive a) => a)`.

#### Restriction (Slice Locales)

- **`Locale.restrict`**: Locale of elements below a fixed `a`, with derived operations; used to form the slice locale.
- **`Locale.restrict.map`**: Frame homomorphism `L -> restrict a` given by `b ↦ b ∧ a`.
- **`Locale.restrict.functor`**: Forgetful functor `restrict a -> L`.

#### Frame Homomorphisms

- **`FrameHom`**: Record extending `PosetHom` between locales; preserves top, finite meets, and arbitrary joins (`func-Join : func (Join C) = SJoin func C`). Several fields have defaults derived from each other.
- **`LocaleHom`**: `LocaleHom L M` is `FrameHom M L`, a morphism of locales in the geometric direction, with its own `id` and `∘`.
- **`FrameHom.func-SJoin`**, **`FrameHom.func-SJoin>=`**, **`FrameHom.func-IJoin`**, **`FrameHom.func-IJoin>=`**: Preservation of `SJoin` and of indexed joins.
- **`FrameHom.func-join`**, **`FrameHom.func-join>=`**: Preservation of binary joins.
- **`FrameHom.func-bottom`**, **`FrameHom.func-bottom>=`**: Preservation of bottom.
- **`FrameHom.func-pHat`**, **`FrameHom.func-pHat<=`**, **`FrameHom.func-pHat>=`**: Frame homomorphisms preserve `pHat P`.
- **`FrameHom.direct`**: Right adjoint of `func`, the join of all `x` with `func x <= y`.
- **`FrameHom.direct-<=`**, **`FrameHom.direct-unit`**, **`FrameHom.direct-counit`**, **`FrameHom.direct-adjoint`**: Monotonicity, unit, counit and the Galois adjunction `func x <= y <-> x <= direct y`.
- **`FrameHom.direct-meet`**, **`FrameHom.direct-top`**: `direct` preserves finite meets.
- **`FrameHom.func_direct_func`**: Triangle identity `func (direct (func x)) = func x`.
- **`FrameHom.image`**: Nucleus `direct ∘ func` whose locale is the image sublocale.
- **`FrameHom.factor`**: Frame map `image.locale -> Cod` factoring through the image.
- **`FrameHom.IsDense`**: `func x <= bottom` implies `x <= bottom`.
- **`FrameHom.dense_direct`**, **`FrameHom.direct_dense`**: Equivalent reformulation of density via `direct bottom <= bottom`.
- **`FrameHom.IsStronglyDense`**: Strong density: `func x <= pHat P` implies `x <= pHat P` for every proposition `P`.
- **`FrameHom.IsWeaklyClosed`**: `func` is surjective and its image nucleus is the smallest nucleus `j` with the weak-density extension property (`func x <= pHat P -> x <= j (pHat P)`).
- **`FrameHom.wclosed-image`**: Weakly-closed image nucleus, defined as the meet of all nuclei satisfying the weak-density extension property.
- **`FrameHom.wclosed-factor`**: Frame map `wclosed-image.locale -> Cod` factoring through the weak closure of the image.
- **`FrameHom.wclosed-factor-sdense`**: The factorization through the weakly-closed image is strongly dense.
- **`FrameHom.isClosed`**: The closed-embedding condition `direct (x ∨ func y) <= direct x ∨ y`.
- **`FrameHom.image_closed`**, **`FrameHom.closed-embedding_image`**: Equivalence between closedness of the morphism and of its image nucleus (under surjectivity).
- **`FrameHom.IsOpen`**: Existence of `z` with `direct (x --> func y) = z --> y` for all `y`: the open-embedding condition.
- **`FrameHom.surjective-split`**: For surjective `func`, `func (direct x) = x`.
- **`FrameHom.surj_nucleus`**: For surjective `func`, the factor `image.locale -> Cod` is an equivalence.
- **`FrameHom.functor`**: The underlying `SiteHom` of a frame homomorphism.
- **`FrameHom.direct_o`**: `direct` of a composite is the composite of `direct`s.
- **`sdense_positive`**, **`func_positive`**: Strongly dense maps preserve positivity; positivity of `f x` implies positivity of `x`.
- **`direct_positive`**: For strongly dense `f`, positivity of `f.direct x` implies positivity of `x`.
- **`sdense_overt`**: Strongly dense maps reflect overtness.

#### Nuclei

- **`Nucleus`**: Record over a locale `L` carrying an inflationary, idempotent, meet-preserving (and hence monotone) operator.
- **`Nucleus.nucleus-univ`**: Universal property `x <= j y -> j x <= j y`.
- **`Nucleus.nucleus_exponent`**: Implications into a `j`-fixed element are themselves `j`-fixed.
- **`Nucleus.exponent-func`**: `x --> y <= j x --> j y`.
- **`Nucleus.nucleus_meet-left`**: `j a ∧ b <= j (a ∧ b)`.
- **`Nucleus.Subtype`**: Sublocale carrier: elements fixed by the nucleus.
- **`Nucleus.locale`**: Locale structure on `Subtype`, with joins reflected through the nucleus.
- **`Nucleus.map`**: Surjective frame homomorphism `L -> j.locale` sending `x` to `j x`; `map.surjective` proves it is surjective.
- **`Nucleus.functor`**: Forgetful functor `locale -> L`.
- **`Nucleus.lift`**: A locale morphism `M -> L` with `f (j x) <= f x` factors through `locale`.
- **`Nucleus.lift_map`**: The factorization `lift f` composed with `map` is `f`.
- **`Nucleus.map_direct`**: For sublocale embeddings, `map.direct x = x.1`.
- **`Nucleus.locale_pHat`**, **`Nucleus.locale_IJoin`**: `pHat` and indexed joins in `locale` are computed in `L` and then passed through the nucleus.
- **`Nucleus.isClosed`**: Closedness of a single nucleus: `nucleus x <= nucleus bottom ∨ x`.
- **`Nucleus.IsDense`**: Density of a single nucleus: `nucleus bottom <= bottom`.
- **`Nucleus.exp`**: Heyting implication of nuclei in the frame of nuclei.
- **`Nucleus.exp-univ1`**, **`Nucleus.exp-univ2`**: Universal property of `exp`.

#### NucleusFrame (Frame of Nuclei)

- **`NucleusFrame`**: `CompleteLattice` instance on `Nucleus L`, ordered by `j <= j'` iff `j' x <= j x` for all `x` (reverse pointwise order); joins are pointwise meets.
- **`NucleusFrame.<=-map`**: Frame homomorphism `j.locale -> j'.locale` for `j' <= j`; `surjective` shows it is surjective.
- **`NucleusFrame.double-nucleus-left`**, **`NucleusFrame.double-nucleus-right`**: Absorption laws `k (j x) = k x` and `j (k x) = k x` when `k <= j`.
- **`NucleusFrame.wclosure_<=`**, **`NucleusFrame.wclosure-sdense`**, **`NucleusFrame.wclosure-inclusion`**: Properties of the weakly-closed-image construction: `j <= j.map.wclosed-image`, the induced map is strongly dense, and it is compatible with the inclusions.
- **`NucleusFrame.nucleus<=open`**, **`NucleusFrame.nucleus<=open.conv`**: `j <= open a` iff `top <= j a`.
- **`NucleusFrame.open_<=`**, **`NucleusFrame.open_<=.conv`**: `open` is monotone in `a`: `a <= b` iff `open a <= open b`.
- **`Nucleus.idNucleus`**, **`Nucleus.topNucleus`**: The identity nucleus and the constant-`top` nucleus.

#### Open vs Closed Relationships

- **`closed<=open`**, **`closed<=open.conv`**: `closed c <= open a` iff `top <= c ∨ a`.
- **`open<=closed`**, **`open<=closed.conv`**: `open a <= closed c` iff `c ∧ a <= bottom`.
- **`closed-compact`**: If `N` is a closed nucleus with `N <= open a` for some `a << top`, then `N.locale` is compact.

#### Discrete Locale

- **`discreteLocale`**: Locale of subsets of a set `X`, with all operations pointwise.
- **`discreteLocale.exponent`**: Heyting implication in `discreteLocale X` is pointwise.
- **`overt=open`**: A locale `L` is overt iff its terminal map `discreteLocale.terminalMap L` is an open morphism.

#### Way-Below and Rather-Below Lemmas

- **`<<_<=`**: `<<` implies `<=`.
- **`<<-left`**, **`<<-right`**: `<<` is monotone in both arguments.
- **`<=<_<=`**: `<=<` implies `<=`.
- **`LocaleRatherBelow`**: `<=<` is a `RatherBelow` relation: bounded by top, monotone on both sides, and closed under finite meets.
