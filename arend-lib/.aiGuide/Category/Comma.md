### Category.Comma

Construction of comma categories from a pair of functors with a common codomain.

Given functors `F : C → E` and `G : D → E`, the comma category `(F ↓ G)` has objects `(x, y, a : F x → G y)` and morphisms given by commuting squares between such triangles. This module builds the precategory structure with explicit composition proofs threaded through the functoriality of `F` and `G`, then upgrades to a univalent category when `C` and `D` are univalent. Auxiliary constructions provide the canonical forgetful functors to the source categories and a covariant action turning natural transformations between the defining functors into functors between comma categories.

#### Main Constructions

- **`commaPrecat`**: The comma precategory `(F ↓ G)` for `F : Functor C E`, `G : Functor D E`. Objects are triples `(x : C, y : D, a : Hom (F x) (G y))`; morphisms `(x,y,a) → (x',y',a')` are pairs `(f : x → x', g : y → y')` together with a commutation proof `a' ∘ F f = G g ∘ a`. Identity and composition are inherited componentwise, with the square-commutation proof rebuilt using `Func-id`, `Func-o`, and associativity.
- **`commaCat`**: Upgrades `commaPrecat F G` to a univalent `Cat` whenever the source categories `C` and `D` are univalent (`E` may remain a mere `Precat`).

#### Forgetful Functors

- **`commaPrecat.leftForget`**: The projection functor `(F ↓ G) → C` sending `(x, y, a) ↦ x` and `(f, g, _) ↦ f`.
- **`commaPrecat.rightForget`**: The projection functor `(F ↓ G) → D` sending `(x, y, a) ↦ y` and `(f, g, _) ↦ g`.

#### Functoriality in the Defining Functors

- **`commaPrecat.functor`**: Given natural transformations `a : F' ⇒ F` and `b : G ⇒ G'`, produces a functor `(F ↓ G) → (F' ↓ G')` by conjugating the connecting morphism: `(x, y, s) ↦ (x, y, b y ∘ s ∘ a x)`. Functoriality of the morphism component uses naturality of `a` and `b` together with the original square-commutation.
