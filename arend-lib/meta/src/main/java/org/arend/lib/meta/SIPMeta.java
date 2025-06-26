package org.arend.lib.meta;

import org.arend.ext.FreeBindingsModifier;
import org.arend.ext.concrete.ConcreteClassElement;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteLetClause;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreLamExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.error.TypeMismatchError;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.*;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.error.FieldNotPropError;
import org.arend.lib.error.TypeError;
import org.arend.lib.util.Names;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SIPMeta extends BaseMetaDefinition {
  @Dependency                               private ArendRef exts;
  @Dependency(name = "Map.dom")             private ArendRef mapDom;
  @Dependency(name = "Map.cod")             private ArendRef mapCod;
  @Dependency(name = "Map.f")               private ArendRef mapFunc;
  @Dependency                               private ArendRef Iso;
  @Dependency(name = "SplitMono.hinv")      private ArendRef isoInv;
  @Dependency(name = "Iso.f_hinv")          private ArendRef isoFuncInv;
  @Dependency(name = "SplitMono.hinv_f")    private ArendRef isoInvFunc;
  @Dependency                               private ArendRef SIP_Set;
  @Dependency                               private ArendRef transport;
  @Dependency                               private ArendRef Jl;
  @Dependency(name = "*>")                  private ArendRef concat;
  @Dependency(name = "Cat.makeUnivalence")  private ArendRef makeUnivalence;

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { true };
  }

  @Override
  public boolean requireExpectedType() {
    return true;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    CoreExpression type = contextData.getExpectedType().normalize(NormalizationMode.WHNF);
    if (!(type instanceof CoreClassCallExpression classCall && classCall.getDefinition().getRef().checkName(Names.EQUIV))) {
      typechecker.getErrorReporter().report(new TypeMismatchError(type, DocFactory.text("an equivalence"), contextData.getMarker()));
      return null;
    }

    CoreExpression isoArg = Names.getAbsImplementation(classCall, Names.EQUIV_MAP, Names.getEquivB());
    if (isoArg != null) isoArg = isoArg.normalize(NormalizationMode.WHNF);
    CoreExpression cat = isoArg instanceof CoreClassCallExpression ? Names.getClosedImplementation((CoreClassCallExpression) isoArg, Names.CAT_MAP, Names.getMapCat()) : null;
    if (cat == null) {
      typechecker.getErrorReporter().report(new TypeMismatchError(type, DocFactory.text("Iso {_} -> _"), contextData.getMarker()));
      return null;
    }

    cat = cat.computeType().normalize(NormalizationMode.WHNF);
    CoreExpression ob = cat instanceof CoreClassCallExpression ? Names.getClosedImplementation((CoreClassCallExpression) cat, Names.PRECAT, Names.getOb()) : null;
    CoreExpression hom = cat instanceof CoreClassCallExpression ? Names.getClosedImplementation((CoreClassCallExpression) cat, Names.PRECAT, Names.getHom()) : null;
    CoreExpression id = cat instanceof CoreClassCallExpression ? Names.getClosedImplementation((CoreClassCallExpression) cat, Names.PRECAT, Names.getId()) : null;
    if (ob == null || hom == null || id == null) {
      typechecker.getErrorReporter().report(new TypeMismatchError(type, DocFactory.text("Iso {\\new Cat _ _ _} -> _"), contextData.getMarker()));
      return null;
    }

    ob = ob.normalize(NormalizationMode.WHNF);
    CoreClassDefinition baseSet = ob instanceof CoreClassCallExpression classCall1 ? Names.findBaseSetSuperClass(classCall1.getDefinition()) : null;
    CoreClassField carrier = baseSet == null || baseSet.getPersonalFields().isEmpty() ? null : baseSet.getPersonalFields().getFirst();
    if (carrier == null) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "The type of objects must be a subclass of 'BaseSet'", ob, contextData.getMarker()));
      return null;
    }
    if (((CoreClassCallExpression) ob).isImplemented(carrier)) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "The underlying set should not be implemented in the type of objects", ob, contextData.getMarker()));
      return null;
    }

    hom = hom.normalize(NormalizationMode.WHNF);
    CoreExpression homBody = hom;
    while (homBody instanceof CoreLamExpression) {
      homBody = ((CoreLamExpression) homBody).getBody().normalize(NormalizationMode.WHNF);
    }
    CoreClassField homFunc = homBody instanceof CoreClassCallExpression classCall1 ? Names.findSuperField(classCall1.getDefinition(), Names.SET_HOM, Names.getSetHomFunc()) : null;
    if (homFunc == null) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "The Hom-set must be a subclass of 'SetHom'", homBody, contextData.getMarker()));
      return null;
    }
    for (CoreClassField field : ((CoreClassCallExpression) homBody).getDefinition().getNotImplementedFields()) {
      boolean ok = true;
      if (!(field.equals(homFunc) || field.isProperty() || ((CoreClassCallExpression) homBody).isImplementedHere(field) || Utils.isProp(field.getResultType()))) {
        typechecker.getErrorReporter().report(new FieldNotPropError(field, contextData.getMarker()));
        ok = false;
      }
      if (!ok) return null;
    }

    TypedExpression obTyped = ob.computeTyped();
    TypedExpression homTyped = hom.computeTyped();
    TypedExpression idTyped = id.computeTyped();

    ConcreteFactory factory = contextData.getFactory();
    ArendRef isoRef = factory.local("e");
    ArendRef sipRef = factory.local("t");
    ArendRef sipRef1 = factory.local("X");
    ArendRef sipRef2 = factory.local("Y");
    ArendRef sipRef3 = factory.local("f");
    ArendRef pathRef = factory.local("q");
    ArendRef extRef = factory.local("x");
    ArendRef jRef1 = factory.local("Z");
    ArendRef jRef2 = factory.local("p");
    ArendRef iRef = factory.local("i");
    ArendRef transportRef = factory.local("Z");

    ConcreteExpression eDom = factory.app(factory.ref(mapDom), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eCod = factory.app(factory.ref(mapCod), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eFunc = factory.app(factory.ref(mapFunc), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eInv = factory.app(factory.ref(isoInv), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eFuncInv = factory.app(factory.ref(isoFuncInv), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eInvFunc = factory.app(factory.ref(isoInvFunc), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eFuncInvPath = factory.app(factory.ref(typechecker.getPrelude().getPathConRef()), true, Collections.singletonList(factory.lam(Collections.singletonList(factory.param(iRef)), factory.app(factory.ref(homFunc.getRef()), false, Collections.singletonList(factory.app(factory.ref(typechecker.getPrelude().getAtRef()), true, Arrays.asList(eFuncInv, factory.ref(iRef))))))));
    ConcreteExpression eInvFuncPath = factory.app(factory.ref(typechecker.getPrelude().getPathConRef()), true, Collections.singletonList(factory.lam(Collections.singletonList(factory.param(iRef)), factory.app(factory.ref(homFunc.getRef()), false, Collections.singletonList(factory.app(factory.ref(typechecker.getPrelude().getAtRef()), true, Arrays.asList(eInvFunc, factory.ref(iRef))))))));

    List<ConcreteClassElement> obFields = new ArrayList<>();
    for (CoreClassField field : ((CoreClassCallExpression) ob).getDefinition().getNotImplementedFields()) {
      if (!((CoreClassCallExpression) ob).isImplementedHere(field)) {
        ConcreteExpression arg = factory.app(factory.ref(typechecker.getPrelude().getAtRef()), true, Arrays.asList(factory.proj(factory.ref(sipRef), field.equals(homFunc) ? 0 : 1), factory.ref(iRef)));
        obFields.add(factory.implementation(field.getRef(), field.equals(homFunc) ? arg : factory.app(factory.ref(field.getRef()), false, Collections.singletonList(arg))));
      }
    }

    ConcreteExpression firstArg = contextData.getArguments().getFirst().getExpression();
    ConcreteExpression argument = factory.meta("sip_arg", new MetaDefinition() {
      @Override
      public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
        return typechecker.withFreeBindings(new FreeBindingsModifier().remove(typechecker.getFreeBinding(isoRef)), tc -> tc.typecheck(firstArg, contextData.getExpectedType()));
      }
    });

    ConcreteLetClause haveClause = factory.letClause(sipRef, Collections.emptyList(), null, factory.app(factory.ref(SIP_Set), true, Arrays.asList(
      factory.lam(Collections.singletonList(factory.param(sipRef1)), factory.classExt(factory.core(obTyped), Collections.singletonList(factory.implementation(carrier.getRef(), factory.ref(sipRef1))))),
      factory.lam(Arrays.asList(factory.param(sipRef1), factory.param(sipRef2), factory.param(sipRef3)), factory.classExt(factory.app(factory.core(homTyped), true, Arrays.asList(factory.ref(sipRef1), factory.ref(sipRef2))), Collections.singletonList(factory.implementation(homFunc.getRef(), factory.ref(sipRef3))))),
      argument,
      factory.newExpr(factory.app(factory.ref(Iso), true, Arrays.asList(eFunc, eInv, eInvFuncPath, eFuncInvPath))),
      eDom, eCod, eFunc, eInv)));
    ConcreteLetClause letClause = factory.letClause(pathRef, Collections.emptyList(), null, factory.app(factory.ref(typechecker.getPrelude().getPathConRef()), true, Collections.singletonList(factory.lam(Collections.singletonList(factory.param(iRef)), factory.newExpr(factory.classExt(factory.core(obTyped), obFields))))));

    return typechecker.typecheck(factory.appBuilder(factory.ref(makeUnivalence)).app(factory.thisExpr(), false).app(factory.lam(Collections.singletonList(factory.param(isoRef)), factory.letExpr(true, false, Collections.singletonList(haveClause), factory.letExpr(false, false, Collections.singletonList(letClause), factory.tuple(factory.ref(pathRef), factory.app(factory.ref(exts), true, Collections.singletonList(factory.lam(Collections.singletonList(factory.param(extRef)),
      factory.app(factory.ref(concat), true, Arrays.asList(
        factory.appBuilder(factory.ref(Jl))
          .app(factory.core(obTyped), false)
          .app(factory.lam(Arrays.asList(factory.param(jRef1), factory.param(jRef2)), factory.app(factory.ref(typechecker.getPrelude().getEqualityRef()), true, Arrays.asList(
            factory.appBuilder(factory.ref(homFunc.getRef()))
              .app(factory.app(factory.ref(transport), true, Arrays.asList(factory.lam(Collections.singletonList(factory.param(transportRef)), factory.app(factory.core(homTyped), true, Arrays.asList(eDom, factory.ref(transportRef)))), factory.ref(jRef2), factory.app(factory.core(idTyped), true, Collections.singletonList(eDom)))), false)
              .app(factory.ref(extRef))
              .build(),
            factory.app(factory.ref(typechecker.getPrelude().getCoerceRef()), true, Arrays.asList(factory.lam(Collections.singletonList(factory.param(iRef)), factory.app(factory.ref(typechecker.getPrelude().getAtRef()), true, Arrays.asList(factory.ref(jRef2), factory.ref(iRef)))), factory.ref(extRef), factory.ref(typechecker.getPrelude().getRightRef())))))))
          .app(factory.ref(typechecker.getPrelude().getIdpRef()))
          .app(factory.ref(pathRef))
          .build(),
        factory.app(factory.proj(factory.ref(sipRef), 2), true, Collections.singletonList(factory.ref(extRef))))))))))))).build(), contextData.getExpectedType());
  }
}
