### Order.Category

Categories of ordered sets: posets, strict posets, and decidable linear orders, with order-preserving morphisms.

#### Poset Morphisms

- **`PosetHom`**: Class of monotone (order-preserving) functions between posets, extending `SetHom`. Field `func-<=` witnesses preservation of `<=`.
- **`PosetCat`**: Category of posets and monotone maps. Composition and identity are inherited from functions; univalence is established via `sip`.

#### Strict Poset Morphisms

- **`StrictPosetHom`**: Class of strictly monotone functions between strict posets, extending `SetHom`. Field `func-<` witnesses preservation of `<`.
- **`StrictPosetCat`**: Category of strict posets and strict-order-preserving maps, with univalence proven via `sip`.

#### Decidable Linear Orders

- **`DecLinearOrderCat`**: Category of decidable linear orders (`LinearOrder.Dec`) with monotone maps as morphisms. The univalence proof additionally shows that monotone bijections preserve the strict order, lattice operations (`meet`, `join`), and the apartness relation `#`.
