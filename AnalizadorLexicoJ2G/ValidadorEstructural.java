package AnalizadorLexicoJ2G;

import AnalizadorSintacticoJ2G.LRParser; // --- CORRECCIÓN DE IMPORTACIÓN ---
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet; // --- NUEVO ---
import java.util.List;
import java.util.Map;
import java.util.Set; // --- NUEVO ---
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidadorEstructural {

    public TablaSimbolos tablaSimbolosGlobal;
    private AnalizadorLexicoCore analizadorLexico;
    private PrintStream outputTables;

    // --- INICIO DE NUEVOS ATRIBUTOS PARA GENERACIÓN ASM ---
    private PrintStream outputAsm;
    // --- ATRIBUTOS GLOBALES ELIMINADOS ---
    // private StringBuilder asmDatosGlobal;
    // private StringBuilder asmCodigoGlobal;
    private String baseAsmTemplate;
    private Map<String, String> reglasRegexCache; // Cache para las regex
    // --- FIN DE NUEVOS ATRIBUTOS ---

    private boolean currentlyInSwitchBlock = false;
    private int switchBlockEntryDepth = 0;
    private boolean currentSwitchClauseActiveAndNeedsDetener = false;
    private boolean currentSwitchClauseHasHadDetener = false;
    private int lineOfCurrentSwitchClauseStart = 0;
    private String contentOfCurrentSwitchClauseStart = "";
    private int switchActualOpeningLine = 0;
    private String currentSwitchExpressionType = null;

    // --- MODIFICACIÓN: Constructor actualizado para ASM ---
    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal, AnalizadorLexicoCore analizadorLexico, PrintStream outputTables, PrintStream outputAsm) {
        this.tablaSimbolosGlobal = tablaSimbolosGlobal;
        this.analizadorLexico = analizadorLexico;
        this.outputTables = outputTables;
        // --- Inicializar nuevos atributos ---
        this.outputAsm = outputAsm;
        // --- ELIMINADOS ---
        // this.asmDatosGlobal = new StringBuilder();
        // this.asmCodigoGlobal = new StringBuilder();
        this.baseAsmTemplate = J2GAnalizadorApp.leerArchivo("J2G/base.asm");
        this.reglasRegexCache = cargarReglasRegexEnMemoria(); // Cargar regex una vez
    }
    
    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal, AnalizadorLexicoCore analizadorLexico, PrintStream outputTables) {
        this(tablaSimbolosGlobal, analizadorLexico, outputTables, null); // Constructor anterior llama al nuevo
    }

    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal) {
        this(tablaSimbolosGlobal, new AnalizadorLexicoCore(tablaSimbolosGlobal), System.out, null);
    }
    
    // --- MÉTODO ELIMINADO ---
    // public void escribirAsmFinal(TablaSimbolos tablaSimbolos) { ... }
    
    // --- INICIO DE NUEVO MÉTODO ---
    /**
     * Genera un bloque de código ensamblador completo para una expresión específica
     * y lo escribe en el archivo de salida ASM.
     * @param expresionOriginal La cadena de texto de la expresión (ej: "b * c + d")
     * @param parser El parser que acaba de analizar la expresión.
     */
    private void generarAsmParaExpresion(String expresionOriginal, LRParser parser) {
        if (this.outputAsm == null) {
             System.err.println("Advertencia: No se proporcionó un PrintStream de salida para ASM. No se generará el archivo.");
            return; // No escribir si no hay stream
        }
        if (this.baseAsmTemplate == null || this.baseAsmTemplate.isEmpty()) {
            System.err.println("Error: Plantilla base.asm no cargada o vacía. No se puede generar ASM.");
            return;
        }

        Set<String> idsUsados = parser.getIdsEncontrados();
        String asmDatosTemporales = parser.getAsmDatos();
        String asmCodigoExpresion = parser.getAsmCodigo();
        String temporalFinal = parser.getFinalTemporalResult();

        StringBuilder variablesComentario = new StringBuilder();
        StringBuilder datosSegmento = new StringBuilder();
        datosSegmento.append("\t; Variables de la expresion\n");
        
        Set<String> variablesDeclaradasEnDatos = new HashSet<>(); // Para no duplicar IDs

        for (String id : idsUsados) {
            SymbolTableEntry entry = this.tablaSimbolosGlobal.findEntryById(id);
            if (entry != null) {
                // 1. Añadir al comentario de variables
                String valorLegible = entry.valor;
                SymbolTableEntry valorEntry = this.tablaSimbolosGlobal.findEntryById(entry.valor);
                if (valorEntry != null) {
                    valorLegible = valorEntry.variable; // Resuelve "id2" a "10"
                }
                
                // Añadir al comentario solo si es una variable de usuario (ej: 'a'), no un literal (ej: '10')
                if (entry.variable.matches(reglasRegexCache.get("VAR_NAME"))) { 
                    variablesComentario.append(String.format("%s (%s): %s\n", entry.variable, entry.id, valorLegible));
                }

                // 2. Añadir a la sección .data (tanto variables como literales)
                if (entry.tipo.equals("int") || entry.tipo.equals("bool")) {
                    String valorAsm = "?";
                    if (entry.variable.matches(reglasRegexCache.get("VAR_NAME"))) { // Es 'a'
                         if (valorEntry != null) {
                             valorAsm = valorEntry.variable; // "10"
                         } else {
                             valorAsm = entry.valor; // Fallback
                         }
                    } else { // Es un literal '10'
                        valorAsm = entry.variable; // "10"
                    }

                    if (valorAsm.equalsIgnoreCase("TRUE")) valorAsm = "1";
                    else if (valorAsm.equalsIgnoreCase("FALSE")) valorAsm = "0";

                    // Escribir la definición 'idX dw Y' si es un número válido y no se ha declarado ya
                    if (valorAsm.matches("-?[0-9]+") && !variablesDeclaradasEnDatos.contains(entry.id)) {
                         datosSegmento.append(String.format("\t%s dw %s \t; %s\n", entry.id, valorAsm, entry.variable));
                         variablesDeclaradasEnDatos.add(entry.id);
                    }
                }
            }
        }

        datosSegmento.append("\n\t; Temporales (segun traza de tablas_analisis.txt)\n");
        datosSegmento.append(asmDatosTemporales);

        // Inyectar en la plantilla
        String asmFinal = this.baseAsmTemplate;
        
        asmFinal = asmFinal.replace("datos segment para public 'data'",
                                  "datos segment para public 'data'\n" + datosSegmento.toString());
        
        asmFinal = asmFinal.replace(";codigo",
                                  ";--- Inicio del codigo de la expresion ---\n\n" 
                                  + asmCodigoExpresion 
                                  + ";--- Fin del codigo de la expresion ---\n"
                                  + "; El resultado booleano final esta en " + temporalFinal + "\n");
        
        // Escribir en el archivo de salida
        this.outputAsm.println("Expresion o condicion:");
        this.outputAsm.println(expresionOriginal.replaceAll("\\s+", " ").trim());
        this.outputAsm.println("Variables:");
        this.outputAsm.print(variablesComentario.toString()); // print, no println
        this.outputAsm.println(asmFinal);
        this.outputAsm.println("----------------------------------------------------");
    }
    // --- FIN DE NUEVO MÉTODO ---

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

    private List<String> getExpressionTokens(String expression) {
        String cleanedExpression = expression.replaceAll("/\\*[\\s\\S]*?\\*/", "").replaceAll("//.*", "").trim();
        List<String> rawTokens = this.analizadorLexico.fase1_limpiarYTokenizar(cleanedExpression);
        List<String> transformedTokens = this.analizadorLexico.fase2_transformarTokens(rawTokens);
        transformedTokens.add("$");
        return transformedTokens;
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
        if (expression == null) return "UNKNOWN";
        String trimmedExpression = expression.trim();

        if (trimmedExpression.matches(reglas.get("STRING_LITERAL"))) return "string";
        if (trimmedExpression.matches(reglas.get("NUMBER_LITERAL"))) return "int";
        if (trimmedExpression.matches(reglas.get("BOOLEAN_LITERAL"))) return "bool";
        
        String mappedType = declaredVariablesTypeMap.get(trimmedExpression);
        if (mappedType != null) {
            if (mappedType.equalsIgnoreCase("STR")) return "string";
            if (mappedType.equalsIgnoreCase("INT")) return "int";
            if (mappedType.equalsIgnoreCase("BOOL")) return "bool";
        }

        if (trimmedExpression.matches("^Input\\s*\\(\\s*\\)\\s*\\.\\s*Str\\s*\\(\\s*\\)$")) return "string";
        if (trimmedExpression.matches("^Input\\s*\\(\\s*\\)\\s*\\.\\s*Int\\s*\\(\\s*\\)$")) return "int";
        if (trimmedExpression.matches("^Input\\s*\\(\\s*\\)\\s*\\.\\s*Bool\\s*\\(\\s*\\)$")) return "bool";
        
        return "UNKNOWN";
    }

    private void performAssignmentTypeChecks(String lhsVar, String op, String rhsExpression, String context,
            Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap,
            List<String> errorsEnLinea) {
        if (declaredVariablesTypeMap.containsKey(lhsVar)) {
            String lhsType = declaredVariablesTypeMap.get(lhsVar).toLowerCase();
            if (lhsType.equals("str")) lhsType = "string";

            String rhsType = getExpressionType(rhsExpression, reglas, declaredVariablesTypeMap);

            if (op.equals(":=")) {
                if (rhsType.equals("UNKNOWN")) {
                    if (rhsExpression.matches("^Input\\s*\\(\\s*\\)$")) {
                        errorsEnLinea.add(
                                "Error de sintaxis: Falta concatenar tipo de dato al Input (ej: Input().Int(), Input().Str()) en la asignación a '"
                                        + lhsVar + "'. Contexto: " + context + ".");
                    } else if (rhsExpression.startsWith("Input")) {
                        errorsEnLinea.add("Error: La estructura del Input ('" + rhsExpression
                                + "') no es la adecuada o está incompleta para la asignación a '" + lhsVar
                                + "'. Verifique la sintaxis (ej: Input().Int();). Contexto: " + context + ".");
                    } else {
                        // No agregar error aquí si es una expresión compleja, el parser lo validará
                        // errorsEnLinea.add("Error de Tipo: No se pudo determinar el tipo de la expresión " + rhsExpression + "' para la asignación a '" + lhsVar + "'. Contexto: " + context + ".");
                    }
                } else if (!lhsType.equals(rhsType)) {
                     if (!context.contains("inicialización de variable")) {
                        errorsEnLinea.add("Error de Tipo: No se puede asignar un valor de tipo " + rhsType +
                                " a la variable '" + lhsVar + "' (tipo " + lhsType + ") " + context + ".");
                    }
                }

            } else if (op.matches("\\+=|-=|\\*=|\\/=")) {
                if (!lhsType.equals("int")) {
                    errorsEnLinea.add("Error de tipo: Operador de asignación compuesta '" + op +
                            "' requiere que la variable '" + lhsVar + "' sea de tipo INT, pero es " + lhsType + " "
                            + context + ".");
                }
                if (rhsType.equals("UNKNOWN")) {
                    if (rhsExpression.startsWith("Input")) {
                        errorsEnLinea.add("Error: No se puede usar una expresión Input ('" + rhsExpression
                                + "') directamente con el operador de asignación compuesta '" + op
                                + "'. Se espera un valor de tipo INT. Contexto: " + context + ".");
                    } else {
                         // No agregar error aquí si es una expresión compleja, el parser lo validará
                        // errorsEnLinea.add("Error de tipo: No se pudo determinar el tipo de la expresión '"
                        //         + rhsExpression + "' para el operador de asignación compuesta '" + op
                        //         + "'. Se espera un valor de tipo INT. Contexto: " + context + ".");
                    }
                } else if (!rhsType.equals("int")) {
                    errorsEnLinea.add("Error de tipo: Operador de asignación compuesta '" + op +
                            "' requiere que el valor a la derecha ('" + rhsExpression + "') sea de tipo INT, pero es "
                            + rhsType + " " + context + ".");
                }
            }
        }
    }

    public void validarEstructuraConRegex(String codigoLimpioFormateado, LRParser parser) {
        // --- MODIFICACIÓN: Usar cache de regex ---
        Map<String, String> reglas = this.reglasRegexCache; 
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
                    lineBraceBalance++;
                } else if (ch == '}') {
                    lineBraceBalance--;
                }
            }
            
            int globalBraceBalanceBeforeLine = globalBraceBalance;
            globalBraceBalance += lineBraceBalance;

            if (globalBraceBalanceBeforeLine + lineBraceBalance < 0 && lineBraceBalance < 0
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
                mainFunctionBlockBraceDepth = globalBraceBalance;
                reglaCoincidioEstaLinea = true;
            } else if (currentlyInMainFunctionBlock) {
                
                if (lineaActual.matches(reglas.getOrDefault("SWITCH_STMT", "^$"))) {
                    reglaCoincidioEstaLinea = true;
                    this.currentlyInSwitchBlock = true;
                    this.switchBlockEntryDepth = globalBraceBalance;
                    this.currentSwitchClauseActiveAndNeedsDetener = false;
                    this.currentSwitchClauseHasHadDetener = false;
                    this.switchActualOpeningLine = currentLineNumber;
                    this.currentSwitchExpressionType = null;

                    Matcher switchMatcher = Pattern.compile(reglas.get("SWITCH_STMT")).matcher(lineaActual);
                    if (switchMatcher.matches()) {
                        String switchExpressionText = switchMatcher.group(1).trim();
                        List<String> expressionTokens = getExpressionTokens(switchExpressionText);
                        outputTables.println("Análisis Sintáctico de la expresión del switch: '" + switchExpressionText + "'");
                        boolean sintaxisValida = parser.parse(expressionTokens);
                        if (!sintaxisValida) {
                             errorsEnLinea.add("Error de sintaxis en la expresión del switch: '" + switchExpressionText + "'.");
                        } else {
                            // --- NUEVO CÓDIGO ASM ---
                            generarAsmParaExpresion(switchExpressionText, parser);
                        }
                        
                        this.currentSwitchExpressionType = getExpressionType(switchExpressionText, reglas, declaredVariablesTypeMap);
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
                        reglaCoincidioEstaLinea = true;
                        if (globalBraceBalance == this.switchBlockEntryDepth - 1) {
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
                                
                                List<String> expressionTokens = getExpressionTokens(rhsAssigned);
                                outputTables.println("Análisis Sintáctico de la expresión de inicialización del for: '" + rhsAssigned + "'");
                                if (!parser.parse(expressionTokens)) {
                                     errorsEnLinea.add("Error de sintaxis en la expresión de inicialización del for: '" + rhsAssigned + "'.");
                                } else {
                                    // --- NUEVO CÓDIGO ASM ---
                                    generarAsmParaExpresion(initPart, parser);
                                }

                                performAssignmentTypeChecks(varName, ":=", rhsAssigned, contextFor, reglas,
                                        declaredVariablesTypeMap, errorsEnLinea);
                            } else if (initAssignMatcher.matches()) {
                                String lhsVar = initAssignMatcher.group(1);
                                String op = initAssignMatcher.group(2);
                                String rhsEx = initAssignMatcher.group(3).trim();
                                errorsEnLinea.addAll(checkVariableUsage(lhsVar, contextFor + " (LHS asignación)",
                                        reglas, declaredVariablesTypeMap));
                                
                                List<String> expressionTokens = getExpressionTokens(rhsEx);
                                outputTables.println("Análisis Sintáctico de la expresión de asignación del for: '" + rhsEx + "'");
                                if (!parser.parse(expressionTokens)) {
                                     errorsEnLinea.add("Error de sintaxis en la expresión de asignación del for: '" + rhsEx + "'.");
                                } else {
                                     // --- NUEVO CÓDIGO ASM ---
                                    generarAsmParaExpresion(initPart, parser);
                                }
                                
                                performAssignmentTypeChecks(lhsVar, op, rhsEx, contextFor, reglas,
                                        declaredVariablesTypeMap, errorsEnLinea);
                            } else {
                                errorsEnLinea.add("Error de sintaxis: Parte de inicialización del for ('" + initPart
                                        + "') no es una declaración (ej: INT i := 0) o una asignación válida.");
                            }
                        }

                        contextFor = "en condición de for";
                        if (!conditionPart.isEmpty()) {
                            List<String> expressionTokens = getExpressionTokens(conditionPart);
                            outputTables.println("Análisis Sintáctico de la expresión de condición del for: '" + conditionPart + "'");
                            if (!parser.parse(expressionTokens)) {
                                errorsEnLinea.add("Error de sintaxis en la expresión de condición del for: '" + conditionPart + "'.");
                            } else {
                                // --- NUEVO CÓDIGO ASM ---
                                generarAsmParaExpresion(conditionPart, parser);
                            }
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
                                
                                List<String> expressionTokens = getExpressionTokens(rhsEx);
                                outputTables.println("Análisis Sintáctico de la expresión de actualización del for: '" + rhsEx + "'");
                                if (!parser.parse(expressionTokens)) {
                                     errorsEnLinea.add("Error de sintaxis en la expresión de actualización del for: '" + rhsEx + "'.");
                                } else {
                                    // --- NUEVO CÓDIGO ASM ---
                                    generarAsmParaExpresion(updatePart, parser);
                                }
                                
                                performAssignmentTypeChecks(lhsVar, op, rhsEx, contextFor, reglas,
                                        declaredVariablesTypeMap, errorsEnLinea);
                            } else {
                                errorsEnLinea.add("Error de sintaxis: Parte de actualización del for ('" + updatePart
                                        + "') no es una asignación válida (ej: i += 1).");
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

                        if (declaredVariablesTypeMap.containsKey(varName)) {
                            errorsEnLinea.add("Error Semántico: Variable '" + varName + "' ya declarada (redefinición).");
                        } else {
                            String rhsType = getExpressionType(rhsAssigned, reglas, declaredVariablesTypeMap);
                            String varDeclaredTypeLower = varDeclaredType.toLowerCase();
                            if(varDeclaredTypeLower.equals("str")) varDeclaredTypeLower = "string";

                            if (!rhsType.equals("UNKNOWN") && !varDeclaredTypeLower.equals(rhsType)) {
                                System.err.println("ADVERTENCIA: Se ignoró la asignación para la variable '" + varName +
                                                  "' por incompatibilidad de tipos. Se esperaba " + varDeclaredType +
                                                  " pero se encontró " + rhsType + ". La variable quedará solo declarada.");

                                SymbolTableEntry varEntry = this.tablaSimbolosGlobal.findVariableEntry(varName);
                                if (varEntry != null) {
                                    String incorrectLiteralId = varEntry.valor;
                                    SymbolTableEntry literalEntry = this.tablaSimbolosGlobal.findEntryById(incorrectLiteralId);
                                    
                                    varEntry.valor = varEntry.variable;
                                    varEntry.tipo = varDeclaredTypeLower;
                                    
                                    if (literalEntry != null) {
                                        this.tablaSimbolosGlobal.removeNewVariableEntry(literalEntry);
                                    }
                                }
                                declaredVariablesTypeMap.put(varName, varDeclaredType);
                            } else {
                                declaredVariablesTypeMap.put(varName, varDeclaredType);
                                errorsEnLinea.addAll(checkVariableUsage(varName, "en declaración (nombre)", reglas, declaredVariablesTypeMap));
                                
                                List<String> expressionTokens = getExpressionTokens(rhsAssigned);
                                outputTables.println("Análisis Sintáctico de la expresión de inicialización: '" + rhsAssigned + "'");
                                if (!parser.parse(expressionTokens)) {
                                     errorsEnLinea.add("Error de sintaxis en la expresión de inicialización: '" + rhsAssigned + "'.");
                                } else {
                                    // --- NUEVO CÓDIGO ASM ---
                                    // Solo generamos ASM para expresiones complejas, no para
                                    // asignaciones literales simples, para seguir el ejemplo.
                                    // generarAsmParaExpresion(rhsAssigned, parser);
                                }
                                
                                performAssignmentTypeChecks(varName, ":=", rhsAssigned, "en inicialización de variable", reglas, declaredVariablesTypeMap, errorsEnLinea);
                            }
                        }
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
                        
                        List<String> expressionTokens = getExpressionTokens(rhsExpression);
                        outputTables.println("Análisis Sintáctico de la expresión de asignación: '" + rhsExpression + "'");
                        if (!parser.parse(expressionTokens)) {
                             errorsEnLinea.add("Error de sintaxis en la expresión de asignación: '" + rhsExpression + "'.");
                        } else {
                            // --- NUEVO CÓDIGO ASM ---
                            // Generar ASM para asignaciones si se desea
                            // generarAsmParaExpresion(rhsExpression, parser);
                        }
                        
                        performAssignmentTypeChecks(lhsVar, op, rhsExpression, "en asignación", reglas,
                                declaredVariablesTypeMap, errorsEnLinea);
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    String[] cStructs = { "IF_STMT", "WHILE_STMT", "DO_WHILE_TAIL_ONLY_STMT", "PRINT_STMT",
                            "DO_WHILE_CLOSURE_LINE_STMT", "ELSE_STMT",
                            "BLOCK_END_ELSE_STMT", "DO_WHILE_DO_STMT", "BLOCK_END" };
                    for (String key : cStructs) {
                        matcher = Pattern.compile(reglas.getOrDefault(key, "^$")).matcher(lineaActual);
                        if (matcher.matches()) {
                            reglaCoincidioEstaLinea = true;
                            String contextMsg = "en " + key.replace("_STMT", "").toLowerCase();

                            if (key.equals("IF_STMT") || key.equals("WHILE_STMT") ||
                                    key.equals("DO_WHILE_CLOSURE_LINE_STMT") || key.equals("DO_WHILE_TAIL_ONLY_STMT")) {
                                String expression = matcher.group(1);
                                List<String> expressionTokens = getExpressionTokens(expression);
                                outputTables.println("Análisis Sintáctico de la expresión de la condición: '" + expression + "'");
                                if (!parser.parse(expressionTokens)) {
                                    errorsEnLinea.add("Error de sintaxis en la expresión de la condición: '" + expression + "'.");
                                } else {
                                    // --- MODIFICACIÓN: Llamar al nuevo método de generación ---
                                    generarAsmParaExpresion(expression, parser);
                                }
                                
                            } else if (key.equals("PRINT_STMT")) {
                                String expression = matcher.group(1);
                                if (!expression.trim().isEmpty()) {
                                    List<String> expressionTokens = getExpressionTokens(expression);
                                    outputTables.println("Análisis Sintáctico del argumento del print: '" + expression + "'");
                                    if (!parser.parse(expressionTokens)) {
                                        errorsEnLinea.add("Error de sintaxis en el argumento del print: '" + expression + "'.");
                                    } else {
                                        // (Opcional: generar ASM para el argumento del print)
                                        // generarAsmParaExpresion(expression, parser);
                                    }
                                }
                            } else {
                                if (!key.equals("ELSE_STMT") && !key.equals("BLOCK_END_ELSE_STMT")
                                        && !key.equals("DO_WHILE_DO_STMT") && !key.equals("BLOCK_END")) {
                                    errorsEnLinea
                                            .addAll(checkBalancedSymbols(lineaActual, currentLineNumber, contextMsg));
                                }
                            }
                            break;
                        }
                    }
                }
                
                mainFunctionBlockBraceDepth = globalBraceBalance;
                if (mainFunctionBlockBraceDepth == 0 && currentlyInMainFunctionBlock) {
                    currentlyInMainFunctionBlock = false;
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
                System.err.println("Error(es) en línea " + currentLineNumber + ": " + lineasCodigo[i]);
                for (String error : errorsEnLinea)
                    System.err.println("  - " + error);
                erroresEncontradosEnGeneral = true;
            }
        }

        if (!mainFunctionDeclared) {
            globalStructuralErrors
                    .add("Error ESTRUCTURAL GLOBAL: No se encontró la función principal 'FUNC J2G Main() {}'.");
        } else if (globalBraceBalance != 0) { 
            if(globalBraceBalance > 0){
                globalStructuralErrors
                        .add("Error ESTRUCTURAL GLOBAL: El bloque 'FUNC J2G Main()' no se cerró correctamente (faltan "
                                + globalBraceBalance + " llave(s) de cierre '}').");
            } else {
                 globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Hay " + (-globalBraceBalance)
                            + " llave(s) de cierre '}' extra o mal colocadas en el programa.");
            }
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

        if (!globalStructuralErrors.isEmpty()) {
            System.err.println("\n--- Errores Estructurales Globales Detectados ---");
            for (String error : globalStructuralErrors)
                System.err.println(error);
            erroresEncontradosEnGeneral = true;
        }

        if (!erroresEncontradosEnGeneral && globalStructuralErrors.isEmpty()) {
            System.err.println("No se encontraron errores de estructura.");
        }
        System.err.println("---------------------------------------");
    }
}