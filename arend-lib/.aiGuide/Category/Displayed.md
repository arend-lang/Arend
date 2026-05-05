### Category.Displayed

Displayed categories over a base category, providing fibered structure where objects and morphisms live "above" those of a base.

#### Displayed Precategory

- **`DPrecat`**: Class of displayed precategories over a base `C : Precat`. Provides `DOb : C -> \hType` (objects displayed over base objects), `DHom` (morphisms displayed over base morphisms), displayed identity `id^`, displayed composition `∘^`, and the displayed versions of the category laws (`id^-left`, `id^-right`, `o^-assoc`) stated up to transport along the corresponding base equation.

#### Displayed Isomorphisms

- **`DIso`**: Class of displayed isomorphisms over a base iso `e : Iso`. Carries displayed objects `dom`, `cod`, a displayed map `f : DHom e.f dom cod`, a displayed inverse `inv^`, and the displayed inverse laws `inv^-left`, `inv^-right` (modulo transport along `e.hinv_f` / `e.f_hinv`).
- **`idIso^`**: The identity displayed isomorphism over `idIso`, built from `id^ x` on both sides.

#### Total Category Construction

- **`totalPrecat`**: The total precategory `\Sigma (a : C) (DOb a)` of a displayed precategory, with hom-sets pairs of base and displayed morphisms.
- **`totalPrecat.proj`**: The projection functor `totalPrecat D -> C` sending `(a, x)` to `a` and forgetting the displayed component.

#### Univalent Displayed Categories

- **`DCat`**: Extends `DPrecat` with a displayed univalence axiom: for fixed base object `a`, the canonical map `idtoiso^ : x = y -> DIso idIso {x} {y}` is an equivalence.
- **`DCat.idtoiso^`**: Sends an equality of displayed objects (over the same base) to a displayed iso over `idIso`, by path induction.
- **`DCat.totalCat`**: Builds a univalent total category `Cat` from a `DCat` and a univalence proof for the base, by composing equivalences through `sigmaEquiv`, `eq-over`, and `total-iso`.
- **`DCat.total-iso`**: Equivalence between pairs `(e : Iso, DIso e)` and isomorphisms in the total precategory `totalPrecat D`.
- **`DCat.eq-over`**: For `p : a = b` in the base, an equivalence between dependent equalities `transport D p x = y` and displayed isos over `idtoiso p`; reduces to `univalence^` when `p = idp`.
