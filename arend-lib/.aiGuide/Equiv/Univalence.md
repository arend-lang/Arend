### Equiv.Univalence

The univalence axiom and conversions between type equality and equivalences.

- **`=-to-Equiv`**: Converts `A = B` to `Equiv {A} {B}`.
- **`=-to-QEquiv`**: Converts `A = B` to `QEquiv {A} {B}` via `transport`.
- **`QEquiv-to-=`**: Converts `QEquiv {A} {B}` to `A = B` via `iso`.
- **`Equiv-to-=`**: Converts `Equiv {A} {B}` to `A = B`.
- **`univalence`**: `QEquiv {X = Y} {Equiv {X} {Y}}` — the univalence equivalence.
