### Category

This directory formalizes category theory: precategories and (univalent) categories, functors and natural transformations, limits/colimits, adjunctions, and a range of derived structures (toposes, additive categories, presheaf categories).

#### Functors and Natural Transformations

- **`Functor.md`** — Functors, natural transformations, functor (pre)categories, and faithful/full/fully faithful refinements.
- **`Yoneda.md`** — The Yoneda embedding, Yoneda lemma, category of elements, density theorem, and presheaf categories as free cocompletions.

#### Limits and Colimits

- **`Limit.md`** — Cones, limits, colimits, and concrete shapes (products, equalizers, pullbacks, terminal objects), plus the layered hierarchy of (co)complete (pre)categories.
- **`KanExtension.md`** — Pointwise left and right Kan extensions of functors, and the Fubini-style relationship between iterated limits and Kan extensions.
- **`Factorization.md`** — Weak and orthogonal factorization systems, with lifts characterized by hom-set equivalences.

#### Adjunctions

- **`Adjoint.md`** — Adjoint functors via unit/counit and triangle identities, hom-set adjunction equivalences, and categorical equivalences.
- **`Coreflection.md`** — Object-wise right adjoints (coreflections), their characterization as terminal objects in comma categories, and packaging as full right adjoints.

#### Derived Categories

- **`Comma.md`** — The comma category `(F ↓ G)` of two functors with a common codomain, with forgetful functors and covariant action on natural transformations.
- **`Slice.md`** — The slice category `C/x` of objects over a fixed object, with a faithful forgetful functor.
- **`Product.md`** — The product `C × D` of two (pre)categories, with iso decomposition into component isos.
- **`Subcat.md`** — Subcategories presented by an indexing map, full subcategories from predicates, and reflective subcategories (with limit inheritance).
- **`Displayed.md`** — Displayed categories over a base, total Grothendieck-style categories, and displayed univalence.

#### Subobjects

- **`Subobj.md`** — Subobjects and regular subobjects as preorders under factorization, with pullbacks computing meets of regular subobjects.
- **`SubobjectPoset.md`** — The poset of subobjects of an object, with meet-semilattice (and dual join-semilattice) structure from pullbacks.

#### Cartesian and Closed Structure

- **`CartesianClosed.md`** — Cartesian closed precategories with exponential objects, currying, evaluation, internal hom, and the `Set` instance.
- **`Topos.md`** — Elementary toposes: subobject classifier, power objects, internal equality, image factorization, and exponentials via power objects; `Set` as a topos.

#### Internal and Enriched Structures

- **`Algebra.md`** — Internal algebraic structures (commutative monoids, abelian groups, commutative rings) inside an arbitrary cartesian category.
- **`PreAdditive.md`** — Pre-additive and additive precategories (enrichment in abelian groups), with rings identified as one-object pre-additive categories.

#### Specific Categories

- **`Simplex.md`** — The simplex category Δ of finite linear orders and monotone maps, shown univalent via rigidity of monotone isomorphisms.

#### Automation

- **`Solver.md`** — A reflection-based solver normalizing composition expressions to right-associated form for proving categorical equalities.

#### Subdirectories

- **`Topos/`** — Further developments in topos theory building on the topos axioms in `Topos.md`.
