package org.arend.module.serialization;

import org.arend.core.context.binding.LevelVariable;
import org.arend.core.context.binding.ParamLevelVariable;
import org.arend.core.definition.*;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.doc.DocFactory;
import org.arend.ext.reference.Precedence;
import org.arend.ext.serialization.DeserializationException;
import org.arend.ext.typechecking.DefinitionListener;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.ext.module.ModuleLocation;
import org.arend.module.scopeprovider.ModuleScopeProvider;
import org.arend.ext.typechecking.meta.MetaTypechecker;
import org.arend.naming.reference.*;
import org.arend.naming.scope.EmptyScope;
import org.arend.naming.scope.Scope;
import org.arend.prelude.Prelude;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.*;
import org.arend.typechecking.order.dependency.DependencyListener;
import org.arend.ext.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModuleDeserialization {
  private final ModuleProtos.Module myModuleProto;
  private final SimpleCallTargetProvider myCallTargetProvider = new SimpleCallTargetProvider();
  private final List<Pair<DefinitionProtos.Definition, Definition>> myDefinitions = new ArrayList<>();
  private final SerializableKeyRegistryImpl myKeyRegistry;
  private final DefinitionListener myDefinitionListener;

  public ModuleDeserialization(ModuleProtos.Module moduleProto, SerializableKeyRegistryImpl keyRegistry, DefinitionListener definitionListener) {
    myModuleProto = moduleProto;
    myKeyRegistry = keyRegistry;
    myDefinitionListener = definitionListener;
  }

  public ModuleProtos.Module getModuleProto() {
    return myModuleProto;
  }

  public void readModule(ModuleScopeProvider moduleScopeProvider, DependencyListener dependencyListener) throws DeserializationException {
    if (myModuleProto.getVersion() != ModuleSerialization.VERSION) {
      throw new DeserializationException("Version mismatch:\nLanguage version: " + ModuleSerialization.VERSION + "\nLibrary binaries version: " + myModuleProto.getVersion());
    }

    for (ModuleProtos.ModuleCallTargets moduleCallTargets : myModuleProto.getModuleCallTargetsList()) {
      ModulePath module = new ModulePath(moduleCallTargets.getNameList());
      Scope scope = moduleScopeProvider.forModule(module);
      if (scope == null) {
        throw new DeserializationException("Cannot find module: " + module);
      }

      for (ModuleProtos.CallTargetTree callTargetTree : moduleCallTargets.getCallTargetTreeList()) {
        fillInCallTargetTree(null, callTargetTree, scope, module, EmptyScope.INSTANCE, null);
      }
    }

    DefinitionDeserialization defDeserialization = new DefinitionDeserialization(myCallTargetProvider, dependencyListener, myKeyRegistry, myDefinitionListener);
    for (Pair<DefinitionProtos.Definition, Definition> pair : myDefinitions) {
      defDeserialization.fillInDefinition(pair.proj1, pair.proj2);
    }
    myDefinitions.clear();
  }

  private void fillInCallTargetTree(String parentName, ModuleProtos.CallTargetTree callTargetTree, Scope scope, ModulePath module, Scope parentScope, TCDefReferable parent) throws DeserializationException {
    TCDefReferable referable = null;
    if (callTargetTree.getIndex() > 0) {
      // Ensure parent is resolved early: it is needed to look up personal fields/constructors
      // before falling back to scope lookup.
      if (parent == null && parentName != null) {
        Referable parentRef = parentScope.resolveName(parentName);
        if (parentRef instanceof TCDefReferable) parent = (TCDefReferable) parentRef;
      }

      // When the parent is a class/data definition, look up personal fields/constructors
      // FIRST.  Coclause functions created by "\default foo x => ..." (without \as) are
      // stored as dynamic subgroups with the same name as the field they implement, so a
      // plain scope.resolveName() would incorrectly return the function instead of the field.
      Referable referable1 = null;
      if (parent != null && parent.getTypechecked() instanceof ClassDefinition parentDef) {
        for (ClassField field : parentDef.getPersonalFields()) {
          if (field.getName().equals(callTargetTree.getName())) {
            referable1 = field.getReferable();
            break;
          }
        }
      } else if (parent != null && parent.getTypechecked() instanceof DataDefinition parentDef) {
        for (Constructor constructor : parentDef.getConstructors()) {
          if (constructor.getName().equals(callTargetTree.getName())) {
            referable1 = constructor.getReferable();
            break;
          }
        }
      }

      // Fall back to scope lookup when not found as a personal field/constructor.
      if (referable1 == null) {
        referable1 = scope.resolveName(callTargetTree.getName(), null);
      }

      referable = referable1 instanceof TCDefReferable ? (TCDefReferable) referable1 : null;
      if (referable == null && module.equals(Prelude.MODULE_PATH) && "Fin".equals(parentName)) {
        if (callTargetTree.getName().equals("zero")) {
          referable = Prelude.FIN_ZERO.getReferable();
        } else if (callTargetTree.getName().equals("suc")) {
          referable = Prelude.FIN_SUC.getReferable();
        }
      }
      if (referable == null) {
        if (referable1 instanceof TCLevelReferable) {
          // Level-parameter referables (from \plevels/\hlevels) are LocatedReferable but not
          // TCDefReferable.  Store them directly so that pLevelsParent/hLevelsParent can be
          // restored during fillInDefinition.
          myCallTargetProvider.putCallTarget(callTargetTree.getIndex(), (LocatedReferable) referable1);
        } else {
          throw new DeserializationException("Cannot resolve reference '" + callTargetTree.getName() + "' in " + module);
        }
      } else {
        myCallTargetProvider.putCallTarget(callTargetTree.getIndex(), referable);
      }
    }

    List<ModuleProtos.CallTargetTree> subtreeList = callTargetTree.getSubtreeList();
    if (!subtreeList.isEmpty()) {
      Scope subscope = scope.resolveNamespace(callTargetTree.getName());
      if (subscope == null) {
        throw new DeserializationException("Cannot resolve reference '" + callTargetTree.getName() + "' in " + module);
      }

      for (ModuleProtos.CallTargetTree tree : subtreeList) {
        fillInCallTargetTree(callTargetTree.getName(), tree, subscope, module, scope, referable);
      }
    }
  }

  public void readDefinitions(ConcreteGroup group) throws DeserializationException {
    readDefinitions(myModuleProto.getGroup(), group);
  }

  public void readDefinitions(ModuleProtos.Group groupProto, ConcreteGroup group) throws DeserializationException {
    if (groupProto.hasDefinition()) {
      LocatedReferable referable = group.referable();
      if (!(referable instanceof TCDefReferable tcReferable)) {
        throw new DeserializationException("'" + referable + "' is not a definition");
      }

      Definition def = readDefinition(groupProto.getDefinition(), tcReferable, false);
      tcReferable.setTypechecked(def);
      myCallTargetProvider.putCallTarget(groupProto.getReferable().getIndex(), def);
      myDefinitions.add(new Pair<>(groupProto.getDefinition(), def));

      List<? extends InternalReferable> fields = group.getFields();
      if (!fields.isEmpty()) {
        Map<String, DefinitionProtos.Definition.ClassData.Field> fieldMap = new HashMap<>();
        for (DefinitionProtos.Definition.ClassData.Field field : groupProto.getDefinition().getClass_().getPersonalFieldList()) {
          fieldMap.put(field.getReferable().getName(), field);
        }

        for (InternalReferable field : fields) {
          DefinitionProtos.Definition.ClassData.Field fieldProto = fieldMap.get(field.getRefName());
          if (fieldProto == null) {
            throw new DeserializationException("Cannot locate '" + field + "'");
          }
          if (!(field instanceof FieldReferableImpl absField)) {
            throw new DeserializationException("Incorrect field '" + field.getRefName() + "'");
          }

          assert def instanceof ClassDefinition;
          ClassField res = new ClassField(absField, (ClassDefinition) def);
          if (fieldProto.getIsProperty()) {
            res.setIsProperty();
          }
          ((ClassDefinition) def).addPersonalField(res);
          absField.setTypechecked(res);
          myCallTargetProvider.putCallTarget(fieldProto.getReferable().getIndex(), res);
        }
      }

      List<? extends InternalReferable> constructors = group.getConstructors();
      if (!constructors.isEmpty()) {
        Map<String, DefinitionProtos.Definition.DataData.Constructor> constructorMap = new HashMap<>();
        for (DefinitionProtos.Definition.DataData.Constructor constructor : groupProto.getDefinition().getData().getConstructorList()) {
          constructorMap.put(constructor.getReferable().getName(), constructor);
        }

        for (InternalReferable constructor : constructors) {
          DefinitionProtos.Definition.DataData.Constructor constructorProto = constructorMap.get(constructor.getRefName());
          if (constructorProto == null) {
            throw new DeserializationException("Cannot locate '" + constructor + "'");
          }

          assert def instanceof DataDefinition;
          Constructor res = new Constructor(constructor, (DataDefinition) def);
          ((DataDefinition) def).addConstructor(res);
          constructor.setTypechecked(res);
          myCallTargetProvider.putCallTarget(constructorProto.getReferable().getIndex(), res);
        }
      }
    }

    List<? extends ConcreteStatement> statements = group.statements();
    if (!groupProto.getSubgroupList().isEmpty() && !statements.isEmpty()) {
      Map<String, ModuleProtos.Group> subgroupMap = new HashMap<>();
      for (ModuleProtos.Group subgroup : groupProto.getSubgroupList()) {
        subgroupMap.put(subgroup.getReferable().getName(), subgroup);
      }
      for (ConcreteStatement statement : statements) {
        ConcreteGroup subgroup = statement.group();
        if (subgroup != null) {
          ModuleProtos.Group subgroupProto = subgroupMap.get(subgroup.referable().getRefName());
          if (subgroupProto != null) {
            readDefinitions(subgroupProto, subgroup);
          }
        }
      }
    }

    List<? extends ConcreteGroup> dynSubgroups = group.dynamicGroups();
    if (!groupProto.getDynamicSubgroupList().isEmpty() && !dynSubgroups.isEmpty()) {
      Map<String, ModuleProtos.Group> subgroupMap = new HashMap<>();
      for (ModuleProtos.Group subgroup : groupProto.getDynamicSubgroupList()) {
        subgroupMap.put(subgroup.getReferable().getName(), subgroup);
      }
      for (ConcreteGroup subgroup : dynSubgroups) {
        ModuleProtos.Group subgroupProto = subgroupMap.get(subgroup.referable().getRefName());
        if (subgroupProto != null) {
          readDefinitions(subgroupProto, subgroup);
        }
      }
    }
  }

  @NotNull
  public ConcreteGroup readGroup(ModuleLocation modulePath) throws DeserializationException {
    return readGroup(myModuleProto.getGroup(), null, modulePath);
  }

  private static GlobalReferable.Kind getDefinitionKind(DefinitionProtos.Definition defProto) {
    DefinitionProtos.Definition.DefinitionDataCase kind = defProto.getDefinitionDataCase();
    switch (kind) {
      case CLASS -> {
        return defProto.getClass_().getIsRecord() ? GlobalReferable.Kind.RECORD : GlobalReferable.Kind.CLASS;
      }
      case DATA -> {
        return GlobalReferable.Kind.DATA;
      }
      case FUNCTION -> {
        var fKind = defProto.getFunction().getKind();
        return fKind == DefinitionProtos.Definition.FunctionKind.INSTANCE ? GlobalReferable.Kind.INSTANCE : fKind == DefinitionProtos.Definition.FunctionKind.COCLAUSE || fKind == DefinitionProtos.Definition.FunctionKind.COCLAUSE_LEMMA ? GlobalReferable.Kind.COCLAUSE_FUNCTION : GlobalReferable.Kind.FUNCTION;
      }
      case CONSTRUCTOR -> {
        return GlobalReferable.Kind.DEFINED_CONSTRUCTOR;
      }
      case META -> {
        return GlobalReferable.Kind.META;
      }
      default -> {
        return GlobalReferable.Kind.OTHER;
      }
    }
  }

  @NotNull
  private ConcreteGroup readGroup(ModuleProtos.Group groupProto, ConcreteGroup parent, ModuleLocation modulePath) throws DeserializationException {
    DefinitionProtos.Referable referableProto = groupProto.getReferable();
    LocatedReferable referable;
    GlobalReferable.Kind kind = getDefinitionKind(groupProto.getDefinition());
    if (parent == null) {
      referable = new FullModuleReferable(modulePath);
    } else if (kind == GlobalReferable.Kind.META) {
      referable = new MetaReferable(null, AccessModifier.PUBLIC, readPrecedence(referableProto.getPrecedence()), referableProto.getName(), readAliasPrecedence(referableProto), referableProto.getAliasName().isEmpty() ? null : referableProto.getAliasName(), new MetaTypechecker() {}, null, parent.referable());
    } else {
      referable = new LocatedReferableImpl(null, AccessModifier.PUBLIC, readPrecedence(referableProto.getPrecedence()), referableProto.getName(), readAliasPrecedence(referableProto), referableProto.getAliasName().isEmpty() ? null : referableProto.getAliasName(), parent.referable(), kind);
    }

    if (referable instanceof TCDefReferable && groupProto.hasDefinition()) {
      Definition def = readDefinition(groupProto.getDefinition(), (TCDefReferable) referable, true);
      ((TCDefReferable) referable).setTypechecked(def);
      myCallTargetProvider.putCallTarget(referableProto.getIndex(), def);
      myDefinitions.add(new Pair<>(groupProto.getDefinition(), def));
    }

    List<ConcreteStatement> statements = new ArrayList<>(groupProto.getSubgroupCount());
    List<ConcreteGroup> dynamicSubgroups = new ArrayList<>(groupProto.getDynamicSubgroupCount());
    ConcreteGroup group = new ConcreteGroup(DocFactory.nullDoc(), referable, null, statements, dynamicSubgroups, Collections.emptyList());
    for (ModuleProtos.Group subgroup : groupProto.getSubgroupList()) {
      statements.add(new ConcreteStatement(readGroup(subgroup, group, modulePath), null, null, null));
    }
    for (ModuleProtos.Group dynSubgroup : groupProto.getDynamicSubgroupList()) {
      dynamicSubgroups.add(readGroup(dynSubgroup, group, modulePath));
    }

    // Reconstruct plevels/hlevels declarations so they are visible in scope.
    for (ModuleProtos.LevelsDeclaration decl : groupProto.getPlevelsDeclarationList()) {
      statements.add(new ConcreteStatement(null, null, buildLevelsDeclaration(decl, true, referable), null));
    }
    for (ModuleProtos.LevelsDeclaration decl : groupProto.getHlevelsDeclarationList()) {
      statements.add(new ConcreteStatement(null, null, null, buildLevelsDeclaration(decl, false, referable)));
    }

    return group;
  }

  private static Concrete.LevelsDefinition buildLevelsDeclaration(
      ModuleProtos.LevelsDeclaration decl, boolean isPLevels, LocatedReferable parent) {
    List<TCLevelReferable> refs = new ArrayList<>(decl.getNameCount());
    LevelDefinition levelDef = new LevelDefinition(isPLevels, decl.getIncreasing(), refs, parent);
    for (String name : decl.getNameList()) {
      refs.add(new TCLevelReferable(null, name, levelDef));
    }
    return new Concrete.LevelsDefinition(null, refs, decl.getIncreasing(), isPLevels);
  }

  private List<LevelVariable> readLevelParameters(List<DefinitionProtos.Definition.LevelParameter> parameters, boolean isStd) {
    if (isStd) return null;
    List<LevelVariable> result = new ArrayList<>(parameters.size());
    for (DefinitionProtos.Definition.LevelParameter parameter : parameters) {
      LevelVariable base = parameter.getIsPlevel() ? LevelVariable.PVAR : LevelVariable.HVAR;
      int size = parameter.getSize();
      if (size == -1) {
        result.add(base);
      } else {
        result.add(new ParamLevelVariable(base.getType(), parameter.getName(), parameter.getIndex(), size));
      }
    }
    return result;
  }

  private Definition readDefinition(DefinitionProtos.Definition defProto, TCDefReferable referable, boolean fillInternalDefinitions) throws DeserializationException {
    final Definition def;
    switch (defProto.getDefinitionDataCase()) {
      case CLASS -> {
        ClassDefinition classDef = new ClassDefinition(referable);
        for (DefinitionProtos.Definition.ClassData.Field fieldProto : defProto.getClass_().getPersonalFieldList()) {
          DefinitionProtos.Referable fieldReferable = fieldProto.getReferable();
          if (fillInternalDefinitions || fieldProto.getIsRealParameter()) {
            FieldReferableImpl absField = new FieldReferableImpl(null, AccessModifier.PUBLIC, readPrecedence(fieldReferable.getPrecedence()), fieldReferable.getName(), readAliasPrecedence(fieldReferable), fieldReferable.getAliasName().isEmpty() ? null : fieldReferable.getAliasName(), fieldProto.getIsExplicit(), fieldProto.getIsParameter(), fieldProto.getIsRealParameter(), referable);
            ClassField res = new ClassField(absField, classDef);
            if (fieldProto.getIsProperty()) {
              res.setIsProperty();
            }
            classDef.addPersonalField(res);
            absField.setTypechecked(res);
            myCallTargetProvider.putCallTarget(fieldReferable.getIndex(), res);
          }
        }
        def = classDef;
      }
      case DATA -> {
        DataDefinition dataDef = new DataDefinition(referable);
        if (fillInternalDefinitions) {
          for (DefinitionProtos.Definition.DataData.Constructor constructor : defProto.getData().getConstructorList()) {
            DefinitionProtos.Referable conReferable = constructor.getReferable();
            InternalReferable absConstructor = new InternalReferableImpl(null, AccessModifier.PUBLIC, readPrecedence(conReferable.getPrecedence()), conReferable.getName(), readAliasPrecedence(conReferable), conReferable.getAliasName().isEmpty() ? null : conReferable.getAliasName(), true, referable, LocatedReferableImpl.Kind.CONSTRUCTOR);
            Constructor res = new Constructor(absConstructor, dataDef);
            dataDef.addConstructor(res);
            absConstructor.setTypechecked(res);
            myCallTargetProvider.putCallTarget(conReferable.getIndex(), res);
          }
        }
        def = dataDef;
      }
      case FUNCTION -> def = new FunctionDefinition(referable);
      case CONSTRUCTOR -> def = new DConstructor(referable);
      case META -> {
        if (!(referable instanceof MetaReferable metaRef)) {
          throw new DeserializationException("'" + referable + "' is not a meta definition");
        }
        def = new MetaTopDefinition(metaRef);
      }
      default -> throw new DeserializationException("Unknown Definition kind: " + defProto.getDefinitionDataCase());
    }
    if (def instanceof TopLevelDefinition) {
      ((TopLevelDefinition) def).setLevelParameters(readLevelParameters(defProto.getLevelParamList(), defProto.getIsStdLevels()));
    } else {
      ((MetaTopDefinition) def).setLevelParameters(readLevelParameters(defProto.getLevelParamList(), defProto.getIsStdLevels()));
    }
    return def;
  }

  private static Precedence readPrecedence(DefinitionProtos.Precedence precedenceProto) throws DeserializationException {
    Precedence.Associativity assoc = switch (precedenceProto.getAssoc()) {
      case LEFT -> Precedence.Associativity.LEFT_ASSOC;
      case RIGHT -> Precedence.Associativity.RIGHT_ASSOC;
      case NON_ASSOC -> Precedence.Associativity.NON_ASSOC;
      default -> throw new DeserializationException("Unknown associativity: " + precedenceProto.getAssoc());
    };
    return new Precedence(assoc, (byte) precedenceProto.getPriority(), precedenceProto.getInfix());
  }

  /** Returns the alias precedence, or {@link Precedence#DEFAULT} when none is stored. */
  private static Precedence readAliasPrecedence(DefinitionProtos.Referable referableProto) throws DeserializationException {
    if (referableProto.getAliasName().isEmpty()) {
      return Precedence.DEFAULT;
    }
    return readPrecedence(referableProto.getAliasPrecedence());
  }
}
