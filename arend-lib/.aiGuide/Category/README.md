## Category

Category theory library: precategories, functors, natural transformations, limits, adjunctions, and topos theory.

### Core Categorical Structures

- **Functor.md** — Functors between precategories, natural transformations, functor categories, and special classes (faithful, full, fully faithful).
- **Product.md** — Product of precategories with componentwise objects and morphisms.
- **Displayed.md** — Displayed categories over a base category, providing fibered structure where objects and morphisms live above those of a base.
- **Simplex.md** — The simplex category Δ, whose objects are natural numbers and morphisms are monotone maps between finite linear orders.
- **Solver.md** — Reflection-based solver for equations in a precategory, normalizing expressions built from variables, identities, and compositions.

### Limits and Colimits

- **Limit.md** — Cones, limits, and standard limit shapes (products, equalizers, pullbacks, terminal objects), plus completeness classes.

### Adjunctions and Kan Extensions

- **Adjoint.md** — Adjoint functors and categorical equivalences, expressed via unit/counit and via hom-set bijections.
- **Coreflection.md** — Coreflections (universal arrows from a functor to an object) and their equivalence with right adjoints expressed pointwise.
- **KanExtension.md** — Left and right Kan extensions of functors along a functor between small categories, computed pointwise via (co)limits over comma categories.

### Subcategories and Subobjects

- **Subcat.md** — Subcategories, full subcategories on predicates, and reflective subcategories with their interaction with limits.
- **Subobj.md** — Subobjects and regular subobjects of an object, ordered by factorization through monomorphisms.
- **SubobjectPoset.md** — The poset of subobjects of an object, equipped with meet/join semilattice structure when pullbacks/pushouts exist.

### Comma and Slice Categories

- **Comma.md** — Comma categories constructed from a pair of functors with common codomain.
- **Slice.md** — The slice category C/x of objects equipped with a morphism into a fixed object x.

### Yoneda and Presheaves

- **Yoneda.md** — The Yoneda embedding, category of elements, colimit-of-representables decomposition, and universal extension along Yoneda into a cocomplete category.

### Cartesian Closed Categories and Factorization

- **CartesianClosed.md** — Cartesian closed precategories: categories with finite products where the product functor has a right adjoint (the exponential).
- **Factorization.md** — Weak and orthogonal factorization systems on a precategory, providing factorizations into a left class followed by a right class with a lifting property.

### Enriched and Additive Categories

- **PreAdditive.md** — Pre-additive and additive categories: categories enriched over abelian groups, with the equivalence between rings and one-object pre-additive categories.
- **Algebra.md** — Internal algebraic structures (commutative monoids, abelian groups, commutative rings) defined as objects in a cartesian category.

### Topos Theory

- **Topos.md** — Elementary toposes as finitely complete cartesian closed precategories with a subobject classifier; instantiation on the category of sets.
- **Topos/** — Subdirectory containing presheaf and sheaf theory:
  - **Presheaf.md** — Presheaf categories and their properties.
  - **Sheaf.md** — Sheaves and sheafification.
  - **Sheaf/** — Further sheaf-theoretic constructions.
