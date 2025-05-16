package AnalizadorLexicoJ2G;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidadorEstructural {

    private TablaSimbolos tablaSimbolosGlobal; // Para acceder a palabras reservadas

    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal) {
        this.tablaSimbolosGlobal = tablaSimbolosGlobal;
    }

    private Map<String, String> cargarReglasRegexEnMemoria() {
        Map<String, String> reglas = new HashMap<>();
        String varOrIdRegex = "([a-z][a-zA-Z0-9_]{0,63}|id[0-9]+)";
        String optionalPromptRegex = "(?:\\((\"(?:\\\\.|[^\"\\\\])*\")\\))?";

        // Definición de Variables
        reglas.put("VAR_NAME", "^[a-z][a-zA-Z0-9_]{0,63}$"); 
        reglas.put("ID_NAME", "^id[0-9]+$");                
        reglas.put("VALID_LHS_VAR", "^" + varOrIdRegex + "$"); 

        // Literales y Constantes
        reglas.put("STRING_LITERAL", "\"(?:\\\\.|[^\"\\\\])*\"");
        reglas.put("NUMBER_LITERAL", "[0-9]+");
        reglas.put("BOOLEAN_LITERAL", "(TRUE|FALSE|true|false)"); 
        reglas.put("ANY_LITERAL_OR_VAR", "(" + varOrIdRegex + "|(\"(?:\\\\.|[^\"\\\\])*\")|([0-9]+)|(TRUE|FALSE|true|false))");

        // Estructura Principal del Programa
        reglas.put("MAIN_FUNC_START", "^FUNC\\s+J2G\\s+Main\\s*\\(\\s*\\)\\s*\\{"); 
        reglas.put("BLOCK_END", "^\\}$"); 

        // Declaración de Variables
        reglas.put("VAR_DECL_NO_INIT", "^(INT|STR|BOOL)\\s+" + varOrIdRegex + "\\s*;");
        reglas.put("VAR_DECL_CON_INIT", "^(INT|STR|BOOL)\\s+" + varOrIdRegex + "\\s*:=\\s*((\"(?:\\\\.|[^\"\\\\])*\")|([0-9]+)|(TRUE|FALSE|true|false)|" + varOrIdRegex + ")\\s*;");
        reglas.put("ASSIGNMENT", "^" + varOrIdRegex + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*(.+)\\s*;");

        // Funciones Predefinidas de Input
        reglas.put("INPUT_STR_STMT", "^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Str\\(\\)\\s*;");
        reglas.put("INPUT_INT_STMT", "^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Int\\(\\)\\s*;");
        reglas.put("INPUT_BOOL_STMT", "^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Bool\\(\\)\\s*;");

        // Estructuras de Control
        reglas.put("IF_STMT", "^if\\s*\\((.+)\\)\\s*\\{"); 
        reglas.put("ELSE_STMT", "^else\\s*\\{");
        reglas.put("BLOCK_END_ELSE_STMT", "^\\}\\s*else\\s*\\{"); 
        reglas.put("SWITCH_STMT", "^sw\\s*\\((.+)\\)\\s*\\{");
        reglas.put("CASE_STMT", "^caso\\s+(\"(?:\\\\.|[^\"\\\\])*\"|" + varOrIdRegex + ")\\s*:"); 
        reglas.put("DEFAULT_STMT", "^por_defecto\\s*:");
        reglas.put("DETENER_STMT", "^detener\\s*;");
        reglas.put("FOR_STMT", "^for\\s*\\((.*);\\s*(.*);\\s*(.*)\\)\\s*\\{"); 
        reglas.put("WHILE_STMT", "^while\\s*\\((.+)\\)\\s*\\{");
        reglas.put("DO_WHILE_DO_STMT", "^do\\s*\\{");
        reglas.put("DO_WHILE_WHILE_STMT", "^\\}\\s*while\\s*\\((.+)\\)\\s*;");

        // Funciones Predefinidas
        reglas.put("PRINT_STMT", "^Print\\s*\\((.*?)\\)\\s*;"); 
        
        return reglas;
    }
    
    private boolean isValidSourceVarName(String name, Map<String, String> reglas) {
        return name.matches(reglas.getOrDefault("VAR_NAME", "^[a-z][a-zA-Z0-9_]{0,63}$"));
    }

    private boolean isGeneratedIdName(String name, Map<String, String> reglas) {
        return name.matches(reglas.getOrDefault("ID_NAME", "^id[0-9]+$"));
    }
    
    private List<String> checkVariableUsage(String varName, String context, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap) {
        List<String> errors = new ArrayList<>();
        if (this.tablaSimbolosGlobal.getPalabrasReservadasYSimbolosConocidos().contains(varName)) {
            return errors; 
        }
        boolean isValidFormat = isValidSourceVarName(varName, reglas) || isGeneratedIdName(varName, reglas);
        if (!isValidFormat) {
            errors.add("Nombre de variable inválido '" + varName + "' " + context + ".");
        } else { 
            if (isValidSourceVarName(varName, reglas) && !declaredVariablesTypeMap.containsKey(varName)) {
                errors.add("Variable '" + varName + "' no declarada " + context + ".");
            }
        }
        return errors;
    }

    private List<String> checkExpressionVariables(String expression, String context, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap) {
        List<String> errors = new ArrayList<>();
        if (expression == null || expression.trim().isEmpty()) return errors;

        List<String> localTabsimTokens = new ArrayList<>(this.tablaSimbolosGlobal.getPalabrasReservadasYSimbolosConocidos());
        localTabsimTokens.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        StringBuilder regexBuilder = new StringBuilder();
        int currentGroup = 1; 
        final int STRING_LITERAL_GROUP = currentGroup++; regexBuilder.append("(\"(?:\\\\.|[^\"\\\\])*\")"); 
        Map<Integer, String> keywordOperatorGroupMap = new HashMap<>();
        for (String token : localTabsimTokens) {
            keywordOperatorGroupMap.put(currentGroup, token);
            regexBuilder.append("|(").append(Pattern.quote(token)).append(")"); currentGroup++;
        }
        final int IDENTIFIER_GROUP = currentGroup++; regexBuilder.append("|([a-zA-Z_][a-zA-Z0-9_]*)"); 
        final int NUMBER_GROUP = currentGroup++; regexBuilder.append("|([0-9]+)"); 
        Pattern tokenPattern = Pattern.compile(regexBuilder.toString());
        Matcher matcher = tokenPattern.matcher(expression);
        while (matcher.find()) {
            if (matcher.group(STRING_LITERAL_GROUP) != null || matcher.group(NUMBER_GROUP) != null) continue;
            boolean isKeywordOrOperator = false;
            for (int groupIndex : keywordOperatorGroupMap.keySet()) if (matcher.group(groupIndex) != null) { isKeywordOrOperator = true; break; }
            if (isKeywordOrOperator) continue;
            if (matcher.group(IDENTIFIER_GROUP) != null) {
                String varName = matcher.group(IDENTIFIER_GROUP);
                if (!varName.equals("Input") && !varName.equals("Str") && !varName.equals("Int") && !varName.equals("Bool")) {
                    errors.addAll(checkVariableUsage(varName, context, reglas, declaredVariablesTypeMap));
                }
            }
        }
        return errors;
    }

    public void validarEstructuraConRegex(String codigoLimpioFormateado) { // Ya no necesita archivoReglasRegex como parámetro
        Map<String, String> reglas = cargarReglasRegexEnMemoria(); // Carga las reglas internamente
        Map<String, String> declaredVariablesTypeMap = new HashMap<>(); 
        int globalBraceBalance = 0; 
        List<String> globalStructuralErrors = new ArrayList<>(); 

        boolean mainFunctionDeclared = false;
        boolean currentlyInMainFunctionBlock = false;
        int mainFunctionBlockBraceDepth = 0;

        String[] lineasCodigo = codigoLimpioFormateado.split("\n");
        boolean erroresEncontradosEnGeneral = false;
        // La impresión del encabezado se puede mover a J2GAnalizadorApp si se prefiere
        // System.out.println("--- Errores de Estructura Encontrados ---"); 

        for (int i = 0; i < lineasCodigo.length; i++) {
            String lineaActual = lineasCodigo[i].trim(); 
            String originalLineForBraceCheck = lineasCodigo[i]; 
            if (lineaActual.isEmpty()) continue;

            boolean reglaCoincidioEstaLinea = false;
            List<String> errorsEnLinea = new ArrayList<>();
            Matcher matcher;

            for (char ch : originalLineForBraceCheck.toCharArray()) {
                if (ch == '{') globalBraceBalance++;
                else if (ch == '}') globalBraceBalance--;
            }
            if (globalBraceBalance < 0) {
                if (errorsEnLinea.stream().noneMatch(err -> err.contains("Llave de cierre '}' inesperada"))) {
                    errorsEnLinea.add("Error de sintaxis: Llave de cierre '}' inesperada o sin pareja de apertura (desbalance global).");
                }
                globalBraceBalance = 0; 
            }

            if (lineaActual.matches(reglas.getOrDefault("MAIN_FUNC_START", "^$"))) {
                if (mainFunctionDeclared) { errorsEnLinea.add("Error ESTRUCTURAL: Múltiples definiciones de 'FUNC J2G Main()'."); }
                if (currentlyInMainFunctionBlock) {errorsEnLinea.add("Error ESTRUCTURAL: Definición de 'FUNC J2G Main()' anidada no permitida.");}
                mainFunctionDeclared = true;
                currentlyInMainFunctionBlock = true;
                mainFunctionBlockBraceDepth = 1; 
                reglaCoincidioEstaLinea = true;
            } else if (currentlyInMainFunctionBlock) {
                if (lineaActual.endsWith("{")) {
                     if (!lineaActual.matches(reglas.getOrDefault("MAIN_FUNC_START", "^$"))) {
                        boolean isBlockOpeningStmt = false;
                        String[] blockOpeners = {"IF_STMT", "WHILE_STMT", "FOR_STMT", "DO_WHILE_DO_STMT", "ELSE_STMT", "SWITCH_STMT", "BLOCK_END_ELSE_STMT"};
                        for (String openerKey : blockOpeners) {
                            if (lineaActual.matches(reglas.getOrDefault(openerKey, "^$"))) {
                                isBlockOpeningStmt = true; break;
                            }
                        }
                        if (isBlockOpeningStmt) mainFunctionBlockBraceDepth++;
                    }
                }
                
                matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_NO_INIT", "^$")).matcher(lineaActual);
                if (matcher.matches()) {
                    reglaCoincidioEstaLinea = true;
                    String varDeclaredType = matcher.group(1); String varName = matcher.group(2);
                    if (!isValidSourceVarName(varName, reglas) && !isGeneratedIdName(varName, reglas)) errorsEnLinea.add("Nombre de variable inválido '" + varName + "'.");
                    else if (declaredVariablesTypeMap.containsKey(varName)) errorsEnLinea.add("Variable '" + varName + "' ya declarada.");
                    else declaredVariablesTypeMap.put(varName, varDeclaredType); 
                }

                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_CON_INIT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        String varDeclaredType = matcher.group(1); String varName = matcher.group(2);
                        if (!isValidSourceVarName(varName, reglas) && !isGeneratedIdName(varName, reglas)) errorsEnLinea.add("Nombre de variable inválido '" + varName + "'.");
                        else if (declaredVariablesTypeMap.containsKey(varName)) errorsEnLinea.add("Variable '" + varName + "' ya declarada.");
                        else declaredVariablesTypeMap.put(varName, varDeclaredType);
                        String assignedValueType = "UNKNOWN"; String sLit = matcher.group(4), nLit = matcher.group(5), bLit = matcher.group(6), rhsV = matcher.group(7);
                        if (sLit != null) assignedValueType = "STR"; else if (nLit != null) assignedValueType = "INT"; else if (bLit != null) assignedValueType = "BOOL";
                        else if (rhsV != null && !rhsV.isEmpty()) { errorsEnLinea.addAll(checkVariableUsage(rhsV, "en RHS de inicialización", reglas, declaredVariablesTypeMap)); assignedValueType = declaredVariablesTypeMap.getOrDefault(rhsV, "UNKNOWN_VAR_TYPE");}
                        if (!assignedValueType.startsWith("UNKNOWN") && declaredVariablesTypeMap.containsKey(varName) && !varDeclaredType.equals(assignedValueType)) errorsEnLinea.add("Error de tipo: Var '" + varName + "' ("+varDeclaredType+") no puede inicializarse con tipo " + assignedValueType + ".");
                    }
                }
                
                String[] inputTypes = {"STR", "INT", "BOOL"}; String[] inputMethods = {"Str", "Int", "Bool"};
                for (int k = 0; k < inputTypes.length && !reglaCoincidioEstaLinea; k++) {
                    matcher = Pattern.compile(reglas.getOrDefault("INPUT_" + inputTypes[k] + "_STMT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true; String lhsVar = matcher.group(1);
                        if (lhsVar != null && !lhsVar.isEmpty()) {
                            List<String> lhsErrors = checkVariableUsage(lhsVar, "en LHS de Input()." + inputMethods[k] + "()", reglas, declaredVariablesTypeMap); errorsEnLinea.addAll(lhsErrors);
                            if (lhsErrors.isEmpty() && declaredVariablesTypeMap.containsKey(lhsVar) && !declaredVariablesTypeMap.get(lhsVar).equals(inputTypes[k])) errorsEnLinea.add("Error de tipo: Var '" + lhsVar + "' (" + declaredVariablesTypeMap.get(lhsVar) + ") no puede asignarse con Input()." + inputMethods[k] + "() que devuelve " + inputTypes[k] + ".");
                        }
                        break; 
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("ASSIGNMENT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true; String lhsVar = matcher.group(1); String op = matcher.group(2); String rhsEx = matcher.group(3);
                        List<String> lhsErrors = checkVariableUsage(lhsVar, "en LHS de asignación", reglas, declaredVariablesTypeMap); errorsEnLinea.addAll(lhsErrors);
                        errorsEnLinea.addAll(checkExpressionVariables(rhsEx, "en RHS de asignación", reglas, declaredVariablesTypeMap));
                        if (op.equals(":=") && lhsErrors.isEmpty() && declaredVariablesTypeMap.containsKey(lhsVar)) {
                            String lhsT = declaredVariablesTypeMap.get(lhsVar), rhsT = "UNKNOWN_EXPR_TYPE";
                            Matcher rhsSM = Pattern.compile("^\\s*(" + reglas.getOrDefault("ANY_LITERAL_OR_VAR","") + ")\\s*$").matcher(rhsEx.trim());
                            if(rhsSM.matches()){ String rClean = rhsSM.group(1); if (rClean.matches(reglas.getOrDefault("STRING_LITERAL", "^$"))) rhsT = "STR"; else if (rClean.matches(reglas.getOrDefault("NUMBER_LITERAL", "^$"))) rhsT = "INT"; else if (rClean.matches(reglas.getOrDefault("BOOLEAN_LITERAL", "^$"))) rhsT = "BOOL"; else if (declaredVariablesTypeMap.containsKey(rClean)) rhsT = declaredVariablesTypeMap.get(rClean); else if (isValidSourceVarName(rClean, reglas) || isGeneratedIdName(rClean, reglas)) rhsT = "UNKNOWN_VAR_TYPE"; }
                            if (!rhsT.startsWith("UNKNOWN") && !lhsT.equals(rhsT)) errorsEnLinea.add("Error de tipo: Var '" + lhsVar + "' (" + lhsT + ") no puede asignarse con tipo " + rhsT + ".");
                        } else if (op.matches("\\+=|-=|\\*=|/=") && lhsErrors.isEmpty() && declaredVariablesTypeMap.containsKey(lhsVar) && !declaredVariablesTypeMap.get(lhsVar).equals("INT")) errorsEnLinea.add("Error de tipo: Op '" + op + "' solo con INT. '" + lhsVar + "' es " + declaredVariablesTypeMap.get(lhsVar) + ".");
                    }
                }
                
                if(!reglaCoincidioEstaLinea) {
                    String[] cStructs = {"IF_STMT", "WHILE_STMT", "PRINT_STMT", "SWITCH_STMT", "FOR_STMT", "DO_WHILE_WHILE_STMT", "ELSE_STMT", "BLOCK_END_ELSE_STMT", "CASE_STMT", "DEFAULT_STMT", "DETENER_STMT", "DO_WHILE_DO_STMT"};
                    String[] cMsgs = {"cond if", "cond while", "args Print", "expr switch", "exprs for", "cond do-while", "else", "} else {", "caso", "defecto", "detener", "do"};
                    for(int k=0; k < cStructs.length && !reglaCoincidioEstaLinea; k++){
                        matcher = Pattern.compile(reglas.getOrDefault(cStructs[k], "^$")).matcher(lineaActual);
                        if (matcher.matches()) { reglaCoincidioEstaLinea = true; 
                            if ( (cStructs[k].equals("IF_STMT") || cStructs[k].equals("WHILE_STMT") || cStructs[k].equals("SWITCH_STMT") || cStructs[k].equals("DO_WHILE_WHILE_STMT")) && matcher.groupCount() >=1 ) errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), cMsgs[k], reglas, declaredVariablesTypeMap));
                            else if (cStructs[k].equals("PRINT_STMT") && matcher.groupCount() >=1) errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), cMsgs[k], reglas, declaredVariablesTypeMap));
                            else if (cStructs[k].equals("FOR_STMT") && matcher.groupCount() >=3) { errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), cMsgs[k]+" init", reglas, declaredVariablesTypeMap)); errorsEnLinea.addAll(checkExpressionVariables(matcher.group(2), cMsgs[k]+" cond", reglas, declaredVariablesTypeMap)); errorsEnLinea.addAll(checkExpressionVariables(matcher.group(3), cMsgs[k]+" incr", reglas, declaredVariablesTypeMap));}
                            else if (cStructs[k].equals("CASE_STMT") && matcher.groupCount() >=1 && !matcher.group(1).startsWith("\"") && !(matcher.group(1).matches(reglas.getOrDefault("ID_NAME", "^$")))) errorsEnLinea.addAll(checkVariableUsage(matcher.group(1), cMsgs[k], reglas, declaredVariablesTypeMap));
                            break; 
                        }
                    }
                }

                if (lineaActual.matches(reglas.getOrDefault("BLOCK_END", "^$"))) {
                    mainFunctionBlockBraceDepth--; 
                    if (mainFunctionBlockBraceDepth == 0) { 
                        currentlyInMainFunctionBlock = false; 
                    } else if (mainFunctionBlockBraceDepth < 0) {
                        mainFunctionBlockBraceDepth = 0; 
                    }
                    reglaCoincidioEstaLinea = true; 
                }

            } else { 
                if (!lineaActual.isEmpty()) { 
                    if (!mainFunctionDeclared) {
                        errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual + "' encontrado antes de la definición de 'FUNC J2G Main()'.");
                    } else { 
                        errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual + "' encontrado después del cierre del bloque 'FUNC J2G Main()'.");
                    }
                }
            }

            if (!reglaCoincidioEstaLinea && errorsEnLinea.isEmpty() && !lineaActual.isEmpty() && mainFunctionDeclared && currentlyInMainFunctionBlock) {
                 errorsEnLinea.add("Error de estructura general en la línea dentro de Main (no coincide con ninguna regla conocida): " + lineaActual);
            }

            if (!errorsEnLinea.isEmpty()) {
                System.out.println("Error(es) en línea " + (i + 1) + ": " + lineasCodigo[i]); 
                for (String error : errorsEnLinea) System.out.println("  - " + error);
                erroresEncontradosEnGeneral = true;
            }
        } 

        if (!mainFunctionDeclared) {
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: No se encontró la función principal 'FUNC J2G Main() {}'.");
        } else if (mainFunctionBlockBraceDepth != 0) { 
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: El bloque 'FUNC J2G Main()' no se cerró correctamente. Profundidad de llaves esperada para Main: 0, actual: " + mainFunctionBlockBraceDepth + ".");
        }

        if (globalBraceBalance != 0) { 
            if (mainFunctionDeclared && mainFunctionBlockBraceDepth == 0) { 
                 globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Desbalance de llaves en el programa. Balance final global: " + globalBraceBalance + " (debería ser 0).");
            } else if (!mainFunctionDeclared) { 
                 globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Desbalance de llaves en el programa. Balance final global: " + globalBraceBalance + " (debería ser 0).");
            }
        }

        if (!globalStructuralErrors.isEmpty()) {
            for (String error : globalStructuralErrors) System.out.println(error);
            erroresEncontradosEnGeneral = true;
        }

        if (!erroresEncontradosEnGeneral && globalStructuralErrors.isEmpty()) {
            System.out.println("No se encontraron errores de estructura.");
        }
        System.out.println("---------------------------------------");
    }
}