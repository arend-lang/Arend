package org.arend.lib.meta.equation;

import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.definition.CoreFunctionDefinition;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.meta.Dependency;

public class BaseAlgebraicMeta extends BaseMetaDefinition {
  @Dependency                                           public ArendRef pmap;
  @Dependency                                           public ArendRef inv;

  @Dependency                                           public CoreFunctionDefinition NatSemiring;
  @Dependency                                           public CoreFunctionDefinition IntRing;
  @Dependency(name = "BottomJoinSemilattice.bottom")    public CoreClassField bottom;
  @Dependency(name = "TopMeetSemilattice.top")          public CoreClassField top;
  @Dependency(name = "JoinSemilattice.join")            public CoreClassField join;
  @Dependency(name = "MeetSemilattice.meet")            public CoreClassField meet;
  @Dependency(name = "AddMonoid.+")                     public CoreClassField plus;
  @Dependency(name = "Semigroup.*")                     public CoreClassField mul;
  @Dependency(name = "Semiring.natCoef")                public CoreClassField natCoef;
  @Dependency(name = "AddGroup.negative")               public CoreClassField negative;
  @Dependency(name = "AAlgebra.coefMap")                public CoreClassField coefMap;
  @Dependency(name = "Pointed.ide")                     public CoreClassField ide;
  @Dependency(name = "AddPointed.zro")                  public CoreClassField zro;

  @Dependency(name = "RingTerm.var")                    public ArendRef varTerm;
  @Dependency(name = "RingTerm.coef")                   public ArendRef coefTerm;
  @Dependency(name = "RingTerm.:ide")                   public ArendRef ideTerm;
  @Dependency(name = "RingTerm.:zro")                   public ArendRef zroTerm;
  @Dependency(name = "RingTerm.:negative")              public ArendRef negativeTerm;
  @Dependency(name = "RingTerm.:*")                     public ArendRef mulTerm;
  @Dependency(name = "RingTerm.:+")                     public ArendRef addTerm;
  @Dependency(name = "BaseData.R")                      public ArendRef RingDataCarrier;
  @Dependency(name = "MonoidData.vars")                 public ArendRef DataFunction;
}
