### Set.Fin.DFin

Defines Dedekind-finite sets: sets where every injective endofunction is automatically surjective.

#### Core Definition

- **`isDFin`**: Propositional predicate stating that `A` is Dedekind-finite, i.e. every injection `f : A -> A` is also a surjection.

#### Consequences of Dedekind-Finiteness

- **`isDFin.isEquiv`**: An injective endofunction on a Dedekind-finite set is an equivalence.
- **`isDFin.isSplit`**: Constructively splits an injection on a Dedekind-finite set, producing for each `a : A` a preimage `a'` with `f a' = a`.

#### Connection to Pigeonhole

- **`isDFin.fromPigeonhole`**: Every `PigeonholeSet` is Dedekind-finite, deriving `isDFin A` from the pigeonhole principle.
