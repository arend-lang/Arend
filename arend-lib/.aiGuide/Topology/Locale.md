### Topology.Locale

Locales (point-free topological spaces): complete distributive lattices satisfying the infinite distributive law, presented either directly or via generators and covering relations.

A **Locale** is a complete distributive lattice in which finite meets distribute over arbitrary joins; morphisms (`FrameHom`) preserve top, finite meets, and arbitrary joins. The category `LocaleCat` is the opposite of `FrameCat` so that morphisms point in the geometric (continuous-map) direction. Sublocales are encoded as **nuclei**: monotone, inflationary, idempotent, meet-preserving operators on a frame, whose fixpoints form a sublocale. Locales can also be presented by generators and basic covers (`FramePres`/`FrameUPres`), with `PresentedFrame` constructing the free locale on such a presentation as the reflector of the inclusion `FrameCat ↪ FramePresCat`. The file develops compactness, regularity, overtness, Hausdorff conditions, open/closed sublocales, the way-below relation `<<`, the rather-below relation `<=<`, and standard factorization systems (dense/closed, strongly-dense/weakly-closed).

#### Locale Class and Frame Structure

- **`Locale`**: Class extending `CompleteLattice`, `BoundedDistributiveLattice`, and `SiteWithBasis`. Joins are derived from arbitrary `Join`, finite meets distribute over arbitrary joins (`Join-ldistr>=`), and the basic site cover is `x <= Join (g i)`. Meets of arbitrary families are constructed as joins of all lower bounds.
- **`Join-ldistr`**, **`Join-rdistr`**, **`Join-rdistr>=`**, **`Join-distr`**, **`Join-distr>=`**: Finite-meet/arbitrary-join distributivity in the two variables and jointly.

#### Way-Below, Compactness, Local Compactness

- **`<<`**: Way-below relation: `x << y` iff every cover of `y` admits a finite subcover of `x`.
- **`isCompact`**: `top << top`.
- **`isLocallyCompact`**: Every element is the join of elements way-below it.

#### Heyting Implication and Negation

- **`-->`**: Frame implication `x --> y := SJoin (\lam z => z ∧ x <= y)`, the largest element whose meet with `x` lies below `y`.
- **`exponent`**: Adjunction `x ∧ y <= z <-> x <= y --> z`.
- **`exponent_monotone`**: `-->` is contravariant in the first argument, covariant in the second.
- **`exponent-double`**: Currying `x --> y --> z = x ∧ y --> z`.
- **`eval`**: Evaluation `(x --> y) ∧ x <= y`.
- **`exponent_meet`**: `-->` distributes over meet on the right.
- **`trueExponent`**: From `x <= y` deduce `top <= x --> y`.
- **`neg`**: Negation `neg x := x --> bottom`.
- **`neg-inverse`**: `neg` is contravariant.

#### Rather-Below and Regularity

- **`<=<`**: Rather-below `x <=< y := top <= neg x ∨ y` (the strong/well-inside relation).
- **`<=<w`**: Weak rather-below, defined via the weakly-closed image nucleus.
- **`IsRegularLocale`**: Every element is a join of elements rather-below it.
- **`IsWeaklyRegularLocale`**: Every element is a join of elements weakly rather-below it.

#### Open and Closed Sublocales

- **`open`**: Nucleus `a --> __` defining the open sublocale at `a`.
- **`open_exp`**: Implication in `open a` is computed pointwise.
- **`open-isOpen`**: The open-sublocale embedding is open in the morphism sense.
- **`closed`**: Nucleus `a ∨ __` defining the closed sublocale at `a`.
- **`closed-isClosed`**: The closed-sublocale embedding is closed.

#### Positivity and Overtness

- **`pHat`**: `Join (\lam (_ : P) => top)` — the proposition `P` reified as an element of the frame.
- **`pHat-impl`**: Monotonicity of `pHat` along implication.
- **`IsPositive`**: `a` is positive when every `pHat`-cover of `a` proves the proposition.
- **`positive_<=`**: Positivity is upward-closed.
- **`positive_cover`**: A positive element witnesses non-emptiness of any indexing set covering it.
- **`positive_Join`** (with converse `conv`): On overt locales, positivity of a join is equivalent to existence of a positive summand.
- **`IsOvert`**: Every element is covered by its positive part.
- **`overt_cover`**: Each element of an overt locale is covered by `Join` over the propositional witness of its own positivity.

#### Restriction (Slice Locales)

- **`restrict`**: Locale of elements below a fixed `a`, with derived operations; used to form the slice locale.
- **`restrict.map`**: Frame homomorphism `L -> restrict a` given by `b ↦ b ∧ a`.
- **`restrict.functor`**: Forgetful functor `restrict a -> L`.

#### Frame Homomorphisms

- **`FrameHom`**: Record extending `SetHom` between locales; preserves `<=`, top, finite meets, and arbitrary joins. Several fields have defaults derived from each other.
- **`func-join`**, **`func-join>=`**: Preservation of binary joins.
- **`func-bottom`**, **`func-bottom>=`**: Preservation of bottom.
- **`func-pHat`**, **`func-pHat<=`**, **`func-pHat>=`**: Frame homomorphisms preserve `pHat P`.
- **`direct`**: Right adjoint of `func`, `direct y := SJoin (\lam x => func x <= y)`.
- **`direct-<=`**, **`direct-unit`**, **`direct-counit`**, **`direct-adjoint`**: Galois adjunction between `func` and `direct`.
- **`direct-meet`**, **`direct-top`**: `direct` preserves finite meets.
- **`func_direct_func`**: Triangle identity `func ∘ direct ∘ func = func`.
- **`image`**: Nucleus `direct ∘ func` whose locale is the image sublocale.
- **`factor`**: Frame map `image.locale -> Cod` factoring through the image.
- **`IsDense`**: `func x <= bottom` implies `x <= bottom`.
- **`dense_direct`** / **`direct_dense`**: Equivalent reformulation of density via `direct bottom <= bottom`.
- **`isStronglyDense`**: Density extended to all `pHat P` instead of just `bottom`.
- **`isWeaklyClosed`**: Surjectivity together with a universal property characterizing weakly-closed embeddings.
- **`wclosed-image`**: Weakly-closed image nucleus, defined as the meet of all nuclei satisfying the weak-density extension property.
- **`wclosed-factor`**, **`wclosed-factor-sdense`**: Factorization through the weakly-closed image is strongly dense.
- **`isClosed`**: The closed-embedding condition `direct (x ∨ func y) <= direct x ∨ y`.
- **`image_closed`** / **`closed-embedding_image`**: Equivalence between closedness of the morphism and of its image (under surjectivity).
- **`IsOpen`**: Existence of `z` with `direct (x --> func y) = z --> y` for all `y` — open-embedding condition.
- **`surjective-split`**: For surjective `func`, `func ∘ direct = id`.
- **`surj_nucleus`**: For surjective `func`, the factor `image.locale -> Cod` is an equivalence.
- **`functor`**: Underlying functor between locales-as-categories.
- **`direct_o`**: `direct` of a composite is the composite of `direct`s.

#### Nuclei

- **`Nucleus`**: Record carrying an inflationary, idempotent, meet-preserving (and hence monotone) operator on a locale.
- **`nucleus-univ`**: Universal property `x <= j y -> j x <= j y`.
- **`nucleus_exponent`**: Implications into a `j`-fixed element are themselves `j`-fixed.
- **`exponent-func`**: `j` preserves implication up to `j`.
- **`Subtype`**: Sublocale carrier — elements fixed by the nucleus.
- **`locale`**: Locale structure on `Subtype`, with joins reflected through the nucleus.
- **`map`**: Surjective frame homomorphism `L -> j.locale` sending `x` to `j x`; `map.surjective` proves it is surjective.
- **`functor`**: Forgetful functor `locale -> L`.
- **`map_direct`**: For sublocale embeddings, `map.direct x = x.1`.
- **`isClosed`**, **`IsDense`**: Closed/dense conditions phrased on a single nucleus.
- **`Nucleus.exp`**: Heyting implication of nuclei in the frame of nuclei.
- **`Nucleus.exp-univ1`**, **`Nucleus.exp-univ2`**: Universal property of `exp`.

#### NucleusFrame (Frame of Nuclei)

- **`NucleusFrame`**: Locale instance on `Nucleus {L}`, ordered pointwise; meets pointwise, top constant, and joins via Meet over nuclei dominating the family.
- **`<=-map`**: Frame homomorphism `j.locale -> j'.locale` for `j <= j'`; `surjective` shows it is surjective.
- **`double-nucleus-left`**, **`double-nucleus-right`**: Absorption laws `k (j x) = k x` and `j (k x) = k x` when `j <= k`.
- **`wclosure_<=`**, **`wclosure-sdense`**, **`wclosure-inclusion`**: Properties of the weakly-closed-image construction inside `NucleusFrame`.
- **`nucleus>=open`** (with **`conv`**): `open a <= j` iff `top <= j a`.
- **`open_<=`** (with **`conv`**): `open` is order-reversing in `a`.
- **`intersection-pullback`**: The intersection of two nuclei realises the pullback of the corresponding sublocale embeddings in `LocaleCat`.

#### Open vs Closed Relationships

- **`closed>=open`** (with **`conv`**): `closed c >= open a` iff `top <= c ∨ a` (open complement of a closed).
- **`open<=closed`** (with **`conv`**): `open a >= closed c` iff `c ∧ a <= bottom`.
- **`closed-compact`**: A closed sublocale of a compact-like nucleus, dominated by an `open a` with `a << top`, is itself compact.

#### FrameCat

- **`FrameCat`**: Category of locales with frame homomorphisms; identity, composition, and univalence supplied.
- **`FrameCat.equiv_iso`**: Promotes a frame homomorphism that is an `Equiv` of underlying sets to an iso in `FrameCat`.

#### Frame Presentations (FramePres)

- **`FramePres`**: Class of a meet operation `conj` together with a `BasicCover` relation; a presentation of a frame by generators and covers.
- **`FramePres.<<`**: Way-below relation phrased on covers in a presentation.
- **`SCover`**: Covering by elements of a predicate `U`.
- **`isLocallyCompact`** (on a presentation): Every element is `SCover`-covered by elements way-below it.
- **`isPositive`** / **`isOvert`**: Presentation-level analogues of positivity and overtness.
- **`Indexing`**: Predicate for re-indexing a family of generating covers; **`indexing-make`**, **`indexing-transport`** support transporting properties along reindexing.

#### Cover Relations

- **`Cover`**: Inductively defined cover relation between an element and a family in a `FramePres`, closed under basic covers, inclusion, transitivity, projections from `conj`, idempotence, commutativity, and left-distributivity.
- **`Cover1`**: Single-element cover, used as the preorder.
- **Companion lemmas**: `cover-trans1`, `cover-proj2`, `cover-index`, `cover-ldistr'`, `cover-rdistr'`, `cover-rdistr`, `cover-conj`, `cover-conj1`, `cover-prod`, `map`, `cover_top`, `cover_<=`, `cover-inj_<=` — standard manipulations of covers.
- **`Cover'`**: Equivalent reformulation closed under product/projection forms; `cover-cover'` and `cover'-cover` show equivalence to `Cover`.

#### Presentation Categories

- **`framePresPreorder`**: Preorder on `P` with `<=` defined as `Cover1`.
- **`framePresSite`**: Site structure on `framePresPreorder P`, with pullbacks given by `conj` and the `BasicCover` lifted up to `Cover1`.
- **`FramePresPrehom`**: Maps preserving `conj` and basic covers.
- **`FramePresHom`**: Prehomomorphism additionally surjective on covers (`func-image`).
- **`FramePresCat`**: Category of frame presentations and homomorphisms.

#### Presented Frame

- **`PresentedFrame`**: Locale of "open subsets" — predicates closed under `SCover` — built from a presentation `P`.
- **`Opens`**: Carrier of `PresentedFrame`.
- **`closure`**: Open generated by an indexed family.
- **`<=` (on Opens)**: Pointwise inclusion.
- **`closure<=`**: Universal property of `closure`.
- **`embed`**: Inclusion `P -> PresentedFrame P` of generators.
- **`embed<=`**: Universal property of `embed` against an open.
- **`element_join`**: Every open is the join of its embedded points.
- **`surj-map`**: Lifts a frame map into `PresentedFrame P` to a surjective preimage on each open.
- **`Cover_embed`** / **`embed_Cover`**: Equivalence between `Cover1` in `P` and `<=` on `embed`.
- **`embed_meet`**: `embed a ∧ embed b = embed (conj a b)`.
- **`func-equality`**, **`func-equality_ext`**: Frame homomorphisms out of `PresentedFrame P` are determined by their values on generators.

#### Unital Presentations

- **`FrameUPres`**: Presentation with a distinguished `unit` generator covered by everything.
- **`FrameUPresHom`**: Homomorphism that preserves `unit`.
- **`FrameUPresCat`**: Category of `FrameUPres`.
- **`FrameUPresCocompleteCat`**: Cocomplete-category instance for `FrameUPres`, computing colimits via the syntactic term construction `FTerm`.
- **`FTerm`**: Higher-inductive type of formal terms over a diagram of unital presentations, modulo functoriality, `conj`, and `unit` relations.
- **`colimit-obj`**: `FrameUPres` structure on `FTerm`.
- **`fpair`**, **`term-product`**: Construction and characterization of binary product terms used for product locales.
- **`colimitMap`**, **`colimit-func`**, **`colimit-univ`**, **`colimit-cone`**, **`colimit-univ-eq`**: Cocone, mediating map, and uniqueness for colimits in `FrameUPresCat`.

#### Reflective Subcategories

- **`FrameUnitalSubcat`**: Fully faithful functor `FrameCat -> FrameUPresCat` viewing a locale as its own unital presentation.
- **`FrameUnitalSubcat.Func-inverse`**: Computes the action of the inverse on a unital presentation morphism.
- **`FrameReflectiveSubcat`**: Reflective inclusion `FrameCat -> FramePresCat` with reflector `PresentedFrame`.
- **`locale_cover`**: Translates `Cover` in `F L` to `<=` in `L`.
- **`adjointMap`**: Adjoint frame homomorphism `PresentedFrame X -> Y` from a presentation map `X -> F Y`.
- **`adjointMap_embed`**: `adjointMap f` extends `f` on generators.
- **`FrameUnitalReflectiveSubcat`**: Reflective inclusion `FrameCat -> FrameUPresCat`, reusing `PresentedFrame` as the reflector.

#### Bicompleteness of FrameCat / LocaleCat

- **`FrameBicat`**: `BicompleteCat` instance on `FrameCat`, with limits computed as compatible families of elements and colimits via the unital reflector and `FrameUPresCocompleteCat`.
- **`FrameBicat.limit-obj`**, **`FrameBicat.pullback-obj`**: Concrete limit and pullback locales.
- **`FrameBicat.proj1`**, **`FrameBicat.proj2`**: Projections of the pullback.
- **`FrameBicat.coneMap_finj`**: Identifies the colimit cone in terms of `embed ∘ finj`.
- **`LocaleCat`**: Opposite category, the bicomplete category of locales. The terminal locale is `discrete (\Sigma)`; the terminal map sends `P` to `pHat (P ())`.
- **`LocaleCat.terminal-direct`**: Right adjoint of the terminal map sends `x` to the predicate `top <= x`.

#### Discrete Locale

- **`discrete`**: Locale of subsets of a set `X`, with all operations pointwise.
- **`discrete.functor`**: Functor `SetCat -> LocaleCat` sending a set to its powerset locale and a function to inverse-image.
- **`discrete.exponent`**: Heyting implication in `discrete X` is pointwise.

#### Pullback Stability and Composition

- **`pullback_open`**: Open morphisms are stable under pullback.
- **`pullback_sdense`**: Strong density is stable under pullback along an open morphism.
- **`overt=open`**: A locale `L` is overt iff its terminal map is an open morphism.
- **`open-comp`**: Open morphisms are closed under composition.

#### Surjections and Regular Monomorphisms

- **`regular_surj`** (with helper `regular_surj-lemma`): Regular monos in `LocaleCat` correspond to surjective frame homomorphisms.
- **`surj_regular`**: Surjective frame maps are regular monos.
- **`surj_equiv`**: For surjective `f`, the factor through the image is an iso of locales; **`surj_equiv.map-comm`** shows it commutes with `image.map`.

#### Hausdorff Conditions and Factorization Systems

- **`isHausdorffLocale`**: Hausdorff condition phrased as a generalized closure property of the diagonal.
- **`isHausdorffLocale.generalized`**: Auxiliary general statement parametrized by an arbitrary locale-with-two-maps over `L`.
- **`isHausdorffLocale.extend`**: Auxiliary "extension" used to express the closure property.
- **`isHausdorffLocale.diagonal_func`**, **`diagonal_direct`**: Concrete description of the diagonal frame map and its right adjoint.
- **`isHausdorffLocale.diagonal-isClosed`**: For Hausdorff `L`, the image nucleus of the diagonal is closed.
- **`regular_Hausdorff`** (with `generalized`): Every regular locale is Hausdorff.
- **`isWeaklyHausdorff`**: Weak Hausdorff condition — diagonal is weakly closed.
- **`wregular_wHausdorff`**: Weakly regular implies weakly Hausdorff.
- **`dense_closed_ofs`**: Orthogonal factorization system on `LocaleCat`: dense maps vs. closed surjections.
- **`sdense_wclosed_ofs`**: Orthogonal factorization system: strongly dense vs. weakly closed maps.

#### Properties from Presentations to Locales

- **`overt-fromPres`**: Overtness lifts from a presentation to its `PresentedFrame`.
- **`sdense_positive`**, **`func_positive`**: Strongly dense maps reflect/preserve positivity.
- **`sdense_overt`**: Strongly dense maps reflect overtness.
- **`sdense-fromPres`**: Sufficient condition on a presentation map for `adjointMap` to be strongly dense.
- **`sdense-comp`**: Strong density is closed under composition.
- **`<<-fromPres`**: Way-below in a presentation transfers to `PresentedFrame`.
- **`locallyCompact-fromPres`**: Local compactness lifts from a presentation.
- **`regular-fromPres`**: Sufficient presentation-level condition for regularity of `PresentedFrame P`.

#### Way-Below and Rather-Below Lemmas

- **`<<_<=`**: `<<` implies `<=`.
- **`<<-left`**, **`<<-right`**: `<<` is monotone in both arguments.
- **`<=<_<=`**: `<=<` implies `<=`.
- **`LocaleRatherBelow`**: `<=<` is a `RatherBelow` relation: bounded by top, monotone on both sides, and closed under finite meets.

#### Way-Below Predicates and Combinatorics

- **`wayBelowPredicate`**: Generic engine deriving way-below–style finite-cover statements from a relation `R` together with monotonicity, density, and a commutation-with-covers axiom.
- **`wayBelowPredicate.comm-lem`**: Iterative refinement lemma, replacing the first `k` entries of an `n`-element cover by `R`-related lists.
- **`wayBelowPredicate.++_singleton`**, **`split_++`**, **`split`**, **`unsplit`**, **`unsplit2`**: Combinatorial helpers for flattening arrays of arrays produced by the refinement.
- **`wayBelowPredicate.indexing-basic`**: Bridges `Indexing` reformulations of basic covers into the way-below engine.
