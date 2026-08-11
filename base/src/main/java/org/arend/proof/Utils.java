package org.arend.proof;

import org.arend.ext.util.Pair;
import org.arend.term.concrete.Concrete;
import org.arend.util.Triple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Utils {
    public static List<Triple<Concrete.GeneralDefinition, List<Concrete.Expression>, Concrete.Expression>> getSignatures(Concrete.ResolvableDefinition def) {
        List<Triple<Concrete.GeneralDefinition, List<Concrete.Expression>, Concrete.Expression>> result = new ArrayList<>();
        if (def instanceof Concrete.FunctionDefinition funcDef) {
            Concrete.Expression resultType = funcDef.getResultType();
            if (resultType != null) {
                List<Concrete.Expression> temporaryParameters = new ArrayList<>();
                for (Concrete.Parameter param : funcDef.getParameters()) {
                    if (param.getType() != null) {
                        temporaryParameters.add(param.getType());
                    }
                }
                Pair<List<Concrete.Expression>, Concrete.Expression> deconstructed = deconstructPi(new Concrete.PiExpression(null, temporaryParameters.stream().map(param -> new Concrete.TypeParameter(true, param, false)).toList(), resultType));
                List<Concrete.Expression> parameters = new ArrayList<>(deconstructed.proj1);
                Concrete.Expression codomain = deconstructed.proj2;
                result.add(new Triple<>(def, parameters, codomain));
                return result;
            }
        } else if (def instanceof Concrete.DataDefinition dataDef) {
            for (Concrete.ConstructorClause clause : dataDef.getConstructorClauses()) {
                for (Concrete.Constructor constructor : clause.getConstructors()) {
                    List<Concrete.Expression> parameters = new ArrayList<>();
                    for (Concrete.TypeParameter param : constructor.getParameters()) {
                        parameters.add(param.getType());
                    }
                    Concrete.Expression codomain = new Concrete.ReferenceExpression(dataDef.getData().getData(), dataDef.getData());
                    result.add(new Triple<>(constructor, parameters, codomain));
                }
            }
        } else if (def instanceof Concrete.ClassDefinition classDef) {
            for (Concrete.ClassElement element : classDef.getElements()) {
                if (element instanceof Concrete.ClassField field) {
                    List<Concrete.Expression> parameters = new ArrayList<>();
                    for (Concrete.TypeParameter param : field.getParameters()) {
                        parameters.add(param.getType());
                    }
                    Pair<List<Concrete.Expression>, Concrete.Expression> deconstructed = deconstructPi(field.getResultType());
                    parameters.addAll(deconstructed.proj1);
                    Concrete.Expression codomain = deconstructed.proj2;
                    result.add(new Triple<>(field, parameters, codomain));
                }
            }
        }
        return result;
    }

    private static Pair<List<Concrete.Expression>, Concrete.Expression> deconstructPi(Concrete.Expression expr) {
        if (expr instanceof Concrete.PiExpression piExpr) {
            Pair<List<Concrete.Expression>, Concrete.Expression> piRes = deconstructPi(piExpr.getCodomain());
            List<Concrete.Expression> params = new ArrayList<>();
            for (Concrete.TypeParameter param : piExpr.getParameters()) {
                params.add(param.getType());
            }
            params.addAll(piRes.proj1);
            return new Pair<>(params, piRes.proj2);
        } else {
            return new Pair<>(Collections.emptyList(), expr);
        }
    }
}
