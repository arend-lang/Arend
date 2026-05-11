### Category.Displayed

Displayed categories over a base category, providing a framework for fibered/indexed structures.

A displayed category over `C` consists of a family of "displayed objects" `DOb : C -> \hType` together with displayed morphisms `DHom f x y` lying over each base morphism `f : Hom a b`. Composition and identity laws are stated up to `transport` along the corresponding base equalities, since the displayed hom-set depends on the underlying morphism. The total category packages base and displayed data into ordinary `\Sigma`-types, and the projection to `C` is a functor; univalence is similarly displayed, ensuring identity types of displayed objects correspond to displayed isos over `idIso`.

#### Displayed Precategories

- **`DPrecat`**: Class of displayed precategories over a base `Precat C`. Carries `DOb : C -> \hType`, displayed homs `DHom f x y` indexed by `f : Hom a b`, displayed identity `id^`, displayed composition `∘^`, and the unitality and associativity laws expressed as transports along the corresponding base equalities (`id-left`, `id-right`, `o-assoc`).
- **`o^`** / **`∘^`**: Displayed composition; given `g^ : DHom g y z` and `f^ : DHom f x y`, produces `DHom (g ∘ f) x z`.
- **`id^-left`**, **`id^-right`**, **`o^-assoc`**: Coherence laws for displayed composition, modulo `transport` along the base category's laws.

#### Displayed Isomorphisms

- **`DIso`**: Record of a displayed isomorphism over a base `Iso e`, packaging a displayed morphism `f : DHom e.f dom cod`, its displayed inverse `inv^`, and the two roundtrip laws (`inv^-left`, `inv^-right`) stated via transport along `e.hinv_f` and `e.f_hinv`.
- **`idIso^`**: The identity displayed iso over `idIso`, with both forward and inverse components given by `id^ x`.

#### Total Category

- **`totalPrecat`**: Builds the Grothendieck-style total precategory of a displayed precategory `D`, with objects `\Sigma (a : C) (D a)` and morphisms pairing a base morphism with a displayed one.
- **`totalPrecat.proj`**: The forgetful functor `totalPrecat D -> C` projecting onto the base component.

#### Displayed Univalence

- **`DCat`**: Extends `DPrecat` with a displayed univalence axiom: for each base object `a` and displayed objects `x y : DOb a`, the canonical map `idtoiso^ : x = y -> DIso idIso {x} {y}` is an equivalence.
- **`DCat.idtoiso^`**: The canonical map sending a path between displayed objects (over the same base) to a displayed iso over `idIso`, defined by path induction.
- **`DCat.totalCat`**: Promotes the total precategory to a univalent `Cat`, given that the base precategory `C` is itself univalent.
- **`DCat.total-iso`**: Equivalence between pairs `(e : Iso, DIso e)` and isos in the total category, used to assemble/decompose total isos.
- **`DCat.eq-over`**: For a base path `p : a = b`, an equivalence between `transport D p x = y` and displayed isos `DIso (idtoiso p) {x} {y}` — the dependent generalization of univalence over a non-trivial base path.
