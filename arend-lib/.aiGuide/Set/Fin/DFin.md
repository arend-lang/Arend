### Set.Fin.DFin

Dedekind-finite sets: sets where every injective endomap is automatically surjective.

This module captures the classical Dedekind characterization of finiteness as a propositional predicate on `\Set`. The key insight is that for such sets, injective self-maps are forced to be equivalences, yielding a constructive splitting of any injection. The module also bridges this notion to the pigeonhole formulation of finiteness, showing that pigeonhole sets are Dedekind-finite.

#### Main Definition

- **`isDFin`**: Predicate that a set `A` is Dedekind-finite: every injective `f : A -> A` is also surjective. Lives in `\Prop`.

#### Consequences of Dedekind-Finiteness

- **`isDFin.isEquiv`**: Promotes an injective endomap on a Dedekind-finite set to an `Equiv`, since injectivity plus the implied surjectivity yield an equivalence.
- **`isDFin.isSplit`**: Constructive preimage operation: given Dedekind-finiteness, an injection `f`, and `a : A`, returns `(a', f a' = a)` by inverting the induced equivalence.

#### Connection to Pigeonhole

- **`isDFin.fromPigeonhole`**: Every `PigeonholeSet` is Dedekind-finite, linking the pigeonhole-principle formulation of finiteness to the Dedekind formulation.
