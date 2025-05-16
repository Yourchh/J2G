package AnalizadorLexicoJ2G;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidadorEstructural {

    private TablaSimbolos tablaSimbolosGlobal;

    private boolean currentlyInSwitchBlock = false;
    private int switchBlockEntryDepth = 0;
    // ... (otras variables de estado del switch como antes)

    private static final String CURRENT_TIMESTAMP_VALIDATOR_TEST = "2025-05-16_0815_V106"; 
    private static final String CURRENT_USER_VALIDATOR_TEST = "Yourchh_TEST_V106";

    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal) {
        this.tablaSimbolosGlobal = tablaSimbolosGlobal;
    }

    private Map<String, String> cargarReglasRegexEnMemoria() {
        Map<String, String> reglas = new HashMap<>();
        String varOrIdPatternCore = "([a-z][a-zA-Z0-9_]{0,63}|id[0-9]+)";
        // Regex para prompt opcional: ( "cualquier cosa entre comillas" ) o nada
        String optionalPromptRegexPart = "(?:\\s*\\(\\s*(\"(?:\\\\\"|[^\"\\\\])*\")\\s*\\))?"; // Grupo 1 es el prompt con comillas
        String anyLiteralOrVarRegex = "(" + varOrIdPatternCore + "|" + "\"(?:\\\\.|[^\"\\\\])*\"" + "|" + "\\d+" + "|" + "(TRUE|FALSE|true|false)" + ")";


        reglas.put("VAR_OR_ID_CORE", varOrIdPatternCore);
        reglas.put("VAR_NAME", "^[a-z][a-zA-Z0-9_]{0,63}$");
        reglas.put("ID_NAME", "^id[0-9]+$");
        reglas.put("STRING_LITERAL", "\"(?:\\\\.|[^\"\\\\])*\"");
        reglas.put("NUMBER_LITERAL", "\\d+");
        reglas.put("BOOLEAN_LITERAL", "(TRUE|FALSE|true|false)");
        reglas.put("MAIN_FUNC_START", "^FUNC\\s+J2G\\s+Main\\s*\\(\\s*\\)\\s*\\{");
        reglas.put("BLOCK_END", "^\\}$"); 
        reglas.put("VAR_DECL_NO_INIT", "^(INT|STR|BOOL)\\s+" + varOrIdPatternCore + "\\s*;");
        reglas.put("VAR_DECL_CON_INIT", "^(INT|STR|BOOL)\\s+" + varOrIdPatternCore + "\\s*:=\\s*" + anyLiteralOrVarRegex + "\\s*;");
        reglas.put("ASSIGNMENT", "^" + varOrIdPatternCore + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*(.+)\\s*;");
        
        // INPUT statements con prompt opcional. Grupo 1: var asignada (opc), Grupo 2: prompt (opc, sin comillas)
        reglas.put("INPUT_STR_STMT", "^(?:(" + varOrIdPatternCore + ")\\s*:=\\s*)?Input" + optionalPromptRegexPart + "\\.Str\\(\\)\\s*;");
        reglas.put("INPUT_INT_STMT", "^(?:(" + varOrIdPatternCore + ")\\s*:=\\s*)?Input" + optionalPromptRegexPart + "\\.Int\\(\\)\\s*;");
        reglas.put("INPUT_BOOL_STMT", "^(?:(" + varOrIdPatternCore + ")\\s*:=\\s*)?Input" + optionalPromptRegexPart + "\\.Bool\\(\\)\\s*;");

        reglas.put("IF_STMT", "^if\\s*\\((.+)\\)\\s*\\{");
        reglas.put("ELSE_STMT", "^else\\s*\\{");
        reglas.put("BLOCK_END_ELSE_STMT", "^\\}\\s*else\\s*\\{"); 
        reglas.put("SWITCH_STMT", "^sw\\s*\\((.+)\\)\\s*\\{");
        reglas.put("CASE_STMT", "^caso\\s+" + anyLiteralOrVarRegex + "\\s*:");
        reglas.put("DEFAULT_STMT", "^por_defecto\\s*:");
        reglas.put("DETENER_STMT", "^detener\\s*;");
        reglas.put("FOR_STMT",
                "^\\s*for\\s*" + "\\(" + "\\s*(INT\\s+([a-z][a-zA-Z0-9_]{0,63})\\s*:=\\s*(\\d+)|([a-z][a-zA-Z0-9_]{0,63})\\s*:=\\s*(\\d+))" + 
                "\\s*:\\s*" + "([^:]+)" + "\\s*:\\s*" + "([^)]+)" + "\\s*\\)" + "\\s*\\{\\s*$");
        reglas.put("WHILE_STMT", "^while\\s*\\((.+)\\)\\s*\\{"); 
        reglas.put("DO_WHILE_DO_STMT", "^do\\s*\\{"); 
        reglas.put("DO_WHILE_CLOSURE_LINE_STMT", "^\\}\\s*while\\s*\\((.+)\\)\\s*;"); 
        reglas.put("DO_WHILE_TAIL_STMT", "^while\\s*\\((.+)\\)\\s*;"); 
        reglas.put("PRINT_STMT", "^Print\\s*\\((.*?)\\)\\s*;");
        return reglas;
    }

    private char getMatchingClosingSymbol(char openSymbol) { /* ... */ return 0; } // Simplificado
    private List<String> checkBalancedSymbols(String text, int lineNumber, String contextDesc) { /* ... */ return new ArrayList<>(); } // Simplificado
    private boolean isValidSourceVarName(String name, Map<String, String> reglas) { /* ... */ return true; } // Simplificado
    private List<String> checkVariableUsage(String varName, String context, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap) { /* ... (como en V104) ... */
        List<String> errors = new ArrayList<>(); 
        if (varName == null || varName.trim().isEmpty()) return errors;
        boolean isKeyword = tablaSimbolosGlobal.getPalabrasReservadasYSimbolosConocidos().contains(varName) && !varName.equalsIgnoreCase("TRUE") && !varName.equalsIgnoreCase("FALSE");
        if (isKeyword) { if (!((context.contains("Input") && (varName.equals("Int") || varName.equals("Str") || varName.equals("Bool"))) || (context.contains("tipo_dato") && (varName.equals("INT") || varName.equals("STR") || varName.equals("BOOL"))))) { errors.add("Error semántico: Palabra reservada '" + varName + "' usada como variable " + context); return errors; } }
        if (!isValidSourceVarName(varName, reglas) && !varName.matches(reglas.get("ID_NAME"))) { if (!varName.equalsIgnoreCase("TRUE") && !varName.equalsIgnoreCase("FALSE") && !varName.matches(reglas.get("NUMBER_LITERAL")) && !varName.matches(reglas.get("STRING_LITERAL"))) { errors.add("Nombre de variable inválido '" + varName + "' " + context); }
        } else { if (isValidSourceVarName(varName, reglas) && !declaredVariablesTypeMap.containsKey(varName)) { errors.add("Variable '" + varName + "' no declarada " + context); } }
        return errors;
    }
    private List<String> checkExpressionVariables(String expression, String contextForExpression, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap, int lineNumber) { /* ... (como en V104) ... */
        List<String> errors = new ArrayList<>(); 
        if (expression == null || expression.trim().isEmpty()) return errors; 
        errors.addAll(checkBalancedSymbols(expression, lineNumber, "en la expresión '" + expression + "' " + contextForExpression));
        List<String> localTabsimTokens = new ArrayList<>(tablaSimbolosGlobal.getPalabrasReservadasYSimbolosConocidos()); 
        localTabsimTokens.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        StringBuilder regexPatternBuilder = new StringBuilder(); 
        regexPatternBuilder.append("(" + reglas.get("STRING_LITERAL") + ")"); 
        int currentGroup = 2; Map<Integer, String> knownTokenGroups = new HashMap<>();
        for (String token : localTabsimTokens) { regexPatternBuilder.append("|(").append(Pattern.quote(token)).append(")"); knownTokenGroups.put(currentGroup++, token); }
        int varOrIdGroup = currentGroup++; regexPatternBuilder.append("|(" + reglas.get("VAR_OR_ID_CORE") + ")"); 
        int numberLiteralGroup = currentGroup++; regexPatternBuilder.append("|(" + reglas.get("NUMBER_LITERAL") + ")");
        Matcher tokenMatcher = Pattern.compile(regexPatternBuilder.toString()).matcher(expression);
        while (tokenMatcher.find()) { 
            if (tokenMatcher.group(1) != null) continue; 
            if (tokenMatcher.group(numberLiteralGroup) != null) continue; 
            boolean matchedKnownToken = false;
            for(int grpIdx : knownTokenGroups.keySet()){ if(tokenMatcher.group(grpIdx) != null){ matchedKnownToken = true; break; } }
            if(matchedKnownToken) continue;
            if (tokenMatcher.group(varOrIdGroup) != null) { String varName = tokenMatcher.group(varOrIdGroup); 
                if (varName.equalsIgnoreCase("TRUE") || varName.equalsIgnoreCase("FALSE")) continue; 
                if (!varName.equals("Input") && !varName.equals("Str") && !varName.equals("Int") && !varName.equals("Bool")) { 
                    errors.addAll(checkVariableUsage(varName, contextForExpression + " (en expr: " + expression+")", reglas, declaredVariablesTypeMap)); 
                } 
            }
        } 
        return errors;
    }

    // MODIFICACIÓN V106 en getExpressionType
    private String getExpressionType(String expression, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap) {
        if (expression == null) return "UNKNOWN";
        String trimmedExpr = expression.trim();

        if (trimmedExpr.matches(reglas.get("STRING_LITERAL"))) return "STR"; 
        if (trimmedExpr.matches(reglas.get("NUMBER_LITERAL"))) return "INT"; 
        if (trimmedExpr.matches(reglas.get("BOOLEAN_LITERAL"))) return "BOOL"; 
        
        if (declaredVariablesTypeMap.containsKey(trimmedExpr)) return declaredVariablesTypeMap.get(trimmedExpr);
        
        // Inferir tipo para llamadas a Input.Type() - Usar startsWith y endsWith para simplicidad
        // Esto asume que la expresión RHS es *solo* la llamada a Input, como en "var := Input.Int()"
        // No manejará "var := Input.Int() + 1" aquí.
        if (trimmedExpr.startsWith("Input") && trimmedExpr.endsWith(".Str()")) return "STR";
        if (trimmedExpr.startsWith("Input") && trimmedExpr.endsWith(".Int()")) return "INT";
        if (trimmedExpr.startsWith("Input") && trimmedExpr.endsWith(".Bool()")) return "BOOL";
        
        return "UNKNOWN"; 
    }

    // performAssignmentTypeChecks (como en V105, asegurando que la condición estricta esté activa)
    private void performAssignmentTypeChecks(String lhsVar, String op, String rhsExpression, String context, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap, List<String> errorsEnLinea) {
        if (!declaredVariablesTypeMap.containsKey(lhsVar)) return;
        String lhsType = declaredVariablesTypeMap.get(lhsVar); 
        String rhsType = getExpressionType(rhsExpression, reglas, declaredVariablesTypeMap);
        
        // System.out.println("[PTC_V106] LHS: " + lhsVar + " ("+lhsType+"), Op: " + op + ", RHS: " + rhsExpression + " (Inferido: "+rhsType+")");

        if (op.equals(":=")) { 
            if (!rhsType.equals("UNKNOWN") && !lhsType.equals(rhsType)) { 
                errorsEnLinea.add("Error Tipo: No se puede asignar tipo " + rhsType + " a var '" + lhsVar + "' (tipo " + lhsType + ") " + context); 
            }
        } else if (op.matches("\\+=|-=|\\*=|/=")) { 
            if (!lhsType.equals("INT")) errorsEnLinea.add("Error Tipo: Op. asignación compuesta '" + op + "' requiere var INT, pero '" + lhsVar + "' es " + lhsType + " " + context); 
            if (!rhsType.equals("INT") && !rhsType.equals("UNKNOWN") && !rhsType.matches(reglas.get("VAR_OR_ID_CORE"))) { // Permitir var += var_int
                 errorsEnLinea.add("Error Tipo: Op. asignación compuesta '" + op + "' requiere valor INT a la derecha, pero es " + rhsType + " " + context); 
            } else if (rhsType.equals("UNKNOWN") && !rhsExpression.matches(reglas.get("NUMBER_LITERAL")) && !declaredVariablesTypeMap.getOrDefault(rhsExpression, "").equals("INT")) {
                // Si es UNKNOWN pero no es un literal numérico ni una variable INT conocida
                errorsEnLinea.add("Error Tipo: Op. asignación compuesta '" + op + "' requiere valor INT a la derecha, pero tipo de '" + rhsExpression + "' es desconocido o no es INT " + context);
            }
        }
    }

    public void validarEstructuraConRegex(String codigoLimpioFormateado) {
        System.out.println("***************************************************************************");
        System.out.println(">>> MÉTODO 'validarEstructuraConRegex' INVOCADO (VERSIÓN DE PRUEBA V106) <<<");
        System.out.println(">>> TIMESTAMP DE ESTA EJECUCIÓN: " + CURRENT_TIMESTAMP_VALIDATOR_TEST + " <<<");
        System.out.println("***************************************************************************");

        Map<String, String> reglas = cargarReglasRegexEnMemoria();
        Map<String, String> declaredVariablesTypeMap = new HashMap<>();
        int globalBraceBalance = 0; 
        List<String> globalStructuralErrors = new ArrayList<>();
        boolean mainFunctionDeclared = false;
        boolean currentlyInMainFunctionBlock = false;
        int mainFunctionBlockBraceDepth = 0; 

        this.currentlyInSwitchBlock = false; 
        this.switchBlockEntryDepth = 0;

        String[] lineasCodigo = codigoLimpioFormateado.split("\n");
        boolean erroresEncontradosEnGeneral = false;

        for (int i = 0; i < lineasCodigo.length; i++) {
            String lineaActual = lineasCodigo[i].trim();
            String originalLineForBraceCheck = lineasCodigo[i];
            int currentLineNumber = i + 1;
            
            if (lineaActual.isEmpty()) continue;

            boolean reglaCoincidioEstaLinea = false; 
            List<String> errorsEnLinea = new ArrayList<>();
            Matcher matcher;

            for (char ch : originalLineForBraceCheck.toCharArray()) { if (ch == '{') globalBraceBalance++; else if (ch == '}') globalBraceBalance--; }

            if (!mainFunctionDeclared && !lineaActual.matches(reglas.getOrDefault("MAIN_FUNC_START", "^$"))) {
                if (!lineaActual.trim().isEmpty()) errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual + "' encontrado antes de 'FUNC J2G Main()'.");
                reglaCoincidioEstaLinea = true; 
            } else if (lineaActual.matches(reglas.getOrDefault("MAIN_FUNC_START", "^$"))) {
                if (mainFunctionDeclared) errorsEnLinea.add("Error ESTRUCTURAL: Múltiples 'FUNC J2G Main()'.");
                mainFunctionDeclared = true; currentlyInMainFunctionBlock = true; mainFunctionBlockBraceDepth = 1; reglaCoincidioEstaLinea = true;
            } else if (currentlyInMainFunctionBlock) {
                
                if (!reglaCoincidioEstaLinea && lineaActual.matches(reglas.getOrDefault("BLOCK_END_ELSE_STMT", "^$"))) { 
                    reglaCoincidioEstaLinea = true; mainFunctionBlockBraceDepth--; mainFunctionBlockBraceDepth++; 
                }
                if (!reglaCoincidioEstaLinea && lineaActual.matches(reglas.getOrDefault("DO_WHILE_CLOSURE_LINE_STMT", "^$"))) { 
                     reglaCoincidioEstaLinea = true; mainFunctionBlockBraceDepth--; 
                     matcher = Pattern.compile(reglas.get("DO_WHILE_CLOSURE_LINE_STMT")).matcher(lineaActual); 
                     if(matcher.matches()) errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), "en condición de }while", reglas, declaredVariablesTypeMap, currentLineNumber));
                }

                String[] blockOpeningKeys = {"IF_STMT", "WHILE_STMT", "SWITCH_STMT", "DO_WHILE_DO_STMT", "ELSE_STMT", "FOR_STMT"};
                if (!reglaCoincidioEstaLinea) {
                    for (String key : blockOpeningKeys) {
                        matcher = Pattern.compile(reglas.getOrDefault(key, "^$")).matcher(lineaActual);
                        if (matcher.matches()) { 
                            reglaCoincidioEstaLinea = true; mainFunctionBlockBraceDepth++;
                            if (key.equals("FOR_STMT")) { /* ... (como en V104) ... */
                                String varNameInFor = null; String initValFor = null;
                                if (matcher.group(2) != null) { varNameInFor = matcher.group(2).trim(); initValFor = matcher.group(3).trim();
                                    if (!declaredVariablesTypeMap.containsKey(varNameInFor)) { declaredVariablesTypeMap.put(varNameInFor, "INT");} else { errorsEnLinea.add("Var '" + varNameInFor + "' ya declarada."); }
                                } else if (matcher.group(4) != null) { varNameInFor = matcher.group(4).trim(); initValFor = matcher.group(5).trim(); if (!declaredVariablesTypeMap.containsKey(varNameInFor)) { errorsEnLinea.add("Var '" + varNameInFor + "' no declarada."); } else { if(!declaredVariablesTypeMap.get(varNameInFor).equals("INT")) errorsEnLinea.add("Var '"+varNameInFor+"' en for debe ser INT.");} }
                                if (varNameInFor != null) errorsEnLinea.addAll(checkVariableUsage(varNameInFor, "en init for (LHS)", reglas, declaredVariablesTypeMap));
                                if (initValFor != null) errorsEnLinea.addAll(checkExpressionVariables(initValFor, "en init for (RHS)", reglas, declaredVariablesTypeMap, currentLineNumber));
                                if (varNameInFor != null && initValFor != null) performAssignmentTypeChecks(varNameInFor, ":=", initValFor, "en init for", reglas, declaredVariablesTypeMap, errorsEnLinea);
                                errorsEnLinea.addAll(checkExpressionVariables(matcher.group(6).trim(), "en cond for", reglas, declaredVariablesTypeMap, currentLineNumber));
                                String updatePart = matcher.group(7).trim(); Matcher updateAssignMatcher = Pattern.compile("^" + reglas.get("VAR_OR_ID_CORE") + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*(.+)$").matcher(updatePart);
                                if (updateAssignMatcher.matches()) { String lhsUpd = updateAssignMatcher.group(1); String opUpd = updateAssignMatcher.group(2); String rhsUpd = updateAssignMatcher.group(3).trim(); errorsEnLinea.addAll(checkVariableUsage(lhsUpd, "en update for (LHS)", reglas, declaredVariablesTypeMap)); errorsEnLinea.addAll(checkExpressionVariables(rhsUpd, "en update for (RHS)", reglas, declaredVariablesTypeMap, currentLineNumber)); performAssignmentTypeChecks(lhsUpd, opUpd, rhsUpd, "en update for", reglas, declaredVariablesTypeMap, errorsEnLinea);
                                } else if (!updatePart.isEmpty()) { errorsEnLinea.add("Error sintaxis: Update del for ('" + updatePart + "') no válida."); }
                            } else if (key.equals("IF_STMT") || key.equals("WHILE_STMT") || key.equals("SWITCH_STMT")) {
                                errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), "en condición de " + key, reglas, declaredVariablesTypeMap, currentLineNumber));
                            }
                            if (key.equals("SWITCH_STMT")) { this.currentlyInSwitchBlock = true; this.switchBlockEntryDepth = mainFunctionBlockBraceDepth; }
                            break; 
                        }
                    }
                }
                
                if (!reglaCoincidioEstaLinea) { 
                    matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_NO_INIT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) { reglaCoincidioEstaLinea = true; String type = matcher.group(1); String name = matcher.group(2).trim(); if (declaredVariablesTypeMap.containsKey(name)) errorsEnLinea.add("Var '" + name + "' ya declarada."); else { declaredVariablesTypeMap.put(name, type); } errorsEnLinea.addAll(checkVariableUsage(name, "en declaración", reglas, declaredVariablesTypeMap)); }
                }
                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_CON_INIT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) { reglaCoincidioEstaLinea = true; String type = matcher.group(1); String name = matcher.group(2).trim(); String val = matcher.group(3).trim(); if (declaredVariablesTypeMap.containsKey(name)) errorsEnLinea.add("Var '" + name + "' ya declarada."); else { declaredVariablesTypeMap.put(name, type); } errorsEnLinea.addAll(checkVariableUsage(name, "en declaración", reglas, declaredVariablesTypeMap)); errorsEnLinea.addAll(checkExpressionVariables(val, "en RHS de declaración", reglas, declaredVariablesTypeMap, currentLineNumber)); performAssignmentTypeChecks(name, ":=", val, "en declaración", reglas, declaredVariablesTypeMap, errorsEnLinea); }
                }
                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("ASSIGNMENT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) { reglaCoincidioEstaLinea = true; String name = matcher.group(1).trim(); String op = matcher.group(2); String val = matcher.group(3).trim(); errorsEnLinea.addAll(checkVariableUsage(name, "en LHS de asignación", reglas, declaredVariablesTypeMap)); errorsEnLinea.addAll(checkExpressionVariables(val, "en RHS de asignación", reglas, declaredVariablesTypeMap, currentLineNumber)); performAssignmentTypeChecks(name, op, val, "en asignación", reglas, declaredVariablesTypeMap, errorsEnLinea); }
                }
                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("PRINT_STMT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) { reglaCoincidioEstaLinea = true; errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1).trim(), "arg Print", reglas, declaredVariablesTypeMap, currentLineNumber)); }
                }
                // INPUT statements (actualizado para V106 para pasar la expresión completa a getExpressionType)
                String[] inputTypes = {"STR", "INT", "BOOL"}; 
                for (String type : inputTypes) { 
                    if (!reglaCoincidioEstaLinea) { 
                        matcher = Pattern.compile(reglas.getOrDefault("INPUT_" + type + "_STMT", "^$")).matcher(lineaActual); 
                        if (matcher.matches()) { 
                            reglaCoincidioEstaLinea = true; 
                            String targetVar = matcher.group(1); // Variable asignada (puede ser null)
                            String promptInRegex = matcher.group(2); // Prompt con comillas (puede ser null)
                            
                            // Construir la representación de la llamada a Input como sería en el código
                            String rhsExpression = "Input" + (promptInRegex != null ? ("(" + promptInRegex + ")") : "()") + "." + type + "()";

                            if(targetVar != null) { 
                                targetVar = targetVar.trim(); 
                                errorsEnLinea.addAll(checkVariableUsage(targetVar, "en LHS de Input", reglas, declaredVariablesTypeMap)); 
                                performAssignmentTypeChecks(targetVar, ":=", rhsExpression, "en Input", reglas, declaredVariablesTypeMap, errorsEnLinea); 
                            } else { 
                                if(promptInRegex != null) errorsEnLinea.addAll(checkExpressionVariables(promptInRegex, "en prompt de Input", reglas, declaredVariablesTypeMap, currentLineNumber));
                            } 
                            break; 
                        } 
                    } 
                }
                
                if (this.currentlyInSwitchBlock) { /* ... (como en V104) ... */
                    if (!reglaCoincidioEstaLinea && lineaActual.matches(reglas.getOrDefault("CASE_STMT", "^$"))) { reglaCoincidioEstaLinea = true; /* ... */ }
                    if (!reglaCoincidioEstaLinea && lineaActual.matches(reglas.getOrDefault("DEFAULT_STMT", "^$"))) { reglaCoincidioEstaLinea = true; /* ... */ }
                    if (!reglaCoincidioEstaLinea && lineaActual.matches(reglas.getOrDefault("DETENER_STMT", "^$"))) { reglaCoincidioEstaLinea = true; /* ... */ }
                } else { if (!reglaCoincidioEstaLinea && (lineaActual.matches(reglas.getOrDefault("CASE_STMT", "^$")) || lineaActual.matches(reglas.getOrDefault("DEFAULT_STMT", "^$")) || lineaActual.matches(reglas.getOrDefault("DETENER_STMT", "^$")))) { errorsEnLinea.add("Error estructural: '" + lineaActual + "' fuera de 'sw'."); reglaCoincidioEstaLinea = true; } }

                if (!reglaCoincidioEstaLinea && lineaActual.matches(reglas.getOrDefault("DO_WHILE_TAIL_STMT", "^$"))) {
                    reglaCoincidioEstaLinea = true;
                    matcher = Pattern.compile(reglas.get("DO_WHILE_TAIL_STMT")).matcher(lineaActual);
                    if(matcher.matches()) errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), "en condición de do-while", reglas, declaredVariablesTypeMap, currentLineNumber));
                }

                if (!reglaCoincidioEstaLinea && lineaActual.matches(reglas.getOrDefault("BLOCK_END", "^$"))) { 
                    reglaCoincidioEstaLinea = true; mainFunctionBlockBraceDepth--;
                    if (this.currentlyInSwitchBlock && mainFunctionBlockBraceDepth == this.switchBlockEntryDepth - 1) { this.currentlyInSwitchBlock = false; }
                    if (mainFunctionBlockBraceDepth == 0 && currentlyInMainFunctionBlock) { currentlyInMainFunctionBlock = false; } 
                    else if (mainFunctionBlockBraceDepth < 0 && mainFunctionDeclared) { errorsEnLinea.add("Error ESTRUCTURAL: Llave '}' extra."); mainFunctionBlockBraceDepth = 0; currentlyInMainFunctionBlock = false; }
                }
            } else { 
                 if (mainFunctionDeclared && !currentlyInMainFunctionBlock && !lineaActual.isEmpty()){
                    errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual + "' después de cerrar 'FUNC J2G Main()'.");
                    reglaCoincidioEstaLinea = true;
                 }
            }

            if (!reglaCoincidioEstaLinea && currentlyInMainFunctionBlock && !lineaActual.isEmpty()) {
                errorsEnLinea.add("Error sintaxis general (no reconocida V106): '" + lineaActual + "'.");
                errorsEnLinea.addAll(checkBalancedSymbols(lineaActual, currentLineNumber, "en línea no reconocida V106"));
            }

            if (!errorsEnLinea.isEmpty()) { System.out.println("Error(es) en línea " + currentLineNumber + ": " + originalLineForBraceCheck); for (String error : errorsEnLinea) System.out.println("  - " + error); erroresEncontradosEnGeneral = true; }
        } 

        if (!mainFunctionDeclared) globalStructuralErrors.add("Error GLOBAL: No 'FUNC J2G Main() {}'.");
        else if (mainFunctionBlockBraceDepth != 0) { globalStructuralErrors.add("Error GLOBAL: Desbalance llaves Main. Profundidad final: " + mainFunctionBlockBraceDepth + (mainFunctionBlockBraceDepth > 0 ? " (faltan "+mainFunctionBlockBraceDepth+" '}')" : " (hay "+(-mainFunctionBlockBraceDepth)+" '}' extra)"));
        } else if (mainFunctionDeclared && currentlyInMainFunctionBlock && mainFunctionBlockBraceDepth == 0) { 
            globalStructuralErrors.add("Error GLOBAL: Inconsistencia, Main cerrado (depth 0) pero 'currentlyInMainFunctionBlock' es true."); 
        }

        if (!globalStructuralErrors.isEmpty()) { System.out.println("\n--- Errores Globales ---"); for (String error : globalStructuralErrors) System.out.println(error); }
        if (!erroresEncontradosEnGeneral && globalStructuralErrors.isEmpty()) { System.out.println("No se encontraron errores de estructura."); }
        System.out.println("---------------------------------------");
        System.out.println(">>> 'validarEstructuraConRegex' FINALIZADO (VERSIÓN DE PRUEBA V106) <<<");
    }
}