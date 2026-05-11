### Category.SubobjectPoset

The poset of subobjects of an object in a category, with lattice structure derived from pullbacks.

This module turns the preorder of subobjects (monos into a fixed object `c`) into a genuine poset by quotienting by the equivalence of mutual containment, then equips it with meet-semilattice structure when the ambient category has pullbacks. The meet of two subobjects is computed as their pullback (which is again a mono into `c`), with the terminal subobject given by the identity. Joins are obtained dually, by passing to the opposite category and assuming pushouts. A wide-pullback abstraction is included to support the general fact that pullbacks of monos are monos.

#### Subobject Poset

- **`SubobjectPoset`**: For an object `c : C`, the antisymmetric poset quotient `Preorder.PosetC` of the subobject preorder `SubobjPreorder c`. Elements are equivalence classes of monos `m : a >-> c` under mutual containment.

#### Wide Pullbacks

- **`widePullback`**: Record bundling a `LimitDiagram` over the wide-pullback shape `Shape J` for a family of maps `maps : \Pi (j : J) -> Hom (objs j) z` into a common codomain `z`. The single field `wpbLim` packages the limit data.
- **`widePullback.Shape`**: The graph shape of a wide pullback over index set `J`: vertices are `Or (\Sigma) J` (one apex plus one node per index), with a unique edge from each `inr i` to the apex `inl ()`.
- **`widePullback.diagram`**: Builds the `Diagram (Shape J) D` whose apex is `z`, leaves are `objs i`, and edges are the given `maps i`.
- **`widePullback.widepullback-of-mono`**: If every leg `monomaps j` is a mono, then the cone map to the apex `coneMap (inl ())` of the wide pullback is itself a mono. Used to lift mono-ness through limits of monos.

#### Meet-Semilattice of Subobjects

- **`SubobjectMeetsemilatice`**: Instance making `SubobjectPoset c` a `TopMeetSemilattice` whenever `C : PrecatWithPullbacks`. Meet is defined on representatives by the pullback of monos via `xx`, well-definedness on the quotient is established via `product-monotone`/`product-monotone'`, and the top element is `subobj _ idIso`. The `meet-left`, `meet-right`, `meet-univ`, and `top-univ` proofs are stubbed (`{?hidden_proof}`).
- **`SubobjectMeetsemilatice.mono-from-pullback`**: Given monos `f : a >-> d` and `g : b >-> d`, produces a mono `pullback f.f g.f >-> d` by composing the pullback projection (which is mono since `g` is) with `g`.
- **`SubobjectMeetsemilatice.xx`** (alias `subobj-product`, infix 7): The meet of two subobjects represented by monos `f` and `g`, packaged as `subobj _ (mono-from-pullback f g)`.
- **`SubobjectMeetsemilatice.product-comm`**: Commutativity up to `<=` of `xx`: `f xx g <= g xx f`, via the swapped pullback map.
- **`SubobjectMeetsemilatice.product-monotone`**: Left monotonicity: if `subobj _ f <= subobj _ g`, then `f xx h <= g xx h`, witnessed by the induced map between pullbacks.
- **`SubobjectMeetsemilatice.product-monotone'`**: Right monotonicity: `h xx f <= h xx g` under the same hypothesis, derived from `product-comm` and `product-monotone`.

#### Join-Semilattice of Subobjects (Dual)

- **`SubobjectJoinSemilattice`**: Instance giving `SubobjectPoset c` a `JoinSemilattice` structure when `C.op` has pullbacks (i.e. `C` has pushouts), obtained by transporting the meet-semilattice instance through `MeetSemilattice.op`.
