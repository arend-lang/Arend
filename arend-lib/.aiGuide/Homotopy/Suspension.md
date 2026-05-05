### Homotopy.Suspension

The unreduced suspension of a type, defined as a pushout of two constant maps to the unit type.

#### Suspension Type

- **`Susp`**: The suspension `Susp A` of a type `A`, defined as `PushoutData {A} (\lam _ => ()) (\lam _ => ())` — a pushout collapsing `A` to two points connected by a meridian for each element.
- **`Susp.rec`**: Recursion principle for `Susp A`: given `b1 b2 : B` and `f : A -> b1 = b2`, defines a map `Susp A -> B` sending the north pole to `b1`, the south pole to `b2`, and each meridian to `f a`.
- **`Susp.pointed`**: Equips `Susp A` with the structure of a `Pointed` type at the north pole.
- **`Susp.pushout`**: The underlying pushout data witnessing `Susp A` as a pushout.

#### Poles and Meridians

- **`north`**: The north pole of `Susp A`, defined as `pinl ()`.
- **`south`**: The south pole of `Susp A`, defined as `pinr ()`.
- **`pmerid`**: The meridian path `north = south` associated to an element `a : A`, given by `path (pglue a)`.
