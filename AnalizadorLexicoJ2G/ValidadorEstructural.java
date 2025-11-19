package AnalizadorLexicoJ2G;

import AnalizadorSintacticoJ2G.LRParser;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidadorEstructural {

    public TablaSimbolos tablaSimbolosGlobal;
    private AnalizadorLexicoCore analizadorLexico;
    private PrintStream outputTables;

    // --- ATRIBUTOS ASM ---
    private String archivoAsmPath;
    private StringBuilder asmDatosGlobal;
    private StringBuilder asmCodigoGlobal;
    private StringBuilder asmProcedimientos;
    private Set<String> allIdsUsadosGlobal;
    private Set<String> allTemporalesUsadosGlobal;
    // ELIMINADO: private Map<String, String> stringLiteralToAsmLabel; 
    // ELIMINADO: private int stringLabelCounter = 1;
    private int labelCounter = 1;
    private Stack<String> controlFlowStack;
    private String baseAsmTemplate;
    private Map<String, String> reglasRegexCache;
    
    // Control de supresión de código por errores
    private int suppressAsmDepth = -1; 

    // --- ATRIBUTOS VALIDACIÓN ---
    private boolean currentlyInSwitchBlock = false;
    private int switchBlockEntryDepth = 0;
    private boolean currentSwitchClauseActiveAndNeedsDetener = false;
    private boolean currentSwitchClauseHasHadDetener = false;
    private int lineOfCurrentSwitchClauseStart = 0;
    private String contentOfCurrentSwitchClauseStart = "";
    private int switchActualOpeningLine = 0;
    private String currentSwitchExpressionType = null;

    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal, AnalizadorLexicoCore analizadorLexico, PrintStream outputTables, String archivoAsmPath) {
        this.tablaSimbolosGlobal = tablaSimbolosGlobal;
        this.analizadorLexico = analizadorLexico;
        this.outputTables = outputTables;
        
        this.archivoAsmPath = archivoAsmPath;
        this.asmDatosGlobal = new StringBuilder();
        this.asmCodigoGlobal = new StringBuilder();
        this.asmProcedimientos = new StringBuilder();
        this.allIdsUsadosGlobal = new LinkedHashSet<>();
        this.allTemporalesUsadosGlobal = new LinkedHashSet<>();
        this.controlFlowStack = new Stack<>();
        
        this.baseAsmTemplate = J2GAnalizadorApp.leerArchivo("J2G/base.asm");
        this.reglasRegexCache = cargarReglasRegexEnMemoria();
        
        cargarProcedimientosYMacros();
    }
    
    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal, AnalizadorLexicoCore analizadorLexico, PrintStream outputTables) {
        this(tablaSimbolosGlobal, analizadorLexico, outputTables, null);
    }

    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal) {
        this(tablaSimbolosGlobal, new AnalizadorLexicoCore(tablaSimbolosGlobal), System.out, null);
    }
    
    // --- MÉTODOS DE GENERACIÓN ASM ---

    private void cargarProcedimientosYMacros() {
        asmProcedimientos.append("\n; --- INICIO MACROS ---\n");
        asmProcedimientos.append(
            "IMPRIMIR_CADENA_DX MACRO\n" +
            "    LOCAL imprimir_proc_call_site_macro\n" +
            "    push ax\n" +
            "    push dx\n" +
            "    call PROC_ImprimirCadenaDX\n" +
            "    pop dx\n" +
            "    pop ax\n" +
            "ENDM\n\n"
        );
        asmProcedimientos.append(
            "IMPRIMIR_MSG MACRO msg_label\n" +
            "    LOCAL print_msg_site_macro\n" +
            "    push dx\n" +
            "    lea dx, msg_label\n" +
            "    IMPRIMIR_CADENA_DX\n" +
            "    pop dx\n" +
            "ENDM\n\n"
        );
        asmProcedimientos.append(
            "SALTO_LINEA MACRO\n" +
            "    IMPRIMIR_MSG saltoLinea_msg\n" + 
            "ENDM\n\n"
        );
        asmProcedimientos.append("; --- FIN MACROS ---\n\n");
        
        asmProcedimientos.append("\n; --- INICIO PROCEDIMIENTOS ---\n");
        asmProcedimientos.append(
            "PROC_ImprimirCadenaDX PROC FAR\n" +
            "    push ax\n" +
            "	mov ah, 09h\n" +
            "	int 21h\n" +
            "    pop ax\n" +
            "	retf\n" +
            "PROC_ImprimirCadenaDX ENDP\n\n"
        );

        asmProcedimientos.append(
            "PROC_CapturarNumeroDecimal PROC FAR\n" +
            "    push bp\n" +
            "    mov bp, sp\n" +
            "    push bx\n" +
            "    push cx\n" +
            "    push dx\n" +
            "    push si\n" +
            "\n" +
            "    xor bx, bx\n" +
            "\n" +
            "input_digit_loop_cap_local_pcnd_procs:\n" +
            "    mov ah, 01h\n" +
            "    int 21h\n" +
            "\n" +
            "    cmp al, 0Dh\n" +
            "    je input_done_cap_local_pcnd_procs\n" +
            "\n" +
            "    cmp al, '0'\n" +
            "    jl input_digit_loop_cap_local_pcnd_procs\n" +
            "    cmp al, '9'\n" +
            "    jg input_digit_loop_cap_local_pcnd_procs\n" +
            "\n" +
            "    sub al, '0'\n" +
            "    mov ah, 0\n" +
            "\n" +
            "    push ax\n" +
            "    mov ax, bx\n" +
            "    mov si, 10\n" +
            "    mul si\n" +
            "    mov bx, ax\n" +
            "    pop ax\n" +
            "    add bx, ax\n" +
            "\n" +
            "    jmp input_digit_loop_cap_local_pcnd_procs\n" +
            "\n" +
            "input_done_cap_local_pcnd_procs:\n" +
            "    mov ax, bx\n" +
            "    clc\n" +
            "\n" +
            "    pop si\n" +
            "    pop dx\n" +
            "    pop cx\n" +
            "    pop bx\n" +
            "    pop bp\n" +
            "    retf\n" +
            "PROC_CapturarNumeroDecimal ENDP\n\n"
        );
        // PROC_MostrarNumeroDecimal eliminado por ser código muerto en tu ejemplo
        asmProcedimientos.append("; --- FIN PROCEDIMIENTOS ---\n");
    }

    public void escribirAsmFinal(TablaSimbolos tablaSimbolos) throws IOException {
        if (this.archivoAsmPath == null || this.baseAsmTemplate == null) {
            return; 
        }

        StringBuilder datosSegmento = new StringBuilder();
        
        datosSegmento.append("\t; --- Mensajes y Literales ---\n");
        datosSegmento.append("\tsaltoLinea_msg db 0Dh, 0Ah, '$'\n");
        // ELIMINADO: Bucle que escribía msg1, msg2 duplicados.
        // Ahora los mensajes se escriben abajo como parte de los IDs del programa.

        datosSegmento.append("\n\t; --- Variables y Literales del Programa ---\n");
        for (String id : allIdsUsadosGlobal) {
            SymbolTableEntry entry = tablaSimbolos.findEntryById(id);
            if (entry != null) {
                String comentario = entry.variable;

                if (entry.tipo.equals("int") || entry.tipo.equals("bool")) {
                    String valorAsm = "?";
                    if (entry.variable.matches(reglasRegexCache.get("VAR_NAME"))) { 
                        SymbolTableEntry valorEntry = tablaSimbolos.findEntryById(entry.valor);
                        if (valorEntry != null) {
                             valorAsm = valorEntry.variable; 
                        } else if (entry.valor.matches("-?[0-9]+")) {
                             valorAsm = entry.valor; 
                        }
                    } else { 
                        valorAsm = entry.variable; 
                    }

                    if (valorAsm.equalsIgnoreCase("TRUE")) valorAsm = "1";
                    else if (valorAsm.equalsIgnoreCase("FALSE")) valorAsm = "0";
                    
                    if (valorAsm.matches("-?[0-9]+")) {
                         datosSegmento.append(String.format("\t%s dw %s \t; %s\n", entry.id, valorAsm, comentario));
                    }
                } else if (entry.tipo.equalsIgnoreCase("string") || entry.tipo.equalsIgnoreCase("str")) {
                    if (entry.variable.startsWith("\"")) {
                         // Es un literal de string (ej: "hola mundo") -> Definir bytes
                         String cleanVal = entry.variable.substring(1, entry.variable.length()-1);
                         // Usamos el ID del literal directamente
                         datosSegmento.append(String.format("\t%s db \"%s\", '$' \t; %s\n", entry.id, cleanVal, comentario));
                    } else {
                         // Es una variable de string (ej: mensaje) -> Definir palabra (puntero)
                         datosSegmento.append(String.format("\t%s dw ? \t; %s\n", entry.id, comentario));
                    }
                }
            }
        }

        datosSegmento.append("\n\t; --- Temporales ---\n");
        for (String temporal : allTemporalesUsadosGlobal) {
            datosSegmento.append(String.format("\t%s dw ?\n", temporal));
        }
        
        String asmFinal = this.baseAsmTemplate;
        asmFinal = asmFinal.replace("datos segment para public 'data'",
                                  "datos segment para public 'data'\n" + datosSegmento.toString() + asmDatosGlobal.toString());
        
        asmFinal = asmFinal.replace(";codigo",
                                  ";--- Inicio del codigo principal ---\n\n" 
                                  + asmCodigoGlobal.toString() 
                                  + "\n;--- Fin del codigo principal ---\n"
                                  + "\n\n" + asmProcedimientos.toString());
        
        try (PrintWriter out = new PrintWriter(new FileWriter(this.archivoAsmPath))) {
            out.print(asmFinal);
        }
    }
    
    private boolean procesarYAcumularExpresion(String expresion, String context, LRParser parser) {
        List<String> expressionTokens = getExpressionTokens(expresion);
        outputTables.println("Análisis Sintáctico (" + context + "): '" + expresion + "'");
        
        boolean sintaxisYSemanticaValida = parser.parse(expressionTokens);
        
        if (sintaxisYSemanticaValida) {
            asmCodigoGlobal.append(parser.getAsmCodigo());
            allIdsUsadosGlobal.addAll(parser.getIdsEncontrados());
            allTemporalesUsadosGlobal.addAll(parser.getTemporalesDeclarados());
        }
        return sintaxisYSemanticaValida;
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
        reglas.put("INPUT_STR_STMT", "^(?:" + varOrIdPatternCore + "\\s*:=\\s*)?Input\\s*\\(\\s*\\)\\s*" + optionalPromptRegex + "\\s*\\.\\s*Str\\s*\\(\\s*\\)\\s*;");
        reglas.put("INPUT_INT_STMT", "^(?:" + varOrIdPatternCore + "\\s*:=\\s*)?Input\\s*\\(\\s*\\)\\s*" + optionalPromptRegex + "\\s*\\.\\s*Int\\s*\\(\\s*\\)\\s*;");
        reglas.put("INPUT_BOOL_STMT", "^(?:" + varOrIdPatternCore + "\\s*:=\\s*)?Input\\s*\\(\\s*\\)\\s*" + optionalPromptRegex + "\\s*\\.\\s*Bool\\s*\\(\\s*\\)\\s*;");
        reglas.put("IF_STMT", "^if\\s*\\((.+)\\)\\s*\\{");
        reglas.put("ELSE_STMT", "^else\\s*\\{");
        reglas.put("BLOCK_END_ELSE_STMT", "^\\}\\s*else\\s*\\{");
        reglas.put("SWITCH_STMT", "^sw\\s*\\((.+)\\)\\s*\\{");
        reglas.put("CASE_STMT", "^caso\\s+" + anyLiteralOrVarRegex + "\\s*:");
        reglas.put("DEFAULT_STMT", "^por_defecto\\s*:");
        reglas.put("DETENER_STMT", "^detener\\s*;");
        reglas.put("FOR_STMT", "^for\\s*\\((.*?)\\s*;\\s*(.*?)\\s*;\\s*(.*?)\\)\\s*\\{");
        reglas.put("WHILE_STMT", "^while\\s*\\((.+)\\)\\s*\\{");
        reglas.put("DO_WHILE_DO_STMT", "^do\\s*\\{");
        reglas.put("DO_WHILE_CLOSURE_LINE_STMT", "^\\}\\s*while\\s*\\((.+)\\)\\s*;");
        reglas.put("DO_WHILE_TAIL_ONLY_STMT", "^while\\s*\\((.+)\\)\\s*;");
        reglas.put("PRINT_STMT", "^Print\\s*\\((.*?)\\)\\s*;");

        return reglas;
    }

    // ... (Métodos auxiliares getMatchingClosingSymbol, checkBalancedSymbols etc., sin cambios) ...
    
    private char getMatchingClosingSymbol(char openSymbol) {
        if (openSymbol == '(') return ')';
        if (openSymbol == '{') return '}';
        if (openSymbol == '[') return ']';
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
                    errors.add("Error de sintaxis: Símbolo de cierre '" + c + "' inesperado en columna " + (i + 1) + " (no hay símbolo de apertura correspondiente) " + contextDesc + ".");
                } else if (stack.peek() != symbolPairs.get(c)) {
                    errors.add("Error de sintaxis: Símbolo de cierre '" + c + "' en columna " + (i + 1) + " no coincide con el símbolo de apertura esperado '" + getMatchingClosingSymbol(stack.peek()) + "' (abierto en columna " + (positionStack.peek() + 1) + ") " + contextDesc + ".");
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
            errors.add("Error de sintaxis: Falta símbolo de cierre '" + expectedClosing + "' para '" + openSymbol + "' abierto en columna " + (openPos + 1) + " " + contextDesc + ".");
        }
        return errors;
    }

    private boolean isValidSourceVarName(String name, Map<String, String> reglas) {
        return name.matches(reglas.getOrDefault("VAR_NAME", "^[a-z][a-zA-Z0-9_]{0,63}$"));
    }

    private boolean isGeneratedIdName(String name, Map<String, String> reglas) {
        return name.matches(reglas.getOrDefault("ID_NAME", "^id[0-9]+$"));
    }

    private List<String> checkVariableUsage(String varName, String context, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap) {
        List<String> errors = new ArrayList<>();
        boolean isKeyword = this.tablaSimbolosGlobal.getPalabrasReservadasYSimbolosConocidos().contains(varName) && !varName.equalsIgnoreCase("TRUE") && !varName.equalsIgnoreCase("FALSE");
        if (isKeyword) {
            if (!((context.contains("Input") && (varName.equals("Int") || varName.equals("Str") || varName.equals("Bool"))) || (context.contains("tipo_dato") && (varName.equals("INT") || varName.equals("STR") || varName.equals("BOOL"))))) {
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

    private List<String> checkExpressionVariables(String expression, String contextForExpression, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap, int lineNumber) {
        List<String> errors = new ArrayList<>();
        if (expression == null || expression.trim().isEmpty()) return errors;

        errors.addAll(checkBalancedSymbols(expression, lineNumber, "en la expresión '" + expression + "' " + contextForExpression));

        List<String> localTabsimTokens = new ArrayList<>(this.tablaSimbolosGlobal.getPalabrasReservadasYSimbolosConocidos());
        localTabsimTokens.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));

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

            if (matcher.group(identifierGroupIndex) != null) matchedIdentifier = matcher.group(identifierGroupIndex);
            if (matcher.group(numberGroupIndex) != null) matchedNumber = matcher.group(numberGroupIndex);

            for (int k = knownTokenGroupStart; k <= knownTokenGroupEnd; k++) {
                if (matcher.group(k) != null) {
                    matchedKnownToken = true;
                    break;
                }
            }

            if (matchedStringLiteral != null || matchedNumber != null || matchedKnownToken) continue;

            if (matchedIdentifier != null) {
                String varName = matchedIdentifier;
                if (!varName.equals("Input") && !varName.equals("Str") && !varName.equals("Int") && !varName.equals("Bool")) {
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
        
        SymbolTableEntry entry = this.tablaSimbolosGlobal.findEntryById(trimmedExpression);
        if (entry != null) {
            if (entry.tipo.equalsIgnoreCase("STR") || entry.tipo.equalsIgnoreCase("string")) return "string";
            if (entry.tipo.equalsIgnoreCase("INT")) return "int";
            if (entry.tipo.equalsIgnoreCase("BOOL")) return "bool";
        }

        return "UNKNOWN";
    }

    private void performAssignmentTypeChecks(String lhsVar, String op, String rhsExpression, String context, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap, List<String> errorsEnLinea) {
        if (declaredVariablesTypeMap.containsKey(lhsVar)) {
            String lhsType = declaredVariablesTypeMap.get(lhsVar).toLowerCase();
            if (lhsType.equals("str")) lhsType = "string";

            String rhsType = getExpressionType(rhsExpression, reglas, declaredVariablesTypeMap);

            if (op.equals(":=")) {
                if (rhsType.equals("UNKNOWN")) {
                    if (rhsExpression.matches("^Input\\s*\\(\\s*\\)$")) {
                        errorsEnLinea.add("Error de sintaxis: Falta concatenar tipo de dato al Input (ej: Input().Int(), Input().Str()) en la asignación a '" + lhsVar + "'. Contexto: " + context + ".");
                    } else if (rhsExpression.startsWith("Input")) {
                        errorsEnLinea.add("Error: La estructura del Input ('" + rhsExpression + "') no es la adecuada o está incompleta para la asignación a '" + lhsVar + "'. Verifique la sintaxis (ej: Input().Int();). Contexto: " + context + ".");
                    }
                } else if (!lhsType.equals(rhsType)) {
                     if (!context.contains("inicialización de variable")) {
                        errorsEnLinea.add("Error de Tipo: No se puede asignar un valor de tipo " + rhsType + " a la variable '" + lhsVar + "' (tipo " + lhsType + ") " + context + ".");
                    }
                }

            } else if (op.matches("\\+=|-=|\\*=|\\/=")) {
                if (!lhsType.equals("int")) {
                    errorsEnLinea.add("Error de tipo: Operador de asignación compuesta '" + op + "' requiere que la variable '" + lhsVar + "' sea de tipo INT, pero es " + lhsType + " " + context + ".");
                }
                if (rhsType.equals("UNKNOWN")) {
                    if (rhsExpression.startsWith("Input")) {
                        errorsEnLinea.add("Error: No se puede usar una expresión Input ('" + rhsExpression + "') directamente con el operador de asignación compuesta '" + op + "'. Se espera un valor de tipo INT. Contexto: " + context + ".");
                    }
                } else if (!rhsType.equals("int")) {
                    errorsEnLinea.add("Error de tipo: Operador de asignación compuesta '" + op + "' requiere que el valor a la derecha ('" + rhsExpression + "') sea de tipo INT, pero es " + rhsType + " " + context + ".");
                }
            }
        }
    }

    public void validarEstructuraConRegex(String codigoLimpioFormateado, LRParser parser) {
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
        
        // Reset para supresión
        this.suppressAsmDepth = -1;

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

            int lineBraceBalance = 0;
            for (char ch : originalLineForBraceCheck.toCharArray()) {
                if (ch == '{') lineBraceBalance++;
                else if (ch == '}') lineBraceBalance--;
            }
            
            // Control de supresión de código muerto (bloques inválidos)
            int globalBraceBalanceBeforeLine = globalBraceBalance;
            globalBraceBalance += lineBraceBalance;

            if (this.suppressAsmDepth != -1) {
                if (globalBraceBalance < this.suppressAsmDepth) {
                    this.suppressAsmDepth = -1; // Fin de supresión
                } else {
                    // Estamos dentro de un bloque inválido, seguimos
                    continue;
                }
            }
            
            if (globalBraceBalanceBeforeLine + lineBraceBalance < 0 && lineBraceBalance < 0 && errorsEnLinea.stream().noneMatch(err -> err.contains("inesperada"))) {
                errorsEnLinea.add("Error de sintaxis: Llave de cierre '}' inesperada en esta línea (causa desbalance global).");
            }

            if (lineaActual.matches(reglas.getOrDefault("MAIN_FUNC_START", "^$"))) {
                if (mainFunctionDeclared) errorsEnLinea.add("Error ESTRUCTURAL: Múltiples definiciones de 'FUNC J2G Main()'.");
                if (currentlyInMainFunctionBlock) errorsEnLinea.add("Error ESTRUCTURAL: Definición de 'FUNC J2G Main()' anidada no permitida.");
                mainFunctionDeclared = true;
                currentlyInMainFunctionBlock = true;
                mainFunctionBlockBraceDepth = globalBraceBalance;
                reglaCoincidioEstaLinea = true;

            } else if (currentlyInMainFunctionBlock) {
                
                if (lineaActual.matches(reglas.getOrDefault("SWITCH_STMT", "^$"))) {
                    // ... Lógica de switch existente ...
                    reglaCoincidioEstaLinea = true;
                } else if (this.currentlyInSwitchBlock) {
                    // ... Lógica de bloques de switch existente ...
                    if (lineaActual.matches(reglas.getOrDefault("CASE_STMT", "^$")) || lineaActual.matches(reglas.getOrDefault("DEFAULT_STMT", "^$"))) {
                        reglaCoincidioEstaLinea = true;
                        this.currentSwitchClauseActiveAndNeedsDetener = true;
                        this.currentSwitchClauseHasHadDetener = false;
                    } else if (lineaActual.matches(reglas.getOrDefault("DETENER_STMT", "^$"))) {
                        reglaCoincidioEstaLinea = true;
                        this.currentSwitchClauseActiveAndNeedsDetener = false;
                        this.currentSwitchClauseHasHadDetener = true;
                    } else if (lineaActual.matches(reglas.getOrDefault("BLOCK_END", "^$"))) {
                        reglaCoincidioEstaLinea = true;
                        if (globalBraceBalance == this.switchBlockEntryDepth - 1) {
                            this.currentlyInSwitchBlock = false;
                        }
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_NO_INIT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        String varName = matcher.group(2);
                        String varDeclaredType = matcher.group(1);
                        if (declaredVariablesTypeMap.containsKey(varName)) errorsEnLinea.add("Error Semántico: Variable '" + varName + "' ya declarada.");
                        else declaredVariablesTypeMap.put(varName, varDeclaredType);
                        
                        errorsEnLinea.addAll(checkVariableUsage(varName, "en declaración", reglas, declaredVariablesTypeMap));
                        if (errorsEnLinea.isEmpty()) {
                            SymbolTableEntry entry = this.tablaSimbolosGlobal.findVariableEntry(varName);
                            if(entry != null) allIdsUsadosGlobal.add(entry.id);
                        }
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
                            errorsEnLinea.add("Error Semántico: Variable '" + varName + "' ya declarada.");
                        } else {
                            errorsEnLinea.addAll(checkExpressionVariables(rhsAssigned, "en inicialización", reglas, declaredVariablesTypeMap, currentLineNumber));
                            String rhsType = getExpressionType(rhsAssigned, reglas, declaredVariablesTypeMap);
                            String varDeclaredTypeLower = varDeclaredType.toLowerCase();
                            if(varDeclaredTypeLower.equals("str")) varDeclaredTypeLower = "string";

                            if (!rhsType.equals("UNKNOWN") && !varDeclaredTypeLower.equals(rhsType)) {
                                System.err.println("ADVERTENCIA: Se ignoró la asignación para '" + varName + "' por incompatibilidad de tipos.");
                                SymbolTableEntry varEntry = this.tablaSimbolosGlobal.findVariableEntry(varName);
                                if (varEntry != null) {
                                    varEntry.valor = varEntry.variable; 
                                    varEntry.tipo = varDeclaredTypeLower; 
                                }
                                declaredVariablesTypeMap.put(varName, varDeclaredType);
                                if (errorsEnLinea.isEmpty()) { 
                                    SymbolTableEntry lhsEntry = this.tablaSimbolosGlobal.findVariableEntry(varName);
                                    if (lhsEntry != null) allIdsUsadosGlobal.add(lhsEntry.id); 
                                }
                            } else {
                                declaredVariablesTypeMap.put(varName, varDeclaredType);
                                errorsEnLinea.addAll(checkVariableUsage(varName, "en declaración", reglas, declaredVariablesTypeMap));
                                performAssignmentTypeChecks(varName, ":=", rhsAssigned, "en inicialización", reglas, declaredVariablesTypeMap, errorsEnLinea);
                                
                                if (errorsEnLinea.isEmpty()) {
                                    SymbolTableEntry lhsEntry = this.tablaSimbolosGlobal.findVariableEntry(varName);
                                    
                                    if (lhsEntry != null) {
                                        allIdsUsadosGlobal.add(lhsEntry.id); 
                                        String rhsId = null;
                                        SymbolTableEntry rhsEntry = this.tablaSimbolosGlobal.findVariableEntry(rhsAssigned);
                                        if (rhsEntry != null) {
                                            rhsId = rhsEntry.id;
                                            allIdsUsadosGlobal.add(rhsId);
                                        } else {
                                            rhsId = this.tablaSimbolosGlobal.obtenerIdParaLiteral(rhsAssigned);
                                            if(rhsId != null) allIdsUsadosGlobal.add(rhsId);
                                        }

                                        if (rhsId != null) {
                                             asmCodigoGlobal.append("\t; ASIGNACION INICIAL: " + lineaActual + "\n");
                                             if (varDeclaredTypeLower.equals("string") || varDeclaredTypeLower.equals("str")) {
                                                SymbolTableEntry rEntry = this.tablaSimbolosGlobal.findEntryById(rhsId);
                                                if (rEntry != null && rEntry.variable.startsWith("\"")) {
                                                     asmCodigoGlobal.append("\tlea ax, " + rhsId + "\n");
                                                } else {
                                                     asmCodigoGlobal.append("\tmov ax, " + rhsId + "\n");
                                                }
                                             } else {
                                                 asmCodigoGlobal.append("\tmov ax, " + rhsId + "\n");
                                             }
                                             asmCodigoGlobal.append("\tmov " + lhsEntry.id + ", ax\n\n");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                String[] inputTypes = { "STR", "INT", "BOOL" };
                for (int k = 0; k < inputTypes.length && !reglaCoincidioEstaLinea; k++) {
                    matcher = Pattern.compile(reglas.getOrDefault("INPUT_" + inputTypes[k] + "_STMT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        errorsEnLinea.addAll(checkBalancedSymbols(lineaActual, currentLineNumber, "en sentencia Input"));
                        String lhsVar = matcher.group(1);
                        
                        if (lhsVar != null && !lhsVar.isEmpty()) {
                            errorsEnLinea.addAll(checkVariableUsage(lhsVar, "en LHS de Input", reglas, declaredVariablesTypeMap));
                        }
                        
                        if (errorsEnLinea.isEmpty() && lhsVar != null && !lhsVar.isEmpty()) {
                            SymbolTableEntry lhsEntry = this.tablaSimbolosGlobal.findVariableEntry(lhsVar);
                            if (lhsEntry != null) {
                                asmCodigoGlobal.append("\t; ASIGNACION: " + lineaActual + "\n");
                                allIdsUsadosGlobal.add(lhsEntry.id);
                                String promptLiteral = matcher.group(2);
                                if (promptLiteral != null && !promptLiteral.isEmpty()) {
                                    String promptId = this.tablaSimbolosGlobal.obtenerIdParaLiteral(promptLiteral);
                                    if(promptId != null) {
                                        allIdsUsadosGlobal.add(promptId);
                                        asmCodigoGlobal.append("\tIMPRIMIR_MSG " + promptId + "\n");
                                    }
                                }

                                switch(inputTypes[k]) {
                                    case "INT":
                                        asmCodigoGlobal.append("\tcall PROC_CapturarNumeroDecimal\n");
                                        asmCodigoGlobal.append("\tmov " + lhsEntry.id + ", ax\n");
                                        break;
                                    case "BOOL":
                                        asmCodigoGlobal.append("\tcall PROC_CapturarNumeroDecimal\n");
                                        asmCodigoGlobal.append("\tmov " + lhsEntry.id + ", ax\n");
                                        break;
                                    case "STR":
                                        asmCodigoGlobal.append("\t; (Lectura de string omitida)\n");
                                        break;
                                }
                                asmCodigoGlobal.append("\tSALTO_LINEA\n\n");
                            }
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
                        
                        errorsEnLinea.addAll(checkVariableUsage(lhsVar, "en LHS de asignación", reglas, declaredVariablesTypeMap));
                        errorsEnLinea.addAll(checkExpressionVariables(rhsExpression, "en RHS de asignación", reglas, declaredVariablesTypeMap, currentLineNumber));
                        performAssignmentTypeChecks(lhsVar, op, rhsExpression, "en asignación", reglas, declaredVariablesTypeMap, errorsEnLinea);
                        
                        if (errorsEnLinea.isEmpty()) {
                            boolean asmGenerado = procesarYAcumularExpresion(rhsExpression, "asignación RHS", parser);
                            if (asmGenerado) {
                                SymbolTableEntry lhsEntry = this.tablaSimbolosGlobal.findVariableEntry(lhsVar);
                                if (lhsEntry != null) {
                                    allIdsUsadosGlobal.add(lhsEntry.id);
                                    String temporalResultado = parser.getFinalTemporalResult();
                                    asmCodigoGlobal.append("\t; ASIGNACION: " + lineaActual + "\n");
                                    
                                    if (op.equals(":=")) {
                                        asmCodigoGlobal.append("\tmov ax, " + temporalResultado + "\n");
                                        asmCodigoGlobal.append("\tmov " + lhsEntry.id + ", ax\n\n");
                                    } else {
                                        String asmOp = (op.equals("+=")) ? "add" : (op.equals("-=")) ? "sub" : "";
                                        if (!asmOp.isEmpty()) {
                                            asmCodigoGlobal.append("\tmov ax, " + lhsEntry.id + "\n");
                                            asmCodigoGlobal.append("\t" + asmOp + " ax, " + temporalResultado + "\n");
                                            asmCodigoGlobal.append("\tmov " + lhsEntry.id + ", ax\n\n");
                                        }
                                    }
                                }
                            } else {
                                 errorsEnLinea.add("Error de sintaxis o semántica en la expresión de asignación.");
                            }
                        }
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    String[] cStructs = { "IF_STMT", "WHILE_STMT", "DO_WHILE_TAIL_ONLY_STMT", "PRINT_STMT", "DO_WHILE_CLOSURE_LINE_STMT", "ELSE_STMT", "BLOCK_END_ELSE_STMT", "DO_WHILE_DO_STMT", "BLOCK_END" };
                    for (String key : cStructs) {
                        matcher = Pattern.compile(reglas.getOrDefault(key, "^$")).matcher(lineaActual);
                        if (matcher.matches()) {
                            reglaCoincidioEstaLinea = true;
                            String contextMsg = "en " + key.replace("_STMT", "").toLowerCase();

                            if (key.equals("IF_STMT") || key.equals("WHILE_STMT")) {
                                String expression = matcher.group(1);
                                errorsEnLinea.addAll(checkExpressionVariables(expression, contextMsg, reglas, declaredVariablesTypeMap, currentLineNumber));
                                if (errorsEnLinea.isEmpty()) {
                                    boolean asmGenerado = procesarYAcumularExpresion(expression, "condición " + key, parser);
                                    if (asmGenerado) {
                                        String endLabel = key.replace("_STMT", "").toLowerCase() + "_end_" + (labelCounter++);
                                        asmCodigoGlobal.append("\t; INICIO " + key + ": " + expression + "\n");
                                        asmCodigoGlobal.append("\tmov ax, " + parser.getFinalTemporalResult() + "\n");
                                        asmCodigoGlobal.append("\tcmp ax, 0\n");
                                        asmCodigoGlobal.append("\tje " + endLabel + "\n");
                                        controlFlowStack.push(endLabel);
                                    } else {
                                        errorsEnLinea.add("Error de sintaxis o semántica en la expresión de la condición.");
                                        this.suppressAsmDepth = globalBraceBalance;
                                    }
                                } else {
                                    this.suppressAsmDepth = globalBraceBalance;
                                }
                            } else if (key.equals("PRINT_STMT")) {
                                String expression = matcher.group(1);
                                if (!expression.trim().isEmpty()) errorsEnLinea.addAll(checkExpressionVariables(expression, contextMsg, reglas, declaredVariablesTypeMap, currentLineNumber));
                                
                                if (errorsEnLinea.isEmpty() && !expression.trim().isEmpty()) {
                                    boolean asmGenerado = procesarYAcumularExpresion(expression, "argumento de print", parser);
                                    if (asmGenerado) {
                                        String argId = parser.getFinalTemporalResult();
                                        String argType = getExpressionType(argId, reglas, declaredVariablesTypeMap);
                                        asmCodigoGlobal.append("\t; PRINT: " + lineaActual + "\n");
                                        
                                        if (argType.equals("string")) {
                                            // Ahora PRINT usa el ID directo de la tabla de símbolos
                                            SymbolTableEntry entry = tablaSimbolosGlobal.findEntryById(argId);
                                            if (entry != null) {
                                                // Usamos el ID como etiqueta directamente
                                                asmCodigoGlobal.append("\tIMPRIMIR_MSG " + entry.id + "\n");
                                            }
                                        } 
                                        asmCodigoGlobal.append("\tSALTO_LINEA\n\n");
                                    } else {
                                        errorsEnLinea.add("Error de sintaxis o semántica en el argumento del print.");
                                    }
                                }
                            } else if (key.equals("BLOCK_END_ELSE_STMT")) {
                                if (!controlFlowStack.isEmpty()) {
                                    String endIfLabel = controlFlowStack.pop();
                                    String endElseLabel = "else_end_" + (labelCounter++);
                                    asmCodigoGlobal.append("\tjmp " + endElseLabel + "\n");
                                    asmCodigoGlobal.append(endIfLabel + ":\n");
                                    controlFlowStack.push(endElseLabel);
                                } else {
                                    errorsEnLinea.add("Error de sintaxis: 'else' sin un 'if' correspondiente.");
                                    this.suppressAsmDepth = globalBraceBalance;
                                }
                            } else if (key.equals("BLOCK_END")) {
                                if (globalBraceBalance == this.switchBlockEntryDepth - 1) {
                                    // Switch end
                                } else if (!controlFlowStack.isEmpty()) {
                                    String endLabel = controlFlowStack.pop();
                                    asmCodigoGlobal.append(endLabel + ":\n\n");
                                }
                            }
                            break;
                        }
                    }
                }
                
                mainFunctionBlockBraceDepth = globalBraceBalance;
                if (mainFunctionBlockBraceDepth == 0 && currentlyInMainFunctionBlock) currentlyInMainFunctionBlock = false;
            } else {
                if (!lineaActual.isEmpty()) {
                    if (!mainFunctionDeclared) errorsEnLinea.add("Error ESTRUCTURAL: Código antes de Main.");
                    else errorsEnLinea.add("Error ESTRUCTURAL: Código después de Main.");
                }
            }

            if (!reglaCoincidioEstaLinea && currentlyInMainFunctionBlock && !lineaActual.isEmpty()) {
                 errorsEnLinea.add("Error de estructura/sintaxis en la línea: " + lineaActual);
            }

            if (!errorsEnLinea.isEmpty()) {
                System.err.println("Error(es) en línea " + currentLineNumber + ": " + lineasCodigo[i]);
                for (String error : errorsEnLinea) System.err.println("  - " + error);
                erroresEncontradosEnGeneral = true;
            }
        }
        
        if (!erroresEncontradosEnGeneral) System.err.println("No se encontraron errores de estructura.");
        System.err.println("---------------------------------------");
    }
}