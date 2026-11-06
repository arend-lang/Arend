### Equiv.Univalence.Set

- **`=-to-QEquiv`**: Converts `p : A = B` into a `QEquiv {A} {B}` with explicit components: forward map is `transport (\lam X => X) p`, retraction is `transport` along `inv p`, with both round-trips proven via `transport_*>` and the path inverse laws.
- **`QEquiv-to-=`**: Builds a path `A = B` from a `QEquiv {A} {B}` using the primitive `iso` constructor (which packages forward map, inverse, and both homotopies into a path).
- **`setUnivalence`**: Univalence for sets: for `X Y : \Set`, `=-to-QEquiv` and `QEquiv-to-=` make `X = Y` a quasi-equivalent type to `QEquiv {X} {Y}`.
- **`=-to-QEquiv-functorial`**: `=-to-QEquiv` takes concatenation of paths to composition of quasi-equivalences: `=-to-QEquiv (p *> q) = =-to-QEquiv p `transQEquiv` =-to-QEquiv q`.
- **`QEquiv-to-=-functorial`**: Dually, `QEquiv-to-= e1 *> QEquiv-to-= e2 = QEquiv-to-= (e1 `transQEquiv` e2)`.
