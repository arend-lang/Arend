### Category.Topos

Elementary toposes as finitely complete cartesian closed precategories equipped with a subobject classifier.

A `ToposPrecat` combines a subobject classifier (`PrecatWithSubobjClassifier`), cartesian closure and power objects (`PrecatWithPowerObject`) over a finitely complete precategory. The subobject classifier `Omega` and the power objects `Power a` are both specified by an equivalence between morphisms and subobjects (pullback of a generic subobject), and the module derives characteristic maps, singletons and an internal hom `PHom b c` built from power objects, which gives exponentials. The nested classes `ToposPrecat.FromSubobjClassifier` and `ToposPrecat.FromPowerObject` let one supply only the subobject classifier (resp. only the power objects) together with the cartesian closed structure (resp. the internal hom) and obtain a topos. The category `\Set` is shown to be a topos with `\Prop` as subobject classifier.

#### Subobject Classifier

- **`PrecatWithSubobjClassifier`**: A finitely complete precategory with a subobject classifier: an object `Omega`, a generic subobject `Omega-true : Subobj Omega`, and for every `c` an equivalence `Hom c Omega ≃ Subobj c` given by pulling back `Omega-true`.
- **`PrecatWithSubobjClassifier.Omega-char`**: The characteristic map `c → Omega` of a subobject `s` of `c`, the inverse of the pullback equivalence `Omega-univ`.
- **`PrecatWithSubobjClassifier.Omega-char-unique`**: Two maps `c → Omega` that pull `Omega-true` back to the same subobject are equal.
- **`PrecatWithSubobjClassifier.Omega-true-map`**: The global element `true : terminal → Omega` whose induced subobject is `Omega-true`.
- **`PrecatWithSubobjClassifier.Omega-true_Omega-true-map`**: `Omega-true` equals the subobject of `Omega` represented by the split mono `Omega-true-map`.
- **`PrecatWithSubobjClassifier.Omega-pullback`**: For a mono `m`, the square exhibiting `m` as the pullback of `Omega-true-map` along its characteristic map `Omega-char (inso~ m)`.
- **`PrecatWithSubobjClassifier.mono-regular`**: Every monomorphism in a category with a subobject classifier is a regular mono.

#### Power Objects

- **`PrecatWithPowerObject`**: A finitely complete precategory with power objects: an assignment `Power a`, a membership subobject `Power-belongs a` of `a × Power a`, and for all `c`, `a` an equivalence `Hom c (Power a) ≃ Subobj (a × c)` given by pulling `Power-belongs a` back along `id × h`.
- **`PrecatWithPowerObject.Power`**: The power object of `a`, the object classifying subobjects of `a × c` as maps `c → Power a`.
- **`PrecatWithPowerObject.Power-char`**: The classifying map `c → Power a` of a subobject of `a × c`, inverse to the pullback equivalence `Power-univ`.
- **`PrecatWithPowerObject.pullback_Power`**: Pulling `Power-belongs a` back along `id × Power-char s` returns `s`.
- **`PrecatWithPowerObject.Power_pullback`**: `Power-char` of the pullback of `Power-belongs a` along `id × f` is `f`.
- **`PrecatWithPowerObject.Power-char_o`**: Naturality of `Power-char`: precomposing with `f` corresponds to pulling the subobject back along `id × f`.
- **`PrecatWithPowerObject.Power_lift-char`**: The pullback of `s` along `id × f` lifts (`subobjLifts`) exactly when `Power-char s ∘ f` equals the classifying map of the top subobject.
- **`PrecatWithPowerObject.Power-char_top`**: The classifying map of the top subobject is stable under precomposition: `Power-char subobjTop ∘ f = Power-char subobjTop`.

#### Singletons and the Internal Hom

- **`PrecatWithPowerObject.Power-singleton`**: The singleton map `c → Power c`, the classifying map of the equality subobject `subobjEq c`.
- **`PrecatWithPowerObject.Power-singleton-mono`**: `Power-singleton c` is a monomorphism.
- **`PrecatWithPowerObject.Power-singleton-subobj`**: The subobject of `Power c` given by the mono `Power-singleton c`.
- **`PrecatWithPowerObject.PHom`**: The internal hom `b → c` built from power objects, as an equalizer inside a power object (the single-valued total relations).
- **`PrecatWithPowerObject.PHom-eval`**: The evaluation map `PHom b c × b → c`.
- **`PrecatWithPowerObject.PHom-univ`**: The universal property of `PHom b c`: `g ↦ PHom-eval ∘ (g × id)` is an equivalence `Hom a (PHom b c) ≃ Hom (a × b) c`, so `PHom` gives exponentials.

#### Toposes

- **`ToposPrecat`**: Extends `PrecatWithSubobjClassifier`, `CartesianClosedPrecat` and `PrecatWithPowerObject`; that is, a finitely complete cartesian closed precategory with a subobject classifier and power objects. The nested classes `FromSubobjClassifier` and `FromPowerObject` construct the missing structure from either one.
- **`SetTopos`**: Instance witnessing that `\Set` is a topos via `ToposPrecat.FromSubobjClassifier`. The subobject classifier is `\Prop`, `Omega-true` is the subobject `\Sigma → \Sigma`, and the characteristic map of a subobject represented by `m` sends `x` to `∃ (y : m.dom) (m.f y = x)`.
