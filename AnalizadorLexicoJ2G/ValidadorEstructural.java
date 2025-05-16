package AnalizadorLexicoJ2G;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidadorEstructural {

    private TablaSimbolos tablaSimbolosGlobal; 

    // Variables de estado para la validación del switch
    private boolean currentlyInSwitchBlock = false;
    private int switchBlockEntryDepth = 0; 
    private boolean currentSwitchClauseNeedsDetener = false; 
    private int lineOfCurrentSwitchClause = 0; 
    private String contentOfCurrentSwitchClause = ""; 
    private int switchActualOpeningLine = 0; // Línea donde se abrió el switch actual

    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal) {
        this.tablaSimbolosGlobal = tablaSimbolosGlobal;
    }

    private Map<String, String> cargarReglasRegexEnMemoria() {
        Map<String, String> reglas = new HashMap<>();
        String varOrIdRegex = "([a-z][a-zA-Z0-9_]{0,63}|id[0-9]+)";
        String optionalPromptRegex = "(?:\\((\"(?:\\\\.|[^\"\\\\])*\")\\))?";

        reglas.put("VAR_NAME", "^[a-z][a-zA-Z0-9_]{0,63}$"); 
        reglas.put("ID_NAME", "^id[0-9]+$");                
        reglas.put("VALID_LHS_VAR", "^" + varOrIdRegex + "$"); 
        reglas.put("STRING_LITERAL", "\"(?:\\\\.|[^\"\\\\])*\"");
        reglas.put("NUMBER_LITERAL", "[0-9]+");
        reglas.put("BOOLEAN_LITERAL", "(TRUE|FALSE|true|false)"); 
        reglas.put("ANY_LITERAL_OR_VAR", "(" + varOrIdRegex + "|(\"(?:\\\\.|[^\"\\\\])*\")|([0-9]+)|(TRUE|FALSE|true|false))");
        reglas.put("MAIN_FUNC_START", "^FUNC\\s+J2G\\s+Main\\s*\\(\\s*\\)\\s*\\{"); 
        reglas.put("BLOCK_END", "^\\}$"); 
        reglas.put("VAR_DECL_NO_INIT", "^(INT|STR|BOOL)\\s+" + varOrIdRegex + "\\s*;");
        reglas.put("VAR_DECL_CON_INIT", "^(INT|STR|BOOL)\\s+" + varOrIdRegex + "\\s*:=\\s*((\"(?:\\\\.|[^\"\\\\])*\")|([0-9]+)|(TRUE|FALSE|true|false)|" + varOrIdRegex + ")\\s*;");
        reglas.put("ASSIGNMENT", "^" + varOrIdRegex + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*(.+)\\s*;");
        reglas.put("INPUT_STR_STMT", "^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Str\\(\\)\\s*;");
        reglas.put("INPUT_INT_STMT", "^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Int\\(\\)\\s*;");
        reglas.put("INPUT_BOOL_STMT", "^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Bool\\(\\)\\s*;");
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
        if (this.tablaSimbolosGlobal.getPalabrasReservadasYSimbolosConocidos().contains(varName) && 
            !varName.equals("true") && !varName.equals("false") && !varName.equals("TRUE") && !varName.equals("FALSE")) { // No validar PRs/simbolos como variables, excepto booleanos
            return errors; 
        }
        boolean isValidFormat = isValidSourceVarName(varName, reglas) || isGeneratedIdName(varName, reglas);
        if (!isValidFormat) {
            if(!varName.equals("true") && !varName.equals("false") && !varName.equals("TRUE") && !varName.equals("FALSE")) // No marcar error para literales booleanos
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
        
        StringBuilder regexPatternBuilder = new StringBuilder();
        // Orden de patrones: String literal, luego tokens conocidos (PR, operadores), luego identificadores, luego números.
        regexPatternBuilder.append("(\"(?:\\\\.|[^\"\\\\])*\")"); // Grupo para literales de cadena
        
        for (String token : localTabsimTokens) {
            regexPatternBuilder.append("|(").append(Pattern.quote(token)).append(")"); // Grupos para tokens conocidos
        }
        regexPatternBuilder.append("|([a-zA-Z_][a-zA-Z0-9_]*)"); // Grupo para identificadores (variables)
        regexPatternBuilder.append("|([0-9]+)"); // Grupo para números
        
        Pattern tokenPattern = Pattern.compile(regexPatternBuilder.toString());
        Matcher matcher = tokenPattern.matcher(expression);
        
        while (matcher.find()) {
            String matchedStringLiteral = matcher.group(1);
            String matchedIdentifier = null;
            String matchedNumber = null;
            boolean matchedKnownToken = false;

            // Encontrar el grupo correcto para identificadores y números
            // Los grupos para tokens conocidos están entre el literal de cadena y el identificador/número
            int knownTokenGroupStart = 2;
            int knownTokenGroupEnd = knownTokenGroupStart + localTabsimTokens.size() -1;
            int identifierGroupIndex = knownTokenGroupEnd + 1;
            int numberGroupIndex = identifierGroupIndex + 1;

            if (matcher.group(identifierGroupIndex) != null) {
                matchedIdentifier = matcher.group(identifierGroupIndex);
            }
            if (matcher.group(numberGroupIndex) != null) {
                matchedNumber = matcher.group(numberGroupIndex);
            }
            
            for (int k = knownTokenGroupStart; k <= knownTokenGroupEnd; k++) {
                if (matcher.group(k) != null) {
                    matchedKnownToken = true;
                    break;
                }
            }

            if (matchedStringLiteral != null || matchedNumber != null || matchedKnownToken) {
                continue; // Es un literal, número o token conocido (PR, operador), no una variable a verificar aquí
            }
            
            if (matchedIdentifier != null) {
                String varName = matchedIdentifier;
                // No validar "Input", "Str", "Int", "Bool" como variables no declaradas en este contexto
                if (!varName.equals("Input") && !varName.equals("Str") && !varName.equals("Int") && !varName.equals("Bool")) {
                    errors.addAll(checkVariableUsage(varName, context, reglas, declaredVariablesTypeMap));
                }
            }
        }
        return errors;
    }

    public void validarEstructuraConRegex(String codigoLimpioFormateado) {
        Map<String, String> reglas = cargarReglasRegexEnMemoria();
        Map<String, String> declaredVariablesTypeMap = new HashMap<>(); 
        int globalBraceBalance = 0; 
        List<String> globalStructuralErrors = new ArrayList<>(); 

        boolean mainFunctionDeclared = false;
        boolean currentlyInMainFunctionBlock = false;
        int mainFunctionBlockBraceDepth = 0; 

        // Resetear estado del switch
        this.currentlyInSwitchBlock = false;
        this.switchBlockEntryDepth = 0;
        this.currentSwitchClauseNeedsDetener = false;
        this.lineOfCurrentSwitchClause = 0;
        this.contentOfCurrentSwitchClause = "";
        this.switchActualOpeningLine = 0;

        String[] lineasCodigo = codigoLimpioFormateado.split("\n");
        boolean erroresEncontradosEnGeneral = false;

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
            if (globalBraceBalance < 0 && errorsEnLinea.stream().noneMatch(err -> err.contains("Llave de cierre '}' inesperada"))) {
                 errorsEnLinea.add("Error de sintaxis: Llave de cierre '}' inesperada o sin pareja de apertura (desbalance global).");
                 globalBraceBalance = 0; 
            }

            if (lineaActual.matches(reglas.getOrDefault("MAIN_FUNC_START", "^$"))) {
                if (mainFunctionDeclared) errorsEnLinea.add("Error ESTRUCTURAL: Múltiples definiciones de 'FUNC J2G Main()'.");
                if (currentlyInMainFunctionBlock) errorsEnLinea.add("Error ESTRUCTURAL: Definición de 'FUNC J2G Main()' anidada no permitida.");
                mainFunctionDeclared = true;
                currentlyInMainFunctionBlock = true;
                mainFunctionBlockBraceDepth = 1; 
                reglaCoincidioEstaLinea = true;
            } else if (currentlyInMainFunctionBlock) {
                boolean esAperturaDeBloqueInternoValido = false;
                // SWITCH_STMT también es una apertura de bloque interno
                String[] blockOpeningRegexKeys = {"IF_STMT", "WHILE_STMT", "FOR_STMT", "SWITCH_STMT", "DO_WHILE_DO_STMT", "ELSE_STMT", "BLOCK_END_ELSE_STMT"};
                for (String key : blockOpeningRegexKeys) {
                    if (lineaActual.matches(reglas.getOrDefault(key, "^$")) && lineaActual.endsWith("{")) {
                        esAperturaDeBloqueInternoValido = true;
                        break;
                    }
                }
                if (esAperturaDeBloqueInternoValido) {
                    mainFunctionBlockBraceDepth++;
                }

                // Lógica específica para SWITCH
                if (lineaActual.matches(reglas.getOrDefault("SWITCH_STMT", "^$"))) {
                    reglaCoincidioEstaLinea = true;
                    this.currentlyInSwitchBlock = true;
                    // mainFunctionBlockBraceDepth ya fue incrementado por ser un bloque que abre con '{'
                    this.switchBlockEntryDepth = mainFunctionBlockBraceDepth; 
                    this.currentSwitchClauseNeedsDetener = false; 
                    this.switchActualOpeningLine = i + 1; 
                    
                    Matcher switchMatcher = Pattern.compile(reglas.get("SWITCH_STMT")).matcher(lineaActual);
                    if(switchMatcher.matches()){ // Para extraer el grupo de la expresión
                        errorsEnLinea.addAll(checkExpressionVariables(switchMatcher.group(1), "en expr switch", reglas, declaredVariablesTypeMap));
                    }

                } else if (this.currentlyInSwitchBlock) {
                    if (lineaActual.matches(reglas.getOrDefault("CASE_STMT", "^$")) || lineaActual.matches(reglas.getOrDefault("DEFAULT_STMT", "^$"))) {
                        reglaCoincidioEstaLinea = true;
                        if (this.currentSwitchClauseNeedsDetener) {
                            globalStructuralErrors.add("Error en bloque anterior (" + this.contentOfCurrentSwitchClause + " en línea " + this.lineOfCurrentSwitchClause + "): Se esperaba 'detener;' antes de este nuevo bloque.");
                        }
                        this.currentSwitchClauseNeedsDetener = true; // Este nuevo caso/default necesita detener
                        this.lineOfCurrentSwitchClause = i + 1;
                        this.contentOfCurrentSwitchClause = lineaActual;
                        if(lineaActual.matches(reglas.getOrDefault("CASE_STMT", "^$"))) {
                             Matcher caseMatcher = Pattern.compile(reglas.get("CASE_STMT")).matcher(lineaActual);
                             if(caseMatcher.matches() && caseMatcher.group(1) != null && !caseMatcher.group(1).startsWith("\"") && !caseMatcher.group(1).matches(reglas.get("NUMBER_LITERAL")) && !caseMatcher.group(1).matches(reglas.get("BOOLEAN_LITERAL"))) { 
                                errorsEnLinea.addAll(checkVariableUsage(caseMatcher.group(1), "en valor de caso", reglas, declaredVariablesTypeMap));
                             }
                        }
                    } else if (lineaActual.matches(reglas.getOrDefault("DETENER_STMT", "^$"))) {
                        reglaCoincidioEstaLinea = true;
                        this.currentSwitchClauseNeedsDetener = false; // El detener se encontró
                    } else if (lineaActual.matches(reglas.getOrDefault("BLOCK_END", "^$"))) { // "}"
                        // La profundidad se decrementará después por la lógica general de "}"
                        // Si la profundidad actual (antes de decrementar por este '}') es igual a switchBlockEntryDepth
                        // Y después de decrementar será switchBlockEntryDepth - 1, entonces este '}' cierra el switch.
                        // Nota: mainFunctionBlockBraceDepth ya fue incrementado por el '{' del switch.
                        // Y será decrementado por este '}' en la lógica general más abajo.
                        // Entonces, el '}' que cierra el switch hará que mainFunctionBlockBraceDepth (después de decrementar)
                        // sea igual a switchBlockEntryDepth - 1.
                        if (mainFunctionBlockBraceDepth == this.switchBlockEntryDepth) { 
                            if (this.currentSwitchClauseNeedsDetener) {
                                globalStructuralErrors.add("Error en bloque final (" + this.contentOfCurrentSwitchClause + " en línea " + this.lineOfCurrentSwitchClause + "): Se esperaba 'detener;' antes de cerrar el bloque 'sw'.");
                            }
                            this.currentlyInSwitchBlock = false;
                            this.currentSwitchClauseNeedsDetener = false; 
                            // reglaCoincidioEstaLinea se marcará por la lógica de BLOCK_END más abajo
                        }
                    }
                }

                // --- VALIDACIONES DE SENTENCIAS GENERALES (si no se manejó por switch o MAIN_FUNC_START) ---
                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_NO_INIT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        String varDeclaredType = matcher.group(1); String varName = matcher.group(2);
                        if (!isValidSourceVarName(varName, reglas) && !isGeneratedIdName(varName, reglas)) errorsEnLinea.add("Nombre de variable inválido '" + varName + "'.");
                        else if (declaredVariablesTypeMap.containsKey(varName)) errorsEnLinea.add("Variable '" + varName + "' ya declarada.");
                        else declaredVariablesTypeMap.put(varName, varDeclaredType); 
                    }
                }
                
                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_CON_INIT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        String varDeclaredType = matcher.group(1); String varName = matcher.group(2); String rhs = matcher.group(3);
                        if (!isValidSourceVarName(varName, reglas) && !isGeneratedIdName(varName, reglas)) errorsEnLinea.add("Nombre de variable inválido '" + varName + "'.");
                        else if (declaredVariablesTypeMap.containsKey(varName)) errorsEnLinea.add("Variable '" + varName + "' ya declarada.");
                        else declaredVariablesTypeMap.put(varName, varDeclaredType);
                        
                        String assignedValueType = "UNKNOWN"; 
                        if (matcher.group(4) != null) assignedValueType = "STR"; // String literal
                        else if (matcher.group(5) != null) assignedValueType = "INT"; // Number literal
                        else if (matcher.group(6) != null) assignedValueType = "BOOL"; // Boolean literal
                        else if (matcher.group(7) != null) { // Variable
                            String rhsVarName = matcher.group(7);
                            errorsEnLinea.addAll(checkVariableUsage(rhsVarName, "en RHS de inicialización", reglas, declaredVariablesTypeMap)); 
                            assignedValueType = declaredVariablesTypeMap.getOrDefault(rhsVarName, "UNKNOWN_VAR_TYPE");
                        }
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
                            String lhsT = declaredVariablesTypeMap.get(lhsVar);
                            // Simplificación de la validación de tipo RHS para asignación, se necesitaría un analizador de expresiones más robusto.
                        } else if (op.matches("\\+=|-=|\\*=|/=") && lhsErrors.isEmpty() && declaredVariablesTypeMap.containsKey(lhsVar) && !declaredVariablesTypeMap.get(lhsVar).equals("INT")) errorsEnLinea.add("Error de tipo: Op '" + op + "' solo con INT. '" + lhsVar + "' es " + declaredVariablesTypeMap.get(lhsVar) + ".");
                    }
                }
                
                if(!reglaCoincidioEstaLinea) {
                    String[] cStructs = {"IF_STMT", "WHILE_STMT", "PRINT_STMT", "FOR_STMT", "DO_WHILE_WHILE_STMT", "ELSE_STMT", "BLOCK_END_ELSE_STMT", "DO_WHILE_DO_STMT"};
                    for(int k=0; k < cStructs.length && !reglaCoincidioEstaLinea; k++){
                        matcher = Pattern.compile(reglas.getOrDefault(cStructs[k], "^$")).matcher(lineaActual);
                        if (matcher.matches()) { 
                            reglaCoincidioEstaLinea = true; 
                            String contextMsg = "en expresión de " + cStructs[k].replace("_STMT", "").toLowerCase();
                            if ( (cStructs[k].equals("IF_STMT") || cStructs[k].equals("WHILE_STMT") || cStructs[k].equals("DO_WHILE_WHILE_STMT")) && matcher.groupCount() >=1 ) errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), contextMsg, reglas, declaredVariablesTypeMap));
                            else if (cStructs[k].equals("PRINT_STMT") && matcher.groupCount() >=1) errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), contextMsg, reglas, declaredVariablesTypeMap));
                            else if (cStructs[k].equals("FOR_STMT") && matcher.groupCount() >=3) { errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), contextMsg+" init", reglas, declaredVariablesTypeMap)); errorsEnLinea.addAll(checkExpressionVariables(matcher.group(2), contextMsg+" cond", reglas, declaredVariablesTypeMap)); errorsEnLinea.addAll(checkExpressionVariables(matcher.group(3), contextMsg+" incr", reglas, declaredVariablesTypeMap));}
                            break; 
                        }
                    }
                }
                // --- FIN DE VALIDACIONES DE SENTENCIAS GENERALES ---

                // Lógica de decremento de profundidad para bloques
                if (lineaActual.matches(reglas.getOrDefault("BLOCK_END", "^$"))) { 
                    mainFunctionBlockBraceDepth--; 
                    if (!reglaCoincidioEstaLinea) { 
                        reglaCoincidioEstaLinea = true; 
                    }
                    if (mainFunctionBlockBraceDepth == 0 && currentlyInMainFunctionBlock) { 
                        currentlyInMainFunctionBlock = false; 
                    } else if (mainFunctionBlockBraceDepth < 0 && mainFunctionDeclared) {
                        errorsEnLinea.add("Error ESTRUCTURAL: Llave de cierre '}' extra o mal colocada.");
                        mainFunctionBlockBraceDepth = 0; 
                        currentlyInMainFunctionBlock = false; 
                    }
                } else if (lineaActual.matches(reglas.getOrDefault("DO_WHILE_WHILE_STMT", "^$"))) { 
                    mainFunctionBlockBraceDepth--; 
                    if (!reglaCoincidioEstaLinea) reglaCoincidioEstaLinea = true;
                } else if (lineaActual.matches(reglas.getOrDefault("BLOCK_END_ELSE_STMT", "^$"))) { 
                    // El '{' del else ya incrementó, el '}' del if anterior decrementa aquí
                    mainFunctionBlockBraceDepth--; 
                    if (!reglaCoincidioEstaLinea) reglaCoincidioEstaLinea = true;
                }


            } else { // No es MAIN_FUNC_START y no estamos actualmente en el bloque Main
                if (!lineaActual.isEmpty()) { 
                    if (!mainFunctionDeclared) {
                        errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual + "' encontrado antes de la definición de 'FUNC J2G Main()'.");
                    } else { 
                        errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual + "' encontrado después del cierre del bloque 'FUNC J2G Main()'.");
                    }
                }
            }

            // Manejo de 'detener' sin ';'
            if (!reglaCoincidioEstaLinea && lineaActual.equals("detener")) {
                errorsEnLinea.add("Error de sintaxis: Se esperaba ';' después de 'detener'.");
                // No marcar reglaCoincidioEstaLinea = true aquí, porque es un error.
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

        // Validaciones Globales Finales
        if (!mainFunctionDeclared) {
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: No se encontró la función principal 'FUNC J2G Main() {}'.");
        } else if (currentlyInMainFunctionBlock) { 
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: El bloque 'FUNC J2G Main()' no se cerró correctamente. Estado final: enBloqueMain=" + currentlyInMainFunctionBlock + ", profundidadLlavesMain=" + mainFunctionBlockBraceDepth + ".");
        } else if (mainFunctionDeclared && mainFunctionBlockBraceDepth != 0) { // Si salimos de Main pero el contador no es 0
             globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Discrepancia en el conteo de llaves del bloque Main. Profundidad final: " + mainFunctionBlockBraceDepth + " (debería ser 0 si Main se cerró correctamente).");
        }
        
        if (this.currentlyInSwitchBlock) { // Switch quedó abierto al final del archivo
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: El bloque 'sw' iniciado en línea " + this.switchActualOpeningLine + " no se cerró correctamente."); 
            if (this.currentSwitchClauseNeedsDetener) { // Y el último caso/default no tuvo detener
                 globalStructuralErrors.add("Error en bloque final (" + this.contentOfCurrentSwitchClause + " en línea " + this.lineOfCurrentSwitchClause + "): Se esperaba 'detener;' antes del final del archivo.");
            }
        }

        if (globalBraceBalance != 0) { 
            boolean mainOrSwitchBlockErrorAlreadyReported = globalStructuralErrors.stream()
                .anyMatch(err -> err.contains("El bloque 'FUNC J2G Main()' no se cerró correctamente") || err.contains("El bloque 'sw' iniciado en línea"));
            if (!mainOrSwitchBlockErrorAlreadyReported) { 
                 globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Desbalance de llaves en el programa. Balance final global: " + globalBraceBalance + " (debería ser 0).");
            }
        }

        if (!globalStructuralErrors.isEmpty()) {
            System.out.println("\n--- Errores Estructurales Globales Detectados ---");
            for (String error : globalStructuralErrors) System.out.println(error);
            erroresEncontradosEnGeneral = true;
        }

        if (!erroresEncontradosEnGeneral && globalStructuralErrors.isEmpty()) {
            System.out.println("No se encontraron errores de estructura.");
        }
        System.out.println("---------------------------------------");
    }
}