### Data.Or

This module defines the disjoint union (coproduct) type `Or` and associated utilities.

#### Or Type

- **`Or`** (infix `\/`): Data type `Or A B` with constructors `inl : A -> Or A B` and `inr : B -> Or A B`.

#### Where Block

- **`levelProp`**: When `A` and `B` are propositions and `A -> B -> Empty`, then `Or A B` is a proposition (any two elements are equal).
- **`map`**: Maps both sides: given `f : A -> C` and `g : B -> D`, transforms `Or A B` into `Or C D`.
- **`rec`**: Eliminator/fold: given `f : A -> C` and `g : B -> C`, produces a `C` from `Or A B`.
- **`Or_Equiv`**: Given equivalences `e1 : Equiv` and `e2 : Equiv`, constructs a `QEquiv` between `Or e1.A e2.A` and `Or e1.B e2.B`.
