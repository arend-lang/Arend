### Category.SubobjectPoset

Constructs the poset of subobjects of an object in a category and equips it with meet/join semilattice structure when pullbacks/pushouts exist.

#### Subobject Poset

- **`SubobjectPoset`**: `SubobjectPoset {C : Precat} (c : C)` — the poset of subobjects of `c`, obtained as the antisymmetric quotient of the subobject preorder `SubobjPreorder`.

#### Wide Pullbacks

- **`widePullback`**: Class packaging a wide pullback of a `J`-indexed family of maps `objs j -> z` into a fixed object `z`, with field `wpbLim` providing the limiting cone over the associated diagram.
- **`widePullback.Shape`**: The graph shape `Or (\Sigma) J` with a unique edge from each `inr j` to the apex `inl ()`, used as the diagram shape for wide pullbacks.
- **`widePullback.diagram`**: Builds the `Diagram (Shape J) D` whose cone tip is `z` and whose `j`-th leg is `objs j` mapped via `maps j`.
- **`widePullback.widepullback-of-mono`**: If every `monomaps j` is a mono, then the apex projection `coneMap (inl ())` of the wide pullback is itself a mono — generalizes "pullback of a mono is a mono" to arbitrary arities.

#### Subobject Meet-Semilattice

- **`SubobjectMeetsemilatice`**: Instance giving `Subobjects(c)` the structure of a `TopMeetSemilattice` whenever `C` has pullbacks. The meet of two subobjects is their pullback, and the top element is the identity subobject.
- **`SubobjectMeetsemilatice.mono-from-pullback`**: Given monos `f : a >-> d` and `g : b >-> d`, produces the composed mono `pullback f g >-> d` representing their intersection as a subobject.
- **`SubobjectMeetsemilatice.xx` (`subobj-product`)**: Infix operator `f xx g` building the subobject of `d` corresponding to the intersection of two monos via `mono-from-pullback`.
- **`SubobjectMeetsemilatice.product-comm`**: Commutativity of intersection: `f xx g <= g xx f` in the subobject order.
- **`SubobjectMeetsemilatice.product-monotone`**: Monotonicity in the left argument: `subobj f <= subobj g` implies `f xx h <= g xx h`.
- **`SubobjectMeetsemilatice.product-monotone'`**: Monotonicity in the right argument: `subobj f <= subobj g` implies `h xx f <= h xx g`, derived from `product-comm` and `product-monotone`.

#### Subobject Join-Semilattice

- **`SubobjectJoinSemilattice`**: Instance giving `Subobjects(c)` a `JoinSemilattice` structure when `C^op` has pullbacks (i.e., `C` has pushouts), obtained by dualizing `SubobjectMeetsemilatice` via `MeetSemilattice.op`.
