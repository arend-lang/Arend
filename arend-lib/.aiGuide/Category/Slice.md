### Category.Slice

The slice category construction over an object in a (pre)category.

Given a precategory `C` and an object `x : C`, this module builds the slice category `C/x` whose objects are morphisms into `x` and whose morphisms are commuting triangles. The construction is given in two layers: `SlicePrecat` provides the precategory structure, and `SliceCat` upgrades it to a category by establishing univalence when the base is a category. A faithful forgetful functor to `C` projects each slice object onto its domain, witnessing that slice morphisms are determined by their underlying maps.

#### Objects

- **`ObOver`**: The type of objects over `x : C`, defined as `\Sigma (y : C) (Hom y x)` — a domain object paired with a morphism into `x`.

#### Slice Precategory

- **`SlicePrecat`**: The precategory structure on `ObOver x`. A morphism `(y, f) -> (z, g)` is a pair `(h : Hom y z, g ∘ h = f)` — a map in `C` together with a proof that the triangle commutes. Identity and composition are inherited from `C`, with the commuting-triangle proofs assembled via `id-right` and associativity.
- **`SlicePrecat.forget`**: The forgetful functor `SlicePrecat x -> C` sending `(y, f)` to `y` and a slice morphism to its underlying map. Packaged as a `FaithfulFunctor`, since the commuting-triangle proof is propositional and adds no information beyond the underlying morphism.

#### Slice Category

- **`SliceCat`**: When `C` is a (univalent) category, the slice `SliceCat x` is also a category. Extends `SlicePrecat x` with a `univalence` proof, so isomorphisms in the slice correspond to identifications of slice objects.
