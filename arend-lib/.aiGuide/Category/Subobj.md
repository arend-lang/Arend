### Category.Subobj

Subobjects of an object in a precategory, as a poset, with the meet-semilattice structure and pullback operations that pullbacks provide.

A subobject of `obj` is a monomorphism into `obj` up to isomorphism over `obj`: `Subobj obj` is the quotient of monos with codomain `obj` by mutual factorization, and `s <= t` holds when `s` factors through `t`. When the category has pullbacks, subobjects form a meet-semilattice with top element, and pulling back along a morphism gives a functorial action on subobjects. The module also expresses "a morphism lifts through a subobject" and the equality subobject of a product `c × c`.

#### Subobjects and the Poset Structure

- **`Subobj`**: The type of subobjects of `obj : C`, defined as the quotient of monomorphisms `sub -> obj` by the relation that each factors through the other.
- **`inso~`**: Sends a monomorphism `m` with codomain `obj` to its class in `Subobj m.cod`.
- **`SubobjPoset`**: The `Poset` structure on `Subobj obj`, where `s1 <= s2` means that (a representative of) `s1` factors through `s2`; antisymmetry holds because mutual factorization is the quotienting relation.
- **`SubobjPoset.extractMap`**: From `inso~ f <= inso~ g` extracts the actual factoring map `h : Hom f.dom g.dom` with `g.f ∘ h = f`, using that `g` is mono to eliminate the propositional truncation.
- **`~-unsoequiv`**: If `inso~ m1 = inso~ m2` then there are maps `f : m1.dom -> m2.dom` and `g : m2.dom -> m1.dom` commuting with the monos, i.e. the two domains are isomorphic over `obj` (merely, as a truncated statement).

#### Meets and Top

- **`subobjTop`**: The greatest subobject of `obj`, the class of the identity iso.
- **`SubobjectSemilattice`**: For a category with pullbacks, `Subobj obj` is a `TopMeetSemilattice`; the meet is the pullback of the two monos and the top is `subobjTop`.

#### Lifting Through Subobjects

- **`subobjLifts`**: The proposition that `f : Hom x y` factors through the subobject `s` of `y`, i.e. there is `g` with `m.f ∘ g = f` for a representative `m`.
- **`subobjLifts_id_top`**: `id` lifts through `s` exactly when `s` is `subobjTop`.
- **`subobjEq`**: The subobject of `Bprod c c` given by the diagonal (a split mono), for a category with binary products.
- **`subobjEq-lifts`**: `pair f g` lifts through `subobjEq c` iff `f = g`, so this subobject classifies equality of morphisms into `c`.

#### Pullback of Subobjects

- **`subobjPullback`**: Pulls a subobject `s` of `y` back along `h : Hom x y` to a subobject of `x`, using the pullback of a representative mono (which is again mono).
- **`subobjPullback-char`**: If `subobjPullback h sub = inso~ m` then the square formed by `h`, `s.f`, `m.dom` and `m` is a pullback square (the type of such pullback structures is contractible).
- **`subobjPullback-lifts`**: `f` lifts through `subobjPullback g s` iff `g ∘ f` lifts through `s`.
- **`subobjLifts_top`**: `f` lifts through `s` iff the pullback of `s` along `f` is `subobjTop`.
- **`subobjPullback_id`**: Pulling back along `id` is the identity on subobjects.
- **`subobjPullback_o`**: Pullback is contravariantly functorial: `subobjPullback (g ∘ f) s = subobjPullback f (subobjPullback g s)`.
- **`subobjPullback-top`**: Pulling back a subobject equal to `inso~ idIso` along any `f` gives `inso~ idIso`.
- **`subobjPullback_top`**: Pullback preserves the top subobject: `subobjPullback f subobjTop = subobjTop`.
- **`subobjPullback-self`**: Pulling back a subobject `s = inso~ h` along its own mono `h.f` gives the top subobject `inso~ idIso`.
- **`subobjPullback_Iso-equiv`**: Pullback along an isomorphism `h` is a quasi-equivalence `Subobj h.cod ≃ Subobj h.dom`, with inverse the pullback along `h.hinv`.
- **`subobjPullback_Iso`**: For an iso `h` and `s = inso~ m`, the pullback of `s` along `h.f` is the class of the mono `Mono.comp h.reverse m`.
