### Homotopy.Suspension

The (unreduced) suspension of a type, defined as a pushout of two constant maps into the unit type.

The suspension `Susp A` glues two cone points (north and south) together by attaching, for each `a : A`, a meridian path `pmerid a : north = south`. This is realized as a `PushoutData` whose two projections collapse `A` to a point, so the only nontrivial data left in the pushout are the meridians. The construction provides the standard space-level definition used in synthetic homotopy theory and comes equipped with both a recursor and a pointed-type wrapper.

#### Core Definition

- **`Susp`**: The suspension `Susp (A : \Type) : \Type`, defined as `PushoutData {A} (\lam _ => ()) (\lam _ => ())`.

#### Point Constructors

- **`north`**: The north pole of the suspension, `north : Susp A`, given by `pinl ()`.
- **`south`**: The south pole of the suspension, `south : Susp A`, given by `pinr ()`.
- **`pmerid`**: The meridian path `pmerid (a : A) : north = south`, obtained from the pushout's `pglue` constructor.

#### Recursion and Auxiliary Structure

- **`Susp.rec`**: Non-dependent recursor: given `b1 b2 : B` and `f : A -> b1 = b2`, produces a map `Susp A -> B` sending `north` to `b1`, `south` to `b2`, and each meridian `pmerid a` to `f a`.
- **`Susp.pointed`**: Equips `Susp A` with a `Pointed` structure based at `north`, yielding the canonical pointed suspension.
- **`Susp.pushout`**: The underlying `pushoutData` value witnessing `Susp A` as a pushout of the two constant maps `A -> ()`.
