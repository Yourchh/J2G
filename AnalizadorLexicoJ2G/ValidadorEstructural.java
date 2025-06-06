/**
 * Clase que valida la estructura y tipos de un programa J2G.
 * 
 * Esta clase verifica:
 * - Estructura general del programa (función Main)
 * - Balance de llaves y paréntesis 
 * - Declaración y uso correcto de variables
 * - Tipos de datos en asignaciones
 * - Estructuras de control (if, while, for, switch)
 * - Sintaxis de sentencias
 *
 * Principales características:
 * - Valida que exista una única función Main
 * - Verifica que las variables estén declaradas antes de usarse
 * - Comprueba que los tipos de datos sean compatibles en asignaciones
 * - Valida la estructura correcta de bloques switch/case
 * - Detecta errores de sintaxis en expresiones
 * 
 * La clase mantiene estado sobre:
 * - Variables declaradas y sus tipos
 * - Profundidad de bloques anidados
 * - Estado actual de bloques switch
 * - Balance global de llaves
 *
 */

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
    private boolean currentSwitchClauseActiveAndNeedsDetener = false;
    private boolean currentSwitchClauseHasHadDetener = false;
    private int lineOfCurrentSwitchClauseStart = 0;
    private String contentOfCurrentSwitchClauseStart = "";
    private int switchActualOpeningLine = 0;
    private String currentSwitchExpressionType = null;

    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal) {
        this.tablaSimbolosGlobal = tablaSimbolosGlobal;
    }

    private Map<String, String> cargarReglasRegexEnMemoria() {
        Map<String, String> reglas = new HashMap<>();
        String varOrIdPatternCore = "([a-z][a-zA-Z0-9_]{0,63}|id[0-9]+)";
        String optionalPromptRegex = "(?:\\((\"(?:\\\\.|[^\"\\\\])*\")\\))?";

        String stringLiteralRegex = "\"(?:\\\\.|[^\"\\\\])*\"";
        String numberLiteralRegex = "[0-9]+";
        String booleanLiteralRegex = "(TRUE|FALSE|true|false)";
        String anyLiteralOrVarRegex = "(" + varOrIdPatternCore + "|" + stringLiteralRegex + "|" + numberLiteralRegex
                + "|" + booleanLiteralRegex + ")";

        reglas.put("VAR_OR_ID_CORE", varOrIdPatternCore);
        reglas.put("VAR_NAME", "^[a-z][a-zA-Z0-9_]{0,63}$");
        reglas.put("ID_NAME", "^id[0-9]+$");
        reglas.put("VALID_LHS_VAR", "^" + varOrIdPatternCore + "$");
        reglas.put("STRING_LITERAL", stringLiteralRegex);
        reglas.put("NUMBER_LITERAL", numberLiteralRegex);
        reglas.put("BOOLEAN_LITERAL", booleanLiteralRegex);
        reglas.put("ANY_LITERAL_OR_VAR", anyLiteralOrVarRegex);
        reglas.put("MAIN_FUNC_START", "^FUNC\\s+J2G\\s+Main\\s*\\(\\s*\\)\\s*\\{");
        reglas.put("BLOCK_END", "^\\}$");
        reglas.put("VAR_DECL_NO_INIT", "^(INT|STR|BOOL)\\s+" + varOrIdPatternCore + "\\s*;");
        reglas.put("VAR_DECL_CON_INIT",
                "^(INT|STR|BOOL)\\s+" + varOrIdPatternCore + "\\s*:=\\s*" + anyLiteralOrVarRegex + "\\s*;");
        reglas.put("ASSIGNMENT", "^" + varOrIdPatternCore + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*(.+)\\s*;");
        reglas.put("INPUT_STR_STMT", "^(?:" + varOrIdPatternCore + "\\s*:=\\s*)?Input\\(\\)\\s*" + optionalPromptRegex
                + "\\.Str\\(\\)\\s*;");
        reglas.put("INPUT_INT_STMT", "^(?:" + varOrIdPatternCore + "\\s*:=\\s*)?Input\\(\\)\\s*" + optionalPromptRegex
                + "\\.Int\\(\\)\\s*;");
        reglas.put("INPUT_BOOL_STMT", "^(?:" + varOrIdPatternCore + "\\s*:=\\s*)?Input\\(\\)\\s*" + optionalPromptRegex
                + "\\.Bool\\(\\)\\s*;");
        reglas.put("IF_STMT", "^if\\s*\\((.+)\\)\\s*\\{");
        reglas.put("ELSE_STMT", "^else\\s*\\{");
        reglas.put("BLOCK_END_ELSE_STMT", "^\\}\\s*else\\s*\\{");
        reglas.put("SWITCH_STMT", "^sw\\s*\\((.+)\\)\\s*\\{");
        reglas.put("CASE_STMT", "^caso\\s+" + anyLiteralOrVarRegex + "\\s*:");
        reglas.put("DEFAULT_STMT", "^por_defecto\\s*:");
        reglas.put("DETENER_STMT", "^detener\\s*;");
        // Updated FOR_STMT regex to use (?<!=):(?!=) for more specific colon delimiters
        reglas.put("FOR_STMT", "^for\\s*\\((.*?)\\s*(?<!=):(?!=)\\s*(.*?)\\s*(?<!=):(?!=)\\s*(.*?)\\)\\s*\\{");
        reglas.put("WHILE_STMT", "^while\\s*\\((.+)\\)\\s*\\{");
        reglas.put("DO_WHILE_DO_STMT", "^do\\s*\\{");
        reglas.put("DO_WHILE_CLOSURE_LINE_STMT", "^\\}\\s*while\\s*\\((.+)\\)\\s*;");
        reglas.put("DO_WHILE_TAIL_ONLY_STMT", "^while\\s*\\((.+)\\)\\s*;");
        reglas.put("PRINT_STMT", "^Print\\s*\\((.*?)\\)\\s*;");

        return reglas;
    }

    private char getMatchingClosingSymbol(char openSymbol) {
        if (openSymbol == '(')
            return ')';
        if (openSymbol == '{')
            return '}';
        if (openSymbol == '[')
            return ']';
        return '?';
    }

    private List<String> checkBalancedSymbols(String text, int lineNumber, String contextDesc) {
        List<String> errors = new ArrayList<>();
        Stack<Character> stack = new Stack<>();
        Stack<Integer> positionStack = new Stack<>();

        Map<Character, Character> symbolPairs = new HashMap<>();
        symbolPairs.put(')', '(');
        symbolPairs.put('}', '{');
        symbolPairs.put(']', '[');

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (symbolPairs.containsValue(c)) {
                stack.push(c);
                positionStack.push(i);
            } else if (symbolPairs.containsKey(c)) {
                if (stack.isEmpty()) {
                    errors.add("Error de sintaxis: Símbolo de cierre '" + c + "' inesperado en columna " + (i + 1)
                            + " (no hay símbolo de apertura correspondiente) " + contextDesc + ".");
                } else if (stack.peek() != symbolPairs.get(c)) {
                    errors.add("Error de sintaxis: Símbolo de cierre '" + c + "' en columna " + (i + 1) +
                            " no coincide con el símbolo de apertura esperado '"
                            + getMatchingClosingSymbol(stack.peek()) + "' (abierto en columna "
                            + (positionStack.peek() + 1) + ") " + contextDesc + ".");
                } else {
                    stack.pop();
                    positionStack.pop();
                }
            }
        }
        while (!stack.isEmpty()) {
            char openSymbol = stack.pop();
            int openPos = positionStack.pop();
            char expectedClosing = getMatchingClosingSymbol(openSymbol);
            errors.add("Error de sintaxis: Falta símbolo de cierre '" + expectedClosing + "' para '" + openSymbol
                    + "' abierto en columna " + (openPos + 1) + " " + contextDesc + ".");
        }
        return errors;
    }

    private boolean isValidSourceVarName(String name, Map<String, String> reglas) {
        return name.matches(reglas.getOrDefault("VAR_NAME", "^[a-z][a-zA-Z0-9_]{0,63}$"));
    }

    private boolean isGeneratedIdName(String name, Map<String, String> reglas) {
        return name.matches(reglas.getOrDefault("ID_NAME", "^id[0-9]+$"));
    }

    private List<String> checkVariableUsage(String varName, String context, Map<String, String> reglas,
            Map<String, String> declaredVariablesTypeMap) {
        List<String> errors = new ArrayList<>();
        boolean isKeyword = this.tablaSimbolosGlobal.getPalabrasReservadasYSimbolosConocidos().contains(varName) &&
                !varName.equalsIgnoreCase("TRUE") && !varName.equalsIgnoreCase("FALSE");
        if (isKeyword) {
            if (!((context.contains("Input")
                    && (varName.equals("Int") || varName.equals("Str") || varName.equals("Bool"))) ||
                    (context.contains("tipo_dato")
                            && (varName.equals("INT") || varName.equals("STR") || varName.equals("BOOL"))))) {
                errors.add("Error semántico: Palabra reservada '" + varName + "' usada como variable " + context + ".");
                return errors;
            }
        }

        boolean isValidFormat = isValidSourceVarName(varName, reglas) || isGeneratedIdName(varName, reglas);
        if (!isValidFormat) {
            if (!varName.equalsIgnoreCase("TRUE") && !varName.equalsIgnoreCase("FALSE"))
                errors.add("Nombre de variable inválido '" + varName + "' " + context + ".");
        } else {
            if (isValidSourceVarName(varName, reglas) && !declaredVariablesTypeMap.containsKey(varName)) {
                errors.add("Variable '" + varName + "' no declarada " + context + ".");
            }
        }
        return errors;
    }

    private List<String> checkExpressionVariables(String expression, String contextForExpression,
            Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap, int lineNumber) {
        List<String> errors = new ArrayList<>();
        if (expression == null || expression.trim().isEmpty())
            return errors;

        errors.addAll(checkBalancedSymbols(expression, lineNumber,
                "en la expresión '" + expression + "' " + contextForExpression));

        List<String> localTabsimTokens = new ArrayList<>(
                this.tablaSimbolosGlobal.getPalabrasReservadasYSimbolosConocidos());
        localTabsimTokens
                .sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));

        StringBuilder regexPatternBuilder = new StringBuilder();
        regexPatternBuilder.append("(" + reglas.get("STRING_LITERAL") + ")");
        for (String token : localTabsimTokens) {
            regexPatternBuilder.append("|(").append(Pattern.quote(token)).append(")");
        }
        regexPatternBuilder.append("|(" + reglas.get("VAR_OR_ID_CORE") + ")");
        regexPatternBuilder.append("|(" + reglas.get("NUMBER_LITERAL") + ")");

        Pattern tokenPattern = Pattern.compile(regexPatternBuilder.toString());
        Matcher matcher = tokenPattern.matcher(expression);

        while (matcher.find()) {
            String matchedStringLiteral = matcher.group(1);
            String matchedIdentifier = null;
            String matchedNumber = null;
            boolean matchedKnownToken = false;

            int knownTokenGroupStart = 2;
            int knownTokenGroupEnd = knownTokenGroupStart + localTabsimTokens.size() - 1;
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
                continue;
            }

            if (matchedIdentifier != null) {
                String varName = matchedIdentifier;
                if (!varName.equals("Input") && !varName.equals("Str") && !varName.equals("Int")
                        && !varName.equals("Bool")) {
                    errors.addAll(checkVariableUsage(varName, contextForExpression, reglas, declaredVariablesTypeMap));
                }
            }
        }
        return errors;
    }

     private String getExpressionType(String expression, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap) {
        if (expression == null) return "UNKNOWN"; // Manejar null para evitar NullPointerException
        String trimmedExpression = expression.trim(); // Usar la expresión trimeada para las comparaciones

        if (trimmedExpression.matches(reglas.get("STRING_LITERAL"))) return "STR";
        if (trimmedExpression.matches(reglas.get("NUMBER_LITERAL"))) return "INT";
        if (trimmedExpression.matches(reglas.get("BOOLEAN_LITERAL"))) return "BOOL";
        if (declaredVariablesTypeMap.containsKey(trimmedExpression)) return declaredVariablesTypeMap.get(trimmedExpression);

        // Validar Input().Tipo() usando regex para flexibilidad con espacios
        // Estas regex son para la parte de la expresión, no para la sentencia completa con ';'
        if (trimmedExpression.matches("^Input\\s*\\(\\s*\\)\\s*\\.\\s*Str\\s*\\(\\s*\\)$")) return "STR";
        if (trimmedExpression.matches("^Input\\s*\\(\\s*\\)\\s*\\.\\s*Int\\s*\\(\\s*\\)$")) return "INT";
        if (trimmedExpression.matches("^Input\\s*\\(\\s*\\)\\s*\\.\\s*Bool\\s*\\(\\s*\\)$")) return "BOOL";
        
        return "UNKNOWN"; 
    }

    private void performAssignmentTypeChecks(String lhsVar, String op, String rhsExpression, String context,
            Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap,
            List<String> errorsEnLinea) {
        if (declaredVariablesTypeMap.containsKey(lhsVar)) {
            String lhsType = declaredVariablesTypeMap.get(lhsVar);
            String rhsType = getExpressionType(rhsExpression, reglas, declaredVariablesTypeMap);

            if (op.equals(":=")) {
                if (rhsType.equals("UNKNOWN")) {
                    // Si el tipo del lado derecho es desconocido, verificamos si es un problema con
                    // Input
                    if (rhsExpression.matches("^Input\\s*\\(\\s*\\)$")) { // Coincide con "Input()" exacto
                        errorsEnLinea.add(
                                "Error de sintaxis: Falta concatenar tipo de dato al Input (ej: Input().Int(), Input().Str()) en la asignación a '"
                                        + lhsVar + "'. Contexto: " + context + ".");
                    } else if (rhsExpression.startsWith("Input")) {
                        errorsEnLinea.add("Error: La estructura del Input ('" + rhsExpression
                                + "') no es la adecuada o está incompleta para la asignación a '" + lhsVar
                                + "'. Verifique la sintaxis (ej: Input().Int();). Contexto: " + context + ".");
                    } else {
                        errorsEnLinea.add("Error de Tipo: No se pudo determinar el tipo de la expresión " + rhsExpression + "' para la asignación a '" + lhsVar + "'. Contexto: " + context + ".");
                    }
                } else if (!lhsType.equals(rhsType)) { // rhsType es CONOCIDO y DIFERENTE de lhsType
                    errorsEnLinea.add("Error de Tipo: No se puede asignar un valor de tipo " + rhsType +
                            " a la variable '" + lhsVar + "' (tipo " + lhsType + ") " + context + ".");
                }
                // Si rhsType es CONOCIDO e IGUAL a lhsType, la asignación es válida, no hay
                // error.

            } else if (op.matches("\\+=|-=|\\*=|\\/=")) {
                // Para operadores compuestos, el RHS también debe ser verificable.
                if (!lhsType.equals("INT")) {
                    errorsEnLinea.add("Error de tipo: Operador de asignación compuesta '" + op +
                            "' requiere que la variable '" + lhsVar + "' sea de tipo INT, pero es " + lhsType + " "
                            + context + ".");
                }
                if (rhsType.equals("UNKNOWN")) {
                    // Si el tipo del RHS es desconocido para un operador compuesto.
                    if (rhsExpression.startsWith("Input")) { // Es poco probable usar Input directamente aquí, pero por
                                                             // si acaso.
                        errorsEnLinea.add("Error: No se puede usar una expresión Input ('" + rhsExpression
                                + "') directamente con el operador de asignación compuesta '" + op
                                + "'. Se espera un valor de tipo INT. Contexto: " + context + ".");
                    } else {
                        errorsEnLinea.add("Error de tipo: No se pudo determinar el tipo de la expresión '"
                                + rhsExpression + "' para el operador de asignación compuesta '" + op
                                + "'. Se espera un valor de tipo INT. Contexto: " + context + ".");
                    }
                } else if (!rhsType.equals("INT")) {
                    errorsEnLinea.add("Error de tipo: Operador de asignación compuesta '" + op +
                            "' requiere que el valor a la derecha ('" + rhsExpression + "') sea de tipo INT, pero es "
                            + rhsType + " " + context + ".");
                }
            }
        }
        // Si lhsVar no está en declaredVariablesTypeMap, checkVariableUsage ya debería
        // haber reportado
        // "Variable 'lhsVar' no declarada".
    }

    public void validarEstructuraConRegex(String codigoLimpioFormateado) {
        Map<String, String> reglas = cargarReglasRegexEnMemoria();
        Map<String, String> declaredVariablesTypeMap = new HashMap<>();
        int globalBraceBalance = 0;
        List<String> globalStructuralErrors = new ArrayList<>();

        boolean mainFunctionDeclared = false;
        boolean currentlyInMainFunctionBlock = false;
        int mainFunctionBlockBraceDepth = 0;

        this.currentlyInSwitchBlock = false;
        this.switchBlockEntryDepth = 0;
        this.currentSwitchClauseActiveAndNeedsDetener = false;
        this.currentSwitchClauseHasHadDetener = false;
        this.lineOfCurrentSwitchClauseStart = 0;
        this.contentOfCurrentSwitchClauseStart = "";
        this.switchActualOpeningLine = 0;
        this.currentSwitchExpressionType = null;

        String varOrIdCorePattern = reglas.get("VAR_OR_ID_CORE");

        String[] lineasCodigo = codigoLimpioFormateado.split("\n");
        boolean erroresEncontradosEnGeneral = false;

        for (int i = 0; i < lineasCodigo.length; i++) {
            String lineaActual = lineasCodigo[i].trim();
            String originalLineForBraceCheck = lineasCodigo[i];
            int currentLineNumber = i + 1;
            if (lineaActual.isEmpty())
                continue;

            boolean reglaCoincidioEstaLinea = false;
            List<String> errorsEnLinea = new ArrayList<>();
            Matcher matcher;

            int lineBraceBalance = 0;
            for (char ch : originalLineForBraceCheck.toCharArray()) {
                if (ch == '{') {
                    globalBraceBalance++;
                    lineBraceBalance++;
                } else if (ch == '}') {
                    globalBraceBalance--;
                    lineBraceBalance--;
                }
            }
            if (globalBraceBalance < 0 && lineBraceBalance < 0
                    && errorsEnLinea.stream().noneMatch(err -> err.contains("inesperada"))) {
                errorsEnLinea.add(
                        "Error de sintaxis: Llave de cierre '}' inesperada en esta línea (causa desbalance global).");
            }

            if (lineaActual.matches(reglas.getOrDefault("MAIN_FUNC_START", "^$"))) {
                if (mainFunctionDeclared)
                    errorsEnLinea.add("Error ESTRUCTURAL: Múltiples definiciones de 'FUNC J2G Main()'.");
                if (currentlyInMainFunctionBlock)
                    errorsEnLinea.add("Error ESTRUCTURAL: Definición de 'FUNC J2G Main()' anidada no permitida.");
                mainFunctionDeclared = true;
                currentlyInMainFunctionBlock = true;
                mainFunctionBlockBraceDepth = 1;
                reglaCoincidioEstaLinea = true;
            } else if (currentlyInMainFunctionBlock) {
                boolean esAperturaDeBloqueInternoValido = false;
                String[] blockOpeningRegexKeys = { "IF_STMT", "WHILE_STMT", /* "FOR_STMT", */ "SWITCH_STMT",
                        "DO_WHILE_DO_STMT", "ELSE_STMT", "BLOCK_END_ELSE_STMT" };
                for (String key : blockOpeningRegexKeys) {
                    if (lineaActual.matches(reglas.getOrDefault(key, "^$")) && lineaActual.endsWith("{")) {
                        esAperturaDeBloqueInternoValido = true;
                        break;
                    }
                }
                if (esAperturaDeBloqueInternoValido) {
                    mainFunctionBlockBraceDepth++;
                }

                if (lineaActual.matches(reglas.getOrDefault("SWITCH_STMT", "^$"))) {
                    reglaCoincidioEstaLinea = true;
                    this.currentlyInSwitchBlock = true;
                    this.switchBlockEntryDepth = mainFunctionBlockBraceDepth;
                    this.currentSwitchClauseActiveAndNeedsDetener = false;
                    this.currentSwitchClauseHasHadDetener = false;
                    this.switchActualOpeningLine = currentLineNumber;
                    this.currentSwitchExpressionType = null;

                    Matcher switchMatcher = Pattern.compile(reglas.get("SWITCH_STMT")).matcher(lineaActual);
                    if (switchMatcher.matches()) {
                        String switchExpressionText = switchMatcher.group(1).trim();
                        errorsEnLinea.addAll(checkExpressionVariables(switchExpressionText, "en expr switch", reglas,
                                declaredVariablesTypeMap, currentLineNumber));
                        this.currentSwitchExpressionType = getExpressionType(switchExpressionText, reglas,
                                declaredVariablesTypeMap);
                        if (this.currentSwitchExpressionType.equals("UNKNOWN")
                                && switchExpressionText.matches(varOrIdCorePattern)
                                && !declaredVariablesTypeMap.containsKey(switchExpressionText)) {
                        } else if (this.currentSwitchExpressionType.equals("UNKNOWN")) {
                            errorsEnLinea.add("Error semántico: La expresión del switch '" + switchExpressionText
                                    + "' no es una variable declarada ni un literal simple (STR, INT, BOOL) cuyo tipo se pueda determinar para la comparación de casos.");
                        }
                    } else {
                        errorsEnLinea.addAll(
                                checkBalancedSymbols(lineaActual, currentLineNumber, "en declaración de switch"));
                    }
                } else if (this.currentlyInSwitchBlock) {
                    if (lineaActual.matches(reglas.getOrDefault("CASE_STMT", "^$"))
                            || lineaActual.matches(reglas.getOrDefault("DEFAULT_STMT", "^$"))) {
                        reglaCoincidioEstaLinea = true;
                        if (this.currentSwitchClauseActiveAndNeedsDetener) {
                            globalStructuralErrors
                                    .add("Error en bloque anterior (" + this.contentOfCurrentSwitchClauseStart
                                            + " en línea " + this.lineOfCurrentSwitchClauseStart
                                            + "): Se esperaba 'detener;' antes de este nuevo bloque de caso/default.");
                        }
                        this.currentSwitchClauseActiveAndNeedsDetener = true;
                        this.currentSwitchClauseHasHadDetener = false;
                        this.lineOfCurrentSwitchClauseStart = currentLineNumber;
                        this.contentOfCurrentSwitchClauseStart = lineaActual;

                        if (lineaActual.matches(reglas.getOrDefault("CASE_STMT", "^$"))) {
                            Matcher caseMatcher = Pattern.compile(reglas.get("CASE_STMT")).matcher(lineaActual);
                            if (caseMatcher.matches()) {
                                String actualCaseValue = caseMatcher.group(1).trim();
                                errorsEnLinea.addAll(checkExpressionVariables(actualCaseValue,
                                        "en valor de caso '" + actualCaseValue + "'", reglas, declaredVariablesTypeMap,
                                        currentLineNumber));
                                String caseValueType = getExpressionType(actualCaseValue, reglas,
                                        declaredVariablesTypeMap);

                                if (this.currentSwitchExpressionType != null
                                        && !this.currentSwitchExpressionType.equals("UNKNOWN") &&
                                        !caseValueType.equals("UNKNOWN")
                                        && !this.currentSwitchExpressionType.equals(caseValueType)) {
                                    errorsEnLinea.add("Error de tipo en caso: El valor del caso '" + actualCaseValue
                                            + "' (tipo " + caseValueType +
                                            ") no coincide con el tipo de la expresión del switch ("
                                            + this.currentSwitchExpressionType + ").");
                                } else if (caseValueType.equals("UNKNOWN")
                                        && !actualCaseValue.matches(varOrIdCorePattern) && !actualCaseValue.isEmpty()) {
                                    errorsEnLinea.add("Error de sintaxis: Valor de caso '" + actualCaseValue
                                            + "' no es un literal válido (STR, INT, BOOL) ni una variable declarada.");
                                }
                            }
                        }
                    } else if (lineaActual.matches(reglas.getOrDefault("DETENER_STMT", "^$"))) {
                        reglaCoincidioEstaLinea = true;
                        if (!this.currentSwitchClauseActiveAndNeedsDetener && !this.currentSwitchClauseHasHadDetener) {
                            errorsEnLinea.add(
                                    "Error: 'detener;' encontrado fuera de un bloque de caso/default que lo requiera, o sin un caso/default activo.");
                        } else if (this.currentSwitchClauseHasHadDetener) {
                            errorsEnLinea.add("Error: Múltiples 'detener;' para el mismo bloque de caso/default.");
                        }
                        this.currentSwitchClauseActiveAndNeedsDetener = false;
                        this.currentSwitchClauseHasHadDetener = true;
                    } else if (lineaActual.matches(reglas.getOrDefault("BLOCK_END", "^$"))) {
                        if (mainFunctionBlockBraceDepth == this.switchBlockEntryDepth) {
                            if (this.currentSwitchClauseActiveAndNeedsDetener) {
                                globalStructuralErrors
                                        .add("Error en bloque final (" + this.contentOfCurrentSwitchClauseStart
                                                + " en línea " + this.lineOfCurrentSwitchClauseStart
                                                + "): Se esperaba 'detener;' antes de cerrar el bloque 'sw'.");
                            }
                            this.currentlyInSwitchBlock = false;
                            this.currentSwitchClauseActiveAndNeedsDetener = false;
                            this.currentSwitchClauseHasHadDetener = false;
                            this.currentSwitchExpressionType = null;
                        }
                    } else {
                        if (this.currentSwitchClauseHasHadDetener) {
                            errorsEnLinea.add("Error: Código '" + lineaActual
                                    + "' encontrado después de 'detener;' y antes del siguiente caso/default o fin del switch.");
                            reglaCoincidioEstaLinea = true;
                        }
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("FOR_STMT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        mainFunctionBlockBraceDepth++;

                        String initPart = matcher.group(1).trim();
                        String conditionPart = matcher.group(2).trim();
                        String updatePart = matcher.group(3).trim();
                        String contextFor;

                        if (!initPart.isEmpty()) {
                            contextFor = "en inicialización de for";
                            Matcher initDeclMatcher = Pattern
                                    .compile("^(INT|STR|BOOL)\\s+" + varOrIdCorePattern + "\\s*:=\\s*(.+)$")
                                    .matcher(initPart);
                            Matcher initAssignMatcher = Pattern
                                    .compile("^" + varOrIdCorePattern + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*(.+)$")
                                    .matcher(initPart);

                            if (initDeclMatcher.matches()) {
                                String varDeclaredType = initDeclMatcher.group(1);
                                String varName = initDeclMatcher.group(2);
                                String rhsAssigned = initDeclMatcher.group(3).trim();
                                declaredVariablesTypeMap.put(varName, varDeclaredType);
                                errorsEnLinea.addAll(checkVariableUsage(varName, contextFor + " (declaración)", reglas,
                                        declaredVariablesTypeMap));
                                errorsEnLinea.addAll(checkExpressionVariables(rhsAssigned,
                                        contextFor + " (RHS para " + varName + ")", reglas, declaredVariablesTypeMap,
                                        currentLineNumber));
                                performAssignmentTypeChecks(varName, ":=", rhsAssigned, contextFor, reglas,
                                        declaredVariablesTypeMap, errorsEnLinea);
                            } else if (initAssignMatcher.matches()) {
                                String lhsVar = initAssignMatcher.group(1);
                                String op = initAssignMatcher.group(2);
                                String rhsEx = initAssignMatcher.group(3).trim();
                                errorsEnLinea.addAll(checkVariableUsage(lhsVar, contextFor + " (LHS asignación)",
                                        reglas, declaredVariablesTypeMap));
                                errorsEnLinea.addAll(checkExpressionVariables(rhsEx,
                                        contextFor + " (RHS asignación para " + lhsVar + ")", reglas,
                                        declaredVariablesTypeMap, currentLineNumber));
                                performAssignmentTypeChecks(lhsVar, op, rhsEx, contextFor, reglas,
                                        declaredVariablesTypeMap, errorsEnLinea);
                            } else {
                                errorsEnLinea.add("Error de sintaxis: Parte de inicialización del for ('" + initPart
                                        + "') no es una declaración (ej: INT i := 0) o una asignación válida.");
                                errorsEnLinea.addAll(checkExpressionVariables(initPart, contextFor, reglas,
                                        declaredVariablesTypeMap, currentLineNumber));
                            }
                        }

                        contextFor = "en condición de for";
                        if (!conditionPart.isEmpty()) {
                            errorsEnLinea.addAll(checkExpressionVariables(conditionPart, contextFor, reglas,
                                    declaredVariablesTypeMap, currentLineNumber));
                        } else {
                            errorsEnLinea.add("Error de sintaxis: Falta la parte de condición en la sentencia for.");
                        }

                        contextFor = "en actualización de for";
                        if (!updatePart.isEmpty()) {
                            Matcher updateAssignMatcher = Pattern
                                    .compile("^" + varOrIdCorePattern + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*(.+)$")
                                    .matcher(updatePart);
                            if (updateAssignMatcher.matches()) {
                                String lhsVar = updateAssignMatcher.group(1);
                                String op = updateAssignMatcher.group(2);
                                String rhsEx = updateAssignMatcher.group(3).trim();
                                errorsEnLinea.addAll(checkVariableUsage(lhsVar, contextFor + " (LHS actualización)",
                                        reglas, declaredVariablesTypeMap));
                                errorsEnLinea.addAll(checkExpressionVariables(rhsEx,
                                        contextFor + " (RHS actualización para " + lhsVar + ")", reglas,
                                        declaredVariablesTypeMap, currentLineNumber));
                                performAssignmentTypeChecks(lhsVar, op, rhsEx, contextFor, reglas,
                                        declaredVariablesTypeMap, errorsEnLinea);
                            } else {
                                errorsEnLinea.add("Error de sintaxis: Parte de actualización del for ('" + updatePart
                                        + "') no es una asignación válida (ej: i += 1).");
                                errorsEnLinea.addAll(checkExpressionVariables(updatePart, contextFor, reglas,
                                        declaredVariablesTypeMap, currentLineNumber));
                            }
                        }
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_NO_INIT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        String varDeclaredType = matcher.group(1);
                        String varName = matcher.group(2);
                        if (declaredVariablesTypeMap.containsKey(varName))
                            errorsEnLinea
                                    .add("Error Semántico: Variable '" + varName + "' ya declarada (redefinición).");
                        else
                            declaredVariablesTypeMap.put(varName, varDeclaredType);
                        errorsEnLinea.addAll(checkVariableUsage(varName, "en declaración (nombre)", reglas,
                                declaredVariablesTypeMap));
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_CON_INIT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        String varDeclaredType = matcher.group(1);
                        String varName = matcher.group(2);
                        String rhsAssigned = matcher.group(3);

                        if (declaredVariablesTypeMap.containsKey(varName))
                            errorsEnLinea
                                    .add("Error Semántico: Variable '" + varName + "' ya declarada (redefinición).");
                        else
                            declaredVariablesTypeMap.put(varName, varDeclaredType);
                        errorsEnLinea.addAll(checkVariableUsage(varName, "en declaración (nombre)", reglas,
                                declaredVariablesTypeMap));
                        errorsEnLinea.addAll(
                                checkExpressionVariables(rhsAssigned, "en RHS de inicialización para " + varName,
                                        reglas, declaredVariablesTypeMap, currentLineNumber));
                        performAssignmentTypeChecks(varName, ":=", rhsAssigned, "en inicialización de variable", reglas,
                                declaredVariablesTypeMap, errorsEnLinea);
                    }
                }

                String[] inputTypes = { "STR", "INT", "BOOL" };
                String[] inputMethodNames = { "Str", "Int", "Bool" };
                for (int k = 0; k < inputTypes.length && !reglaCoincidioEstaLinea; k++) {
                    matcher = Pattern.compile(reglas.getOrDefault("INPUT_" + inputTypes[k] + "_STMT", "^$"))
                            .matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        errorsEnLinea
                                .addAll(checkBalancedSymbols(lineaActual, currentLineNumber, "en sentencia Input"));
                        String lhsVar = matcher.group(1);
                        if (lhsVar != null && !lhsVar.isEmpty()) {
                            errorsEnLinea.addAll(
                                    checkVariableUsage(lhsVar, "en LHS de Input", reglas, declaredVariablesTypeMap));
                            if (declaredVariablesTypeMap.containsKey(lhsVar) &&
                                    !declaredVariablesTypeMap.get(lhsVar).equals(inputTypes[k])) {
                                errorsEnLinea.add("Error de Tipo: El método Input." + inputMethodNames[k]
                                        + "() devuelve " + inputTypes[k] +
                                        ", pero se intenta asignar a la variable '" + lhsVar + "' que es de tipo " +
                                        declaredVariablesTypeMap.get(lhsVar) + ".");
                            }
                        }
                        if (matcher.group(2) != null) {
                            errorsEnLinea.addAll(
                                    checkBalancedSymbols(matcher.group(2), currentLineNumber, "en prompt de Input"));
                        }
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("ASSIGNMENT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        String lhsVar = matcher.group(1);
                        String op = matcher.group(2);
                        String rhsExpression = matcher.group(3).trim();
                        errorsEnLinea.addAll(
                                checkVariableUsage(lhsVar, "en LHS de asignación", reglas, declaredVariablesTypeMap));
                        errorsEnLinea
                                .addAll(checkExpressionVariables(rhsExpression, "en RHS de asignación para " + lhsVar,
                                        reglas, declaredVariablesTypeMap, currentLineNumber));
                        performAssignmentTypeChecks(lhsVar, op, rhsExpression, "en asignación", reglas,
                                declaredVariablesTypeMap, errorsEnLinea);
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    String[] cStructs = { "IF_STMT", "WHILE_STMT", "DO_WHILE_TAIL_ONLY_STMT", "PRINT_STMT",
                            "DO_WHILE_CLOSURE_LINE_STMT", "ELSE_STMT",
                            "BLOCK_END_ELSE_STMT", "DO_WHILE_DO_STMT" };
                    for (String key : cStructs) {
                        matcher = Pattern.compile(reglas.getOrDefault(key, "^$")).matcher(lineaActual);
                        if (matcher.matches()) {
                            reglaCoincidioEstaLinea = true;
                            String contextMsg = "en " + key.replace("_STMT", "").toLowerCase();

                            if (key.equals("IF_STMT") || key.equals("WHILE_STMT") ||
                                    key.equals("DO_WHILE_CLOSURE_LINE_STMT") || key.equals("DO_WHILE_TAIL_ONLY_STMT")) {
                                errorsEnLinea
                                        .addAll(checkExpressionVariables(matcher.group(1), "condición de " + contextMsg,
                                                reglas, declaredVariablesTypeMap, currentLineNumber));
                            } else if (key.equals("PRINT_STMT")) {
                                errorsEnLinea
                                        .addAll(checkExpressionVariables(matcher.group(1), "argumento de " + contextMsg,
                                                reglas, declaredVariablesTypeMap, currentLineNumber));
                            } else {
                                if (!key.equals("ELSE_STMT") && !key.equals("BLOCK_END_ELSE_STMT")
                                        && !key.equals("DO_WHILE_DO_STMT")) {
                                    errorsEnLinea
                                            .addAll(checkBalancedSymbols(lineaActual, currentLineNumber, contextMsg));
                                }
                            }
                            break;
                        }
                    }
                }

                if (lineaActual.matches(reglas.getOrDefault("BLOCK_END", "^$"))) {
                    if (!reglaCoincidioEstaLinea && this.currentlyInSwitchBlock
                            && mainFunctionBlockBraceDepth == this.switchBlockEntryDepth) {
                        if (this.currentSwitchClauseActiveAndNeedsDetener) {
                            globalStructuralErrors
                                    .add("Error en bloque final (" + this.contentOfCurrentSwitchClauseStart
                                            + " en línea " + this.lineOfCurrentSwitchClauseStart
                                            + "): Se esperaba 'detener;' antes de cerrar el bloque 'sw'.");
                        }
                        this.currentlyInSwitchBlock = false;
                    }

                    mainFunctionBlockBraceDepth--;
                    if (!reglaCoincidioEstaLinea)
                        reglaCoincidioEstaLinea = true;

                    if (mainFunctionBlockBraceDepth == 0 && currentlyInMainFunctionBlock) {
                        currentlyInMainFunctionBlock = false;
                    } else if (mainFunctionBlockBraceDepth < 0 && mainFunctionDeclared) {
                        if (errorsEnLinea.stream().noneMatch(e -> e.contains("Llave de cierre '}' inesperada"))) {
                            errorsEnLinea.add("Error ESTRUCTURAL: Llave de cierre '}' extra o mal colocada.");
                        }
                        mainFunctionBlockBraceDepth = 0;
                        currentlyInMainFunctionBlock = false;
                    }
                } else if (lineaActual.matches(reglas.getOrDefault("DO_WHILE_CLOSURE_LINE_STMT", "^$"))) {
                    mainFunctionBlockBraceDepth--;
                    if (!reglaCoincidioEstaLinea)
                        reglaCoincidioEstaLinea = true;
                } else if (lineaActual.matches(reglas.getOrDefault("BLOCK_END_ELSE_STMT", "^$"))) {
                    mainFunctionBlockBraceDepth--;
                    if (!reglaCoincidioEstaLinea)
                        reglaCoincidioEstaLinea = true;
                }

            } else {
                if (!lineaActual.isEmpty()) {
                    if (!mainFunctionDeclared) {
                        errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual
                                + "' encontrado antes de la definición de 'FUNC J2G Main()'.");
                    } else {
                        errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual
                                + "' encontrado después del cierre del bloque 'FUNC J2G Main()'.");
                    }
                    errorsEnLinea.addAll(checkBalancedSymbols(lineaActual, currentLineNumber, "fuera de Main"));
                }
            }

            if (!reglaCoincidioEstaLinea && currentlyInMainFunctionBlock && !lineaActual.isEmpty()) {
                if (lineaActual.equals("detener")) {
                    errorsEnLinea.add(
                            "Error de sintaxis: Se esperaba ';' después de 'detener'. Línea: '" + lineaActual + "'.");
                } else if (lineaActual.startsWith("detener") && !lineaActual.matches(reglas.get("DETENER_STMT"))) {
                    errorsEnLinea.add(
                            "Error de sintaxis: 'detener' mal formado o sentencia incompleta. Probablemente falta un ';' después de 'detener'. Línea: '"
                                    + lineaActual + "'.");
                } else {
                    List<String> balanceErrors = checkBalancedSymbols(lineaActual, currentLineNumber,
                            "en línea no reconocida: '" + lineaActual + "'");
                    if (!balanceErrors.isEmpty()) {
                        errorsEnLinea.addAll(balanceErrors);
                    } else {
                        boolean potentiallyMissingSemicolon = false;
                        String varOrIdPatternPart = reglas.get("VALID_LHS_VAR");
                        if (varOrIdPatternPart.startsWith("^"))
                            varOrIdPatternPart = varOrIdPatternPart.substring(1);
                        if (varOrIdPatternPart.endsWith("$"))
                            varOrIdPatternPart = varOrIdPatternPart.substring(0, varOrIdPatternPart.length() - 1);
                        String anyLiteralOrVarPatternPart = reglas.get("ANY_LITERAL_OR_VAR");
                        String[] commonStmtPatternsNoSemicolon = {
                                "^Print\\s*\\(.*?\\)$",
                                "^(INT|STR|BOOL)\\s+" + varOrIdPatternPart + "(\\s*:=\\s*" + anyLiteralOrVarPatternPart
                                        + ")?$",
                                "^" + varOrIdPatternPart + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*.+$",
                                "^Input\\s*.*?\\.Str\\(\\)$", "^Input\\s*.*?\\.Int\\(\\)$",
                                "^Input\\s*.*?\\.Bool\\(\\)$"
                        };
                        for (String stmtPattern : commonStmtPatternsNoSemicolon) {
                            if (lineaActual.matches(stmtPattern)) {
                                errorsEnLinea.add(
                                        "Error de sintaxis: Posiblemente falta un ';' al final de la sentencia. Línea: '"
                                                + lineaActual + "'.");
                                potentiallyMissingSemicolon = true;
                                break;
                            }
                        }
                        if (!potentiallyMissingSemicolon) {
                            errorsEnLinea.add(
                                    "Error de estructura/sintaxis general en la línea (no coincide con ninguna regla conocida o está mal formada): '"
                                            + lineaActual + "'.");
                        }
                    }
                }
            }

            if (!errorsEnLinea.isEmpty()) {
                System.out.println("Error(es) en línea " + currentLineNumber + ": " + lineasCodigo[i]);
                for (String error : errorsEnLinea)
                    System.out.println("  - " + error);
                erroresEncontradosEnGeneral = true;
            }
        }

        if (!mainFunctionDeclared) {
            globalStructuralErrors
                    .add("Error ESTRUCTURAL GLOBAL: No se encontró la función principal 'FUNC J2G Main() {}'.");
        } else if (currentlyInMainFunctionBlock) {
            globalStructuralErrors
                    .add("Error ESTRUCTURAL GLOBAL: El bloque 'FUNC J2G Main()' no se cerró correctamente (faltan "
                            + mainFunctionBlockBraceDepth + " llave(s) de cierre '}').");
        }

        if (this.currentlyInSwitchBlock) {
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: El bloque 'sw' iniciado en línea "
                    + this.switchActualOpeningLine + " no se cerró correctamente (posiblemente falta '}').");
            if (this.currentSwitchClauseActiveAndNeedsDetener) {
                globalStructuralErrors.add("Error en el último bloque (" + this.contentOfCurrentSwitchClauseStart
                        + " en línea " + this.lineOfCurrentSwitchClauseStart
                        + ") del switch no cerrado: Se esperaba 'detener;' antes del final del archivo o cierre del switch.");
            }
        }

        if (globalBraceBalance != 0 && !currentlyInMainFunctionBlock && mainFunctionDeclared) {
            boolean mainOrSwitchBlockErrorAlreadyReported = globalStructuralErrors.stream()
                    .anyMatch(err -> err.contains("El bloque 'FUNC J2G Main()' no se cerró correctamente") ||
                            err.contains("El bloque 'sw' iniciado en línea"));
            if (!mainOrSwitchBlockErrorAlreadyReported) {
                if (globalBraceBalance > 0) {
                    globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Faltan " + globalBraceBalance
                            + " llave(s) de cierre '}' en el programa.");
                } else {
                    globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Hay " + (-globalBraceBalance)
                            + " llave(s) de cierre '}' extra o mal colocadas en el programa.");
                }
            }
        }

        if (!globalStructuralErrors.isEmpty()) {
            System.out.println("\n--- Errores Estructurales Globales Detectados ---");
            for (String error : globalStructuralErrors)
                System.out.println(error);
            erroresEncontradosEnGeneral = true;
        }

        if (!erroresEncontradosEnGeneral && globalStructuralErrors.isEmpty()) {
            System.out.println("No se encontraron errores de estructura.");
        }
        System.out.println("---------------------------------------");
    }
}