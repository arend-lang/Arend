### Equiv.BiEquiv

Bi-invertible maps: functions with a separate section and retraction, and their equivalence with `Equiv`.

A `BiEquiv` is a function equipped with both a right inverse (`Section`) and a left inverse (`Retraction`). Being bi-invertible is a proposition (`BiEquiv.levelProp`). The coercions `BiEquiv.fromQEquiv` and `BiEquiv.toQEquiv` convert between bi-invertible maps and quasi-inverse data `QEquiv`, and `BiEquiv~Equiv` shows that the type of bi-invertible maps is equivalent to `Equiv`.

#### Bi-invertible maps

- **`BiEquiv`**: A record extending `Section` and `Retraction`: a function `f` with a section and a retraction.
- **`BiEquiv.fromQEquiv` / `BiEquiv.toQEquiv`**: Coercions between `QEquiv` and `BiEquiv`, taking the same inverse as section and retraction, and conversely adjusting the section to a two-sided inverse.
- **`BiEquiv.equals`**: Two bi-equivalences are equal as soon as their underlying functions are equal.
- **`BiEquiv.levelProp`**: Being bi-invertible for a fixed function `f` is a proposition.

#### Comparison with Equiv

- **`BiEquiv~Equiv`**: A `QEquiv` between bi-invertible maps from `A` to `B` and `Equiv A B`.
