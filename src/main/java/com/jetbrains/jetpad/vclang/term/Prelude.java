package com.jetbrains.jetpad.vclang.term;

import com.jetbrains.jetpad.vclang.module.Namespace;
import com.jetbrains.jetpad.vclang.module.RootModule;
import com.jetbrains.jetpad.vclang.term.definition.*;
import com.jetbrains.jetpad.vclang.term.pattern.elimtree.BranchElimTreeNode;
import com.jetbrains.jetpad.vclang.term.expr.Expression;
import com.jetbrains.jetpad.vclang.term.expr.arg.Argument;
import com.jetbrains.jetpad.vclang.term.expr.arg.TypeArgument;
import com.jetbrains.jetpad.vclang.term.pattern.elimtree.LeafElimTreeNode;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;

public class Prelude {
  public static ClassDefinition PRELUDE_CLASS;
  public static Namespace PRELUDE;

  public static DataDefinition NAT;
  public static Constructor ZERO, SUC;

  public static DataDefinition INTERVAL;
  public static Constructor LEFT, RIGHT;

  public static FunctionDefinition COERCE;

  public static DataDefinition PATH;
  public static FunctionDefinition PATH_INFIX;
  public static Constructor PATH_CON;

  public static FunctionDefinition AT;

  static {
    PRELUDE_CLASS = new ClassDefinition(RootModule.ROOT, new Name("Prelude"));
    RootModule.ROOT.addDefinition(PRELUDE_CLASS);
    PRELUDE = RootModule.ROOT.getChild(new Name("Prelude"));

    NAT = new DataDefinition(PRELUDE, new Name("Nat"), Abstract.Definition.DEFAULT_PRECEDENCE, new Universe.Type(0, Universe.Type.SET), new ArrayList<TypeArgument>());
    Namespace natNamespace = PRELUDE.getChild(NAT.getName());
    ZERO = new Constructor(natNamespace, new Name("zero"), Abstract.Definition.DEFAULT_PRECEDENCE, new Universe.Type(0, Universe.Type.PROP), new ArrayList<TypeArgument>(), NAT);
    SUC = new Constructor(natNamespace, new Name("suc"), Abstract.Definition.DEFAULT_PRECEDENCE, new Universe.Type(0, Universe.Type.SET), typeArgs(TypeArg(DataCall(NAT))), NAT);
    NAT.addConstructor(ZERO);
    NAT.addConstructor(SUC);

    PRELUDE.addDefinition(NAT);
    PRELUDE.addDefinition(ZERO);
    PRELUDE.addDefinition(SUC);

    INTERVAL = new DataDefinition(PRELUDE, new Name("I"), Abstract.Definition.DEFAULT_PRECEDENCE, new Universe.Type(0, Universe.Type.PROP), new ArrayList<TypeArgument>());
    Namespace intervalNamespace = PRELUDE.getChild(INTERVAL.getName());
    LEFT = new Constructor(intervalNamespace, new Name("left"), Abstract.Definition.DEFAULT_PRECEDENCE, new Universe.Type(0, Universe.Type.PROP), new ArrayList<TypeArgument>(), INTERVAL);
    RIGHT = new Constructor(intervalNamespace, new Name("right"), Abstract.Definition.DEFAULT_PRECEDENCE, new Universe.Type(0, Universe.Type.PROP), new ArrayList<TypeArgument>(), INTERVAL);
    Constructor abstractConstructor = new Constructor(intervalNamespace, new Name("<abstract>"), Abstract.Definition.DEFAULT_PRECEDENCE, new Universe.Type(0, Universe.Type.PROP), new ArrayList<TypeArgument>(), INTERVAL);
    INTERVAL.addConstructor(LEFT);
    INTERVAL.addConstructor(RIGHT);
    INTERVAL.addConstructor(abstractConstructor);

    PRELUDE.addDefinition(INTERVAL);
    PRELUDE.addDefinition(LEFT);
    PRELUDE.addDefinition(RIGHT);

    List<Argument> coerceArguments = new ArrayList<>(3);
    coerceArguments.add(Tele(vars("type"), Pi(DataCall(INTERVAL), Universe(Universe.NO_LEVEL))));
    coerceArguments.add(Tele(vars("elem"), Apps(Index(0), ConCall(LEFT))));
    coerceArguments.add(Tele(vars("point"), DataCall(INTERVAL)));
    BranchElimTreeNode coerceElimTreeNode = branch(0, clause(LEFT, Abstract.Definition.Arrow.RIGHT, Index(0)));
    COERCE = new FunctionDefinition(PRELUDE, new Name("coe"), Abstract.Definition.DEFAULT_PRECEDENCE, coerceArguments, Apps(Index(2), Index(0)), coerceElimTreeNode);

    PRELUDE.addDefinition(COERCE);

    List<TypeArgument> PathParameters = new ArrayList<>(3);
    PathParameters.add(Tele(vars("A"), Pi(DataCall(INTERVAL), Universe(Universe.NO_LEVEL, Universe.Type.NOT_TRUNCATED))));
    PathParameters.add(TypeArg(Apps(Index(0), ConCall(LEFT))));
    PathParameters.add(TypeArg(Apps(Index(1), ConCall(RIGHT))));
    PATH = new DataDefinition(PRELUDE, new Name("Path"), Abstract.Definition.DEFAULT_PRECEDENCE, new Universe.Type(0, Universe.Type.NOT_TRUNCATED), PathParameters);
    List<TypeArgument> pathArguments = new ArrayList<>(1);
    pathArguments.add(TypeArg(Pi("i", DataCall(INTERVAL), Apps(Index(3), Index(0)))));
    PATH_CON = new Constructor(PRELUDE.getChild(PATH.getName()), new Name("path"), Abstract.Definition.DEFAULT_PRECEDENCE, new Universe.Type(0, Universe.Type.NOT_TRUNCATED), pathArguments, PATH);
    PATH.addConstructor(PATH_CON);

    PRELUDE.addDefinition(PATH);
    PRELUDE.addDefinition(PATH_CON);

    List<Argument> pathInfixArguments = new ArrayList<>(3);
    pathInfixArguments.add(Tele(false, vars("A"), Universe(0)));
    pathInfixArguments.add(Tele(vars("a", "a'"), Index(0)));
    Expression pathInfixTerm = Apps(DataCall(PATH), Lam(teleArgs(Tele(vars("_"), DataCall(INTERVAL))), Index(3)), Index(1), Index(0));
    PATH_INFIX = new FunctionDefinition(PRELUDE, new Name("=", Abstract.Definition.Fixity.INFIX), new Abstract.Definition.Precedence(Abstract.Definition.Associativity.NON_ASSOC, (byte) 0), pathInfixArguments, Universe(0), leaf(pathInfixTerm));

    PRELUDE.addDefinition(PATH_INFIX);

    List<Argument> atArguments = new ArrayList<>(5);
    atArguments.add(Tele(false, vars("A"), PathParameters.get(0).getType()));
    atArguments.add(Tele(false, vars("a"), PathParameters.get(1).getType()));
    atArguments.add(Tele(false, vars("a'"), PathParameters.get(2).getType()));
    atArguments.add(Tele(vars("p"), Apps(DataCall(PATH), Index(2), Index(1), Index(0))));
    atArguments.add(Tele(vars("i"), DataCall(INTERVAL)));
    Expression atResultType = Apps(Index(4), Index(0));
    BranchElimTreeNode atElimTree = branch(0,
        clause(LEFT, Index(2)),
        clause(RIGHT, Index(1)),
        clause(null, branch(1, clause(PATH_CON, Apps(Index(1), Index(0)))))
    );
    AT = new FunctionDefinition(PRELUDE, new Name("@", Abstract.Definition.Fixity.INFIX), new Abstract.Definition.Precedence(Abstract.Definition.Associativity.LEFT_ASSOC, (byte) 9), atArguments, atResultType, atElimTree);

    PRELUDE.addDefinition(AT);
  }
}
