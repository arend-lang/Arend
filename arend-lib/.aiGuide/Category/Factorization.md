### Category.Factorization

Weak and orthogonal factorization systems on a precategory, providing factorizations of morphisms into a left class followed by a right class with a lifting property.

#### Weak Factorization Systems

- **`WFS`**: Class for a weak factorization system on a precategory `C`. Specifies two morphism predicates `L` and `R` such that every morphism `h : x -> z` factors as `g ∘ f = h` with `L f` and `R g` (`factors`), and any commutative square with an `L`-morphism on the left and an `R`-morphism on the right admits a (not necessarily unique) diagonal filler (`lift`).
- **`C`**: The underlying precategory.
- **`L`**, **`R`**: Predicates picking out the left and right classes of morphisms.
- **`factors`**: Produces an `(L, R)`-factorization of any morphism.
- **`lift`**: Diagonal filler `l : Hom b c` for a commutative square `g ∘ t = s ∘ f` with `L f` and `R g`, satisfying `l ∘ f = t` and `g ∘ l = s`.

#### Orthogonal Factorization Systems

- **`OFS`**: Class extending `WFS` to an orthogonal factorization system, where the diagonal filler is unique. The uniqueness is encoded by `unique-lift`, an equivalence between `Hom b c` and the type of commutative squares with sides `f` (in `L`) and `g` (in `R`). The `lift` field is derived from this equivalence.
- **`unique-lift`**: Equivalence asserting that for `L f` and `R g`, lifts in squares are uniquely determined.

#### Lemmas

- **`WFS.left-epi`**: In a cartesian precategory, an `L`-morphism `f` is epic against any object `z` whose diagonal `diagonal z` lies in `R`: from `g ∘ f = h ∘ f` deduce `g = h`.
- **`OFS.liftFromMono`**: Constructs the `unique-lift` equivalence from weaker data: when `g` is a monomorphism, mere existence of a lift `l` satisfying `g ∘ l = s` (without requiring `l ∘ f = t`) suffices to produce the full orthogonality equivalence.
