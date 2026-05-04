### Category.Slice

Constructs the slice category `C/x` of objects equipped with a morphism into a fixed object `x`.

#### Objects

- **`ObOver`**: Type of objects over `x`: `\Sigma (y : C) (Hom y x)` — pairs of an object and a morphism into `x`.

#### Slice Categories

- **`SlicePrecat`**: The slice precategory `C/x` over an object `x : C`. Morphisms `(y, f) -> (z, g)` are pairs `(h : Hom y z, g ∘ h = f)` (commuting triangles). Identity and composition lift from `C` with proofs of triangle commutativity.
- **`SlicePrecat.forget`**: The forgetful faithful functor `C/x -> C` sending `(y, f)` to `y` and a triangle to its underlying morphism.
- **`SliceCat`**: The slice category `C/x` when `C` is a (univalent) category. Inherits the precategory structure from `SlicePrecat` and proves univalence by transporting isomorphisms in `C` (obtained via the forgetful functor) back to identifications of slice objects.
