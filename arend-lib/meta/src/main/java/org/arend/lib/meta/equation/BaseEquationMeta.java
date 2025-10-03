package org.arend.lib.meta.equation;

import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.meta.Dependency;

public class BaseEquationMeta extends BaseAlgebraicMeta {
  @Dependency(name = "BaseSet.E")                       CoreClassField carrier;
  @Dependency(name = "*>")                              public ArendRef concat;
  @Dependency(name = "Precat.Hom")                      public ArendRef catHom;
  @Dependency(name = "Precat.o")                        CoreClassField catComp;
  @Dependency(name = "Precat.id")                       CoreClassField catId;
  @Dependency(name = "AddMonoid.zro-right")             ArendRef addMonZroRight;
  @Dependency(name = "PseudoSemiring.zro_*-right")      ArendRef zeroMulRight;
  @Dependency                                           CoreClassDefinition TopMeetSemilattice;
  @Dependency                                           CoreClassDefinition BoundedDistributiveLattice;
  @Dependency(name = "AddGroup.fromZero")               ArendRef fromZero;
  @Dependency(name = "AddGroup.toZero")                 ArendRef toZero;
  @Dependency(name = "List.nil")                        ArendRef nil;
  @Dependency(name = "List.::")                         ArendRef cons;
  @Dependency                                           CoreClassDefinition Monoid;
  @Dependency                                           CoreClassDefinition CMonoid;
  @Dependency                                           CoreClassDefinition AddMonoid;
  @Dependency                                           CoreClassDefinition AbMonoid;
  @Dependency                                           CoreClassDefinition Ring;
  @Dependency                                           CoreClassDefinition Semiring;
  @Dependency(name = "Monoid.LDiv")                     CoreClassDefinition ldiv;
  @Dependency(name = "Monoid.RDiv")                     CoreClassDefinition rdiv;

  @Dependency(name = "AlgData.terms-equality")          ArendRef ringTermsEq;
  @Dependency(name = "CAlgData.terms-equality")         ArendRef commSemiringTermsEq;
  @Dependency(name = "CRingData.terms-equality")        ArendRef commRingTermsEq;
  @Dependency(name = "LatticeData.terms-equality")      ArendRef latticeTermsEq;
  @Dependency(name = "AlgData.interpret")               ArendRef ringInterpret;
  @Dependency(name = "gensZeroToIdealZero")             ArendRef gensZeroToIdealZero;
  @Dependency                                           public ArendRef LatticeData;
  @Dependency                                           public ArendRef CRingData;
  @Dependency                                           public ArendRef RingData;
  @Dependency                                           public ArendRef CSemiringData;
  @Dependency                                           public ArendRef SemiringData;
  @Dependency(name = "LatticeData.L")                   public ArendRef LatticeDataCarrier;
  @Dependency                                           public ArendRef HData;
  @Dependency                                           public ArendRef MonoidData;
  @Dependency                                           public ArendRef CMonoidData;
  @Dependency                                           public ArendRef LData;
  @Dependency(name = "LData.L")                         public ArendRef SemilatticeDataCarrier;

  // Monoid solver
  @Dependency(name = "MonoidData.interpretNF")          ArendRef monoidInterpretNF;
  @Dependency(name = "MonoidData.interpretNF_++")       ArendRef monoidInterpretNFConcat;
  @Dependency(name = "MonoidData.normalize-consistent") ArendRef monoidNormConsist;
  @Dependency(name = "MonoidData.terms-equality-conv")  ArendRef termsEqConv;
  @Dependency(name = "LData.terms-equality")            ArendRef semilatticeTermsEq;
  @Dependency(name = "CMonoidData.terms-equality")      ArendRef commTermsEq;
  @Dependency(name = "MonoidData.terms-equality")       ArendRef termsEq;
  @Dependency(name = "CMonoidData.terms-equality-conv") ArendRef commTermsEqConv;
  @Dependency(name = "CMonoidData.sort-consistent")     ArendRef sortDef;
  @Dependency(name = "CMonoidData.replace-consistent")  ArendRef commReplaceDef;
  @Dependency(name = "MonoidData.replace-consistent")   ArendRef replaceDef;
  @Dependency(name = "MonoidTerm.var")                  ArendRef varMTerm;
  @Dependency(name = "MonoidTerm.:ide")                 ArendRef ideMTerm;
  @Dependency(name = "MonoidTerm.:*")                   ArendRef mulMTerm;

  // Category solver
  @Dependency(name = "HData.interpretNF")               ArendRef catInterpretNF;
  @Dependency(name = "HData.interpretNF_++")            ArendRef catInterpretNFConcat;
  @Dependency(name = "HData.normalize-consistent")      ArendRef catNormConsist;
  @Dependency(name = "HData.terms-equality")            ArendRef catTermsEq;
  @Dependency(name = "CatTerm.var")                     ArendRef varCTerm;
  @Dependency(name = "CatTerm.:id")                     ArendRef idCTerm;
  @Dependency(name = "CatTerm.:o")                      ArendRef compCTerm;
  @Dependency(name = "CatNF.:nil")                      ArendRef nilCatNF;
  @Dependency(name = "CatNF.:cons")                     ArendRef consCatNF;
  @Dependency(name = "HData.H")                         ArendRef HDataFunc;
  @Dependency(name = "HData.V")                         ArendRef VDataFunc;
}
