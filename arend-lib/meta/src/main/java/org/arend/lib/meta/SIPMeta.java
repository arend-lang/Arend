package org.arend.lib.meta;

import org.arend.ext.FreeBindingsModifier;
import org.arend.ext.concrete.ConcreteClassElement;
import org.arend.ext.concrete.ConcreteFactory;
import org.arend.ext.concrete.ConcreteLetClause;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.core.definition.CoreClassDefinition;
import org.arend.ext.core.definition.CoreClassField;
import org.arend.ext.core.definition.CoreFunctionDefinition;
import org.arend.ext.core.expr.CoreClassCallExpression;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.expr.CoreLamExpression;
import org.arend.ext.core.ops.NormalizationMode;
import org.arend.ext.dependency.Dependency;
import org.arend.ext.error.TypeMismatchError;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.*;
import org.arend.lib.StdExtension;
import org.arend.lib.error.FieldNotPropError;
import org.arend.lib.error.TypeError;
import org.arend.lib.util.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SIPMeta extends BaseMetaDefinition {
  private final StdExtension ext;
  @org.arend.ext.typechecking.meta.Dependency private ArendRef exts;

  @Dependency(module = "Category", name = "Map.C")            public CoreClassField mapCat;
  @Dependency(module = "Category", name = "Map.dom")          public CoreClassField mapDom;
  @Dependency(module = "Category", name = "Map.cod")          public CoreClassField mapCod;
  @Dependency(module = "Category", name = "Map.f")            public CoreClassField mapFunc;
  @Dependency(module = "Category")                            public CoreClassDefinition Iso;
  @Dependency(module = "Category", name = "SplitMono.hinv")   public CoreClassField isoInv;
  @Dependency(module = "Category", name = "Iso.f_hinv")       public CoreClassField isoFuncInv;
  @Dependency(module = "Category", name = "SplitMono.hinv_f") public CoreClassField isoInvFunc;
  @Dependency(module = "Category", name = "Precat.Ob")        public CoreClassField catOb;
  @Dependency(module = "Category", name = "Precat.Hom")       public CoreClassField catHom;
  @Dependency(module = "Category", name = "Precat.id")        public CoreClassField catId;
  @Dependency(module = "Category", name = "Precat.o")         public CoreClassField catComp;
  @Dependency(module = "Set.Category")                        public CoreFunctionDefinition SIP_Set;
  @Dependency(module = "Set.Category")                        public CoreClassDefinition SetHom;
  @Dependency(module = "Set.Category", name = "SetHom.func")  public CoreClassField homFunc;
  @Dependency(module = "Category", name = "Cat.makeUnivalence") public CoreFunctionDefinition makeUnivalence;

  public SIPMeta(StdExtension ext) {
    this.ext = ext;
  }

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
    if (!(type instanceof CoreClassCallExpression && ((CoreClassCallExpression) type).getDefinition() == ext.equationMeta.Equiv)) {
      typechecker.getErrorReporter().report(new TypeMismatchError(type, DocFactory.text("an equivalence"), contextData.getMarker()));
      return null;
    }

    CoreExpression isoArg = ((CoreClassCallExpression) type).getAbsImplementationHere(ext.equationMeta.equivRight);
    if (isoArg != null) isoArg = isoArg.normalize(NormalizationMode.WHNF);
    CoreExpression cat = isoArg instanceof CoreClassCallExpression ? ((CoreClassCallExpression) isoArg).getClosedImplementation(ext.sipMeta.mapCat) : null;
    if (cat == null) {
      typechecker.getErrorReporter().report(new TypeMismatchError(type, DocFactory.text("Iso {_} -> _"), contextData.getMarker()));
      return null;
    }

    cat = cat.computeType().normalize(NormalizationMode.WHNF);
    CoreExpression ob = cat instanceof CoreClassCallExpression ? ((CoreClassCallExpression) cat).getClosedImplementation(ext.sipMeta.catOb) : null;
    CoreExpression hom = cat instanceof CoreClassCallExpression ? ((CoreClassCallExpression) cat).getClosedImplementation(ext.sipMeta.catHom) : null;
    CoreExpression id = cat instanceof CoreClassCallExpression ? ((CoreClassCallExpression) cat).getClosedImplementation(ext.sipMeta.catId) : null;
    if (ob == null || hom == null || id == null) {
      typechecker.getErrorReporter().report(new TypeMismatchError(type, DocFactory.text("Iso {\\new Cat _ _ _} -> _"), contextData.getMarker()));
      return null;
    }

    ob = ob.normalize(NormalizationMode.WHNF);
    if (!(ob instanceof CoreClassCallExpression && ((CoreClassCallExpression) ob).getDefinition().isSubClassOf(ext.BaseSet))) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "The type of objects must be a subclass of 'BaseSet'", ob, contextData.getMarker()));
      return null;
    }
    if (((CoreClassCallExpression) ob).isImplemented(ext.carrier)) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "The underlying set should not be implemented in the type of objects", ob, contextData.getMarker()));
      return null;
    }

    hom = hom.normalize(NormalizationMode.WHNF);
    CoreExpression homBody = hom;
    while (homBody instanceof CoreLamExpression) {
      homBody = ((CoreLamExpression) homBody).getBody().normalize(NormalizationMode.WHNF);
    }
    if (!(homBody instanceof CoreClassCallExpression && ((CoreClassCallExpression) homBody).getDefinition().isSubClassOf(ext.sipMeta.SetHom))) {
      typechecker.getErrorReporter().report(new TypeError(typechecker.getExpressionPrettifier(), "The Hom-set must be a subclass of 'SetHom'", homBody, contextData.getMarker()));
      return null;
    }
    for (CoreClassField field : ((CoreClassCallExpression) homBody).getDefinition().getNotImplementedFields()) {
      boolean ok = true;
      if (!(field == ext.sipMeta.homFunc || field.isProperty() || ((CoreClassCallExpression) homBody).isImplementedHere(field) || Utils.isProp(field.getResultType()))) {
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

    ConcreteExpression eDom = factory.app(factory.ref(ext.sipMeta.mapDom.getRef()), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eCod = factory.app(factory.ref(ext.sipMeta.mapCod.getRef()), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eFunc = factory.app(factory.ref(ext.sipMeta.mapFunc.getRef()), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eInv = factory.app(factory.ref(ext.sipMeta.isoInv.getRef()), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eFuncInv = factory.app(factory.ref(ext.sipMeta.isoFuncInv.getRef()), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eInvFunc = factory.app(factory.ref(ext.sipMeta.isoInvFunc.getRef()), false, Collections.singletonList(factory.ref(isoRef)));
    ConcreteExpression eFuncInvPath = factory.app(factory.ref(ext.prelude.getPathConRef()), true, Collections.singletonList(factory.lam(Collections.singletonList(factory.param(iRef)), factory.app(factory.ref(ext.sipMeta.homFunc.getRef()), false, Collections.singletonList(factory.app(factory.ref(ext.prelude.getAtRef()), true, Arrays.asList(eFuncInv, factory.ref(iRef))))))));
    ConcreteExpression eInvFuncPath = factory.app(factory.ref(ext.prelude.getPathConRef()), true, Collections.singletonList(factory.lam(Collections.singletonList(factory.param(iRef)), factory.app(factory.ref(ext.sipMeta.homFunc.getRef()), false, Collections.singletonList(factory.app(factory.ref(ext.prelude.getAtRef()), true, Arrays.asList(eInvFunc, factory.ref(iRef))))))));

    List<ConcreteClassElement> obFields = new ArrayList<>();
    for (CoreClassField field : ((CoreClassCallExpression) ob).getDefinition().getNotImplementedFields()) {
      if (!((CoreClassCallExpression) ob).isImplementedHere(field)) {
        ConcreteExpression arg = factory.app(factory.ref(ext.prelude.getAtRef()), true, Arrays.asList(factory.proj(factory.ref(sipRef), field == ext.sipMeta.homFunc ? 0 : 1), factory.ref(iRef)));
        obFields.add(factory.implementation(field.getRef(), field == ext.sipMeta.homFunc ? arg : factory.app(factory.ref(field.getRef()), false, Collections.singletonList(arg))));
      }
    }

    ConcreteExpression firstArg = contextData.getArguments().getFirst().getExpression();
    ConcreteExpression argument = factory.meta("sip_arg", new MetaDefinition() {
      @Override
      public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
        return typechecker.withFreeBindings(new FreeBindingsModifier().remove(typechecker.getFreeBinding(isoRef)), tc -> tc.typecheck(firstArg, contextData.getExpectedType()));
      }
    });

    ConcreteLetClause haveClause = factory.letClause(sipRef, Collections.emptyList(), null, factory.app(factory.ref(ext.sipMeta.SIP_Set.getRef()), true, Arrays.asList(
      factory.lam(Collections.singletonList(factory.param(sipRef1)), factory.classExt(factory.core(obTyped), Collections.singletonList(factory.implementation(ext.carrier.getRef(), factory.ref(sipRef1))))),
      factory.lam(Arrays.asList(factory.param(sipRef1), factory.param(sipRef2), factory.param(sipRef3)), factory.classExt(factory.app(factory.core(homTyped), true, Arrays.asList(factory.ref(sipRef1), factory.ref(sipRef2))), Collections.singletonList(factory.implementation(ext.sipMeta.homFunc.getRef(), factory.ref(sipRef3))))),
      argument,
      factory.newExpr(factory.app(factory.ref(ext.sipMeta.Iso.getRef()), true, Arrays.asList(eFunc, eInv, eInvFuncPath, eFuncInvPath))),
      eDom, eCod, eFunc, eInv)));
    ConcreteLetClause letClause = factory.letClause(pathRef, Collections.emptyList(), null, factory.app(factory.ref(ext.prelude.getPathConRef()), true, Collections.singletonList(factory.lam(Collections.singletonList(factory.param(iRef)), factory.newExpr(factory.classExt(factory.core(obTyped), obFields))))));

    return typechecker.typecheck(factory.appBuilder(factory.ref(ext.sipMeta.makeUnivalence.getRef())).app(factory.thisExpr(), false).app(factory.lam(Collections.singletonList(factory.param(isoRef)), factory.letExpr(true, false, Collections.singletonList(haveClause), factory.letExpr(false, false, Collections.singletonList(letClause), factory.tuple(factory.ref(pathRef), factory.app(factory.ref(exts), true, Collections.singletonList(factory.lam(Collections.singletonList(factory.param(extRef)),
      factory.app(factory.ref(ext.concat.getRef()), true, Arrays.asList(
        factory.appBuilder(factory.ref(ext.Jl.getRef()))
          .app(factory.core(obTyped), false)
          .app(factory.lam(Arrays.asList(factory.param(jRef1), factory.param(jRef2)), factory.app(factory.ref(ext.prelude.getEqualityRef()), true, Arrays.asList(
            factory.appBuilder(factory.ref(ext.sipMeta.homFunc.getRef()))
              .app(factory.app(factory.ref(ext.transport.getRef()), true, Arrays.asList(factory.lam(Collections.singletonList(factory.param(transportRef)), factory.app(factory.core(homTyped), true, Arrays.asList(eDom, factory.ref(transportRef)))), factory.ref(jRef2), factory.app(factory.core(idTyped), true, Collections.singletonList(eDom)))), false)
              .app(factory.ref(extRef))
              .build(),
            factory.app(factory.ref(ext.prelude.getCoerceRef()), true, Arrays.asList(factory.lam(Collections.singletonList(factory.param(iRef)), factory.app(factory.ref(ext.prelude.getAtRef()), true, Arrays.asList(factory.ref(jRef2), factory.ref(iRef)))), factory.ref(extRef), factory.ref(ext.prelude.getRightRef())))))))
          .app(factory.ref(ext.prelude.getIdpRef()))
          .app(factory.ref(pathRef))
          .build(),
        factory.app(factory.proj(factory.ref(sipRef), 2), true, Collections.singletonList(factory.ref(extRef))))))))))))).build(), contextData.getExpectedType());
  }
}
