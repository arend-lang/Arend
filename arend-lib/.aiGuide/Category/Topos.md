### Category.Topos

Elementary toposes as finitely complete cartesian closed precategories equipped with a subobject classifier.

A `ToposPrecat` axiomatizes a topos by combining finite limits, cartesian closure, and a subobject classifier `omega` with a generic mono `true-map : terminal → omega` such that every monomorphism arises uniquely as the pullback of `true-map` along a characteristic map. From these axioms the module derives the power-object structure: `Power B := omega^B` with its membership relation `belongs : Power B × B → omega`, internal equality `eq B`, singletons, names of subobjects, and image factorization. The construction culminates in an internal exponential object `graphs.apex B C` (built as a pullback expressing "graphs of single-valued total relations") together with an evaluation map and transpose, giving an alternative, topos-theoretic proof that `B` is exponentiable. The category `\Set` is shown to be a topos with `\Prop` as subobject classifier.

#### Main Class

- **`ToposPrecat`**: Extends `FinCompletePrecat` and `CartesianClosedPrecat`. Adds a subobject classifier `omega`, a global element `true-map : terminal → omega`, a characteristic map `char-map` for every mono, the pullback square `char-pullback` exhibiting the mono as the preimage of `true`, and uniqueness `char-unique` of the classifying map. Cartesian closure is derived: `omega` is exponentiable (`p-exponential`) and a default `exp` is provided via the power-object construction.

#### Power Objects

- **`Power B`**: The power object `omega^B`, defined as the exponential of the subobject classifier.
- **`belongs`**: The internal membership relation `Power B × B → omega`, the counit of the exponential adjunction.
- **`p-transpose`**: Transpose of `f : A × B → omega` to `A → Power B` along the exponential adjunction.
- **`p-transpose-univ`**: Universal property: `f = belongs ∘ prodMap (p-transpose f) (id B)`.
- **`p-transpose-unique`**: Uniqueness of the transpose given the universal equation.
- **`anti-transpose`**: The inverse direction `(A → Power B) → (A × B → omega)`.
- **`antitranspose-eq`**: Round-trip `f = anti-transpose (p-transpose f)`.
- **`transpose-inj`**: `p-transpose` is injective.

#### Internal Equality and Singletons

- **`internal-equality`** (alias **`eq`**): The internal equality predicate `B × B → omega`, defined as the characteristic map of the diagonal.
- **`singleton`**: `B → Power B`, the transpose of internal equality, sending `b` to `{b}`.
- **`char-rel`**: Characteristic map of the graph of a morphism `f : B → C`, namely `eq C ∘ prodMap f (id C)`.
- **`eqq-p-transpose-s`**: `singleton B ∘ b = p-transpose (char-rel b)`.
- **`pbBeta2'`**: Auxiliary computation for the left pullback square involving `prodMap b (id) = diagonal ∘ p2`.
- **`left-square`**: Pullback exhibiting `(pair (id X) b, b)` as the pullback of `prodMap b (id B)` along `diagonal B`.
- **`right-square`**: Pullback square classifying the diagonal of `B` via internal equality.
- **`full-square`**: The graph of `b` as a pullback obtained by composing `left-square` and `right-square` (pullback lemma).
- **`singleton-mono`**: `singleton B` is a monomorphism.
- **`is-singleton`**: `Power B → omega`, the characteristic map of `singleton-mono`, classifying which subsets are singletons.

#### Monomorphisms in a Topos

- **`true-over-obj`**: The constant `true` map `B → omega`, i.e. `true-map ∘ terminalMap`.
- **`monic-is-regular`**: Every monomorphism is regular: `m` is the equalizer of `true-over-obj` and `char-map m`.
- **`monic+epi=iso`**: A morphism that is both monic and epic is an isomorphism (a topos is balanced).

#### Names and Images

- **`global`**: Global elements `Hom terminal X`.
- **`namePower`**: Given `phi : A → omega`, its name as a global element of `Power A`.
- **`image`**: The image map `Power (B × C) × B → Power C`, sending a relation `R` and `b` to `{c | (b,c) ∈ R}`.
- **`is-image-single`**: Predicate on `Power (B × C) × B` asserting that the image at `b` is a singleton.
- **`all-with-single-img`**: `Power (B × C) → Power B`, the set of `b` whose `R`-image is a singleton.
- **`graphs`**: Pullback comparing `namePower true-over-obj` with `all-with-single-img`, classifying total functional relations.
  - **`graphs.apex B C`**: The apex of this pullback, serving as the internal function space.
  - **`graphs.map`**: Projection from `graphs.apex B C` to `Power (B × C)`.

#### Exponentials via Power Objects

- **`terminate`**: `terminalMap ∘ f = terminalMap` (used for diagram chasing).
- **`eval_comm`**: Commutativity needed to define evaluation as a pullback map.
- **`eval`**: Evaluation map `graphs.apex B C × B → C`, factoring the image relation through the singleton classifier.
- **`h`**: Helper transposition `(A × B → C) → A → Power (B × C)`, encoding a morphism as a relation.
- **`ptranspose-eqq-eq`**: Compatibility lemma between `p-transpose (char-rel f)` and the image construction.
- **`singleton-is-single`**: Singletons are classified as singletons: `is-singleton ∘ singleton C = true-over-obj`.
- **`transpose'`**: Transpose `(A × B → C) → A → graphs.apex B C` realizing the exponential adjunction.
- **`transpose-univ'`**: Universal property `f = eval ∘ prodMap (transpose' f) (id B)`.
- **`transpose-unique'`**: Uniqueness of the transpose.
- **`exp-adjoint`**: Right-adjoint coreflection of the functor `– × B`, packaging the data above into the exponential adjunction.
- **`exp`** (default): Derives `isExponential` for every `B` from `exp-adjoint`, recovering cartesian closure from the power-object structure.

#### Propositional Helpers

- **`sigma-prop-ext`**: For `A : \Prop`, `a : A` gives `A = \Sigma` (a witnessed proposition is contractibly the unit type).
- **`sigma-prop-ext-inv`**: Inverse extracting an inhabitant of `A` from `A = \Sigma`.

#### The Topos of Sets

- **`SetTopos`**: Instance witnessing that `\Set` is a topos. The subobject classifier is `\Prop`, `true-map` picks out `\Sigma`, and `char-map m` is `IsElement m` — the predicate "`b` is in the image of `m`".
  - **`SetTopos.mono-is-inj`**: A monomorphism in `\Set` is an injective function (proved via the Yoneda-style "name" trick).
  - **`SetTopos.IsElement`**: The image predicate of a mono `m`, with constructor `isContained` and proof that it is a proposition (using injectivity of `m`).
  - **`SetTopos.isElement-char`**: Decoding membership from the pullback equation `(λ x ↦ IsElement m (p1 x)) = (λ _ ↦ \Sigma)`.
