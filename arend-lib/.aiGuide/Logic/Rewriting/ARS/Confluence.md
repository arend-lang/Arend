### Logic.Rewriting.ARS.Confluence

Confluence properties of abstract reduction systems and the equivalences between them.

This module formalizes the standard hierarchy of confluence-style properties for `SimpleARS`: confluence, Church-Rosser, local confluence, strong confluence, the normal form property, and unique normal forms. The core results establish the classical equivalences and implications — Church-Rosser ⇔ confluence, strong confluence ⇒ confluence, confluence ⇒ normal form property ⇒ unique normal forms — culminating in Newman's lemma, which states that for terminating systems local confluence coincides with confluence. Auxiliary `\where` lemmas factor inductive arguments over conversion sequences (`<~>*`) and reduction sequences (`~>_*`) used in the main proofs.

#### Auxiliary Joining Predicate

- **`StrongJoin`**: Strong joinability of `b` and `c`: there exists `d` with `b ~>_= d` (zero or one step) and `c ~>_* d` (any number of steps). Used to formulate strong confluence.

#### Confluence Properties

- **`isStronglyConfluent`**: For any peak `a ~> b`, `a ~> c` of single steps, `b` and `c` are strongly joinable.
- **`isChurchRosser`**: Any two convertible terms (`a <~>* b`) have a common reduct (`Join a b`).
- **`isConfluent`**: Any peak of multi-step reductions `a ~>_* b`, `a ~>_* c` can be joined.
- **`isLocallyConfluent`**: Any peak of single steps `a ~> b`, `a ~> c` can be joined (weak confluence).

#### Normal Form Properties

- **`hasUniqueNormalFormsWrtReduction`**: Two normal forms reachable from a common ancestor are equal.
- **`hasUniqueNormalForms`**: Any two convertible normal forms are equal.
- **`hasNormalFormProperty`**: If `a` is convertible to a normal form `b`, then `a` reduces to `b`.

#### Equivalence of Church-Rosser and Confluence

- **`ChurchRosser=>Confluence`**: Church-Rosser implies confluence (a peak is a special conversion).
- **`Confluence=>ChurchRosser`**: Confluence implies Church-Rosser, by induction on the conversion sequence.
  - **`conf=>rc-helper`**: Inductive helper producing a `Join a b` from a conversion `a <~>* b` under the confluence hypothesis.
- **`ChurchRosser=Confluence`**: Propositional equality `isChurchRosser A = isConfluent A`.

#### Strong Confluence Implies Confluence

- **`StrongConfluence=>Confluence`**: Strong confluence implies confluence.
  - **`SCR=>strong-join`**: From `a ~>_* b` and `a ~> c`, produces a `StrongJoin b c` by induction on the multi-step reduction.
  - **`strong-join=>Confluence`**: Lifts strong joining to full confluence on multi-step peaks.

#### Confluence and Normal Forms

- **`Confluence=>LocalConfluence`**: Every confluent system is locally confluent (single steps are reductions).
- **`Confluence=>NormalFormProperty`**: Confluent systems satisfy the normal form property.
  - **`conf=>nfp-helper`**: From a conversion `a <~>* b` with `b` a normal form, builds the reduction `a ~>_* b`.
- **`NormalFormProperty=>UniqueNormalForms`**: The normal form property implies unique normal forms (up to convertibility).
- **`UniqueNormalForms=>UniqueNormalFormsWrtReduction`**: Unique normal forms (w.r.t. conversion) implies the weaker reduction-based version.

#### Confluence from Normalization or Termination

- **`Normalization+UniqueNormalForms=>Confluence`**: A normalizing system with unique normal forms is confluent.
- **`Termination+LocalConfluence=>Confluence`**: Newman's lemma: a terminating, locally confluent system is confluent.
- **`Newman`** (alias of `Termination=>[LocalConfluence=Confluence]`): For terminating systems, local confluence and confluence are propositionally equal.
