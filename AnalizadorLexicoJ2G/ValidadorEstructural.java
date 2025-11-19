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

    // --- INICIO DE ATRIBUTOS PARA GENERACIÓN ASM GLOBAL ---
    private String archivoAsmPath; // Ruta para el archivo de salida final
    private StringBuilder asmDatosGlobal; // Acumula todas las definiciones .data
    private StringBuilder asmCodigoGlobal; // Acumula todo el código .code
    private StringBuilder asmProcedimientos; // Almacena los procedimientos .inc
    private Set<String> allIdsUsadosGlobal; // Rastrea todos los id (id1, id20) usados
    private Set<String> allTemporalesUsadosGlobal; // Rastrea todos los temporales (t1, t2) usados
    private Map<String, String> stringLiteralToAsmLabel; // Mapea "hola" -> msg1
    private int stringLabelCounter = 1;
    private int labelCounter = 1; // Para etiquetas if/else/while
    private Stack<String> controlFlowStack; // Pila para etiquetas de fin (ej: "if_end_1")
    private String baseAsmTemplate;
    private Map<String, String> reglasRegexCache;
    // --- FIN DE ATRIBUTOS ASM ---

    // --- INICIO ATRIBUTOS PARA VALIDACIÓN (DE TU VERSIÓN) ---
    private boolean currentlyInSwitchBlock = false;
    private int switchBlockEntryDepth = 0;
    private boolean currentSwitchClauseActiveAndNeedsDetener = false;
    private boolean currentSwitchClauseHasHadDetener = false;
    private int lineOfCurrentSwitchClauseStart = 0;
    private String contentOfCurrentSwitchClauseStart = "";
    private int switchActualOpeningLine = 0;
    private String currentSwitchExpressionType = null;
    // --- FIN ATRIBUTOS VALIDACIÓN ---

    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal, AnalizadorLexicoCore analizadorLexico, PrintStream outputTables, String archivoAsmPath) {
        this.tablaSimbolosGlobal = tablaSimbolosGlobal;
        this.analizadorLexico = analizadorLexico;
        this.outputTables = outputTables;
        
        // --- Inicializar atributos ASM ---
        this.archivoAsmPath = archivoAsmPath;
        this.asmDatosGlobal = new StringBuilder();
        this.asmCodigoGlobal = new StringBuilder();
        this.asmProcedimientos = new StringBuilder();
        this.allIdsUsadosGlobal = new LinkedHashSet<>();
        this.allTemporalesUsadosGlobal = new LinkedHashSet<>();
        this.stringLiteralToAsmLabel = new HashMap<>();
        this.controlFlowStack = new Stack<>();
        
        this.baseAsmTemplate = J2GAnalizadorApp.leerArchivo("J2G/base.asm");
        this.reglasRegexCache = cargarReglasRegexEnMemoria(); // Cargar regex una vez
        
        // Cargar los procedimientos y macros proporcionados
        cargarProcedimientosYMacros();
    }
    
    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal, AnalizadorLexicoCore analizadorLexico, PrintStream outputTables) {
        this(tablaSimbolosGlobal, analizadorLexico, outputTables, null); // Constructor anterior
    }

    public ValidadorEstructural(TablaSimbolos tablaSimbolosGlobal) {
        this(tablaSimbolosGlobal, new AnalizadorLexicoCore(tablaSimbolosGlobal), System.out, null);
    }
    
    
    // --- INICIO MÉTODOS DE GENERACIÓN ASM ---

    /**
     * Carga el texto de los procedimientos y macros proporcionados en el
     * StringBuilder asmProcedimientos.
     */
    private void cargarProcedimientosYMacros() {
        // --- INICIO MACROS ---
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
            "    IMPRIMIR_MSG saltoLinea_msg\n" + // Usará saltoLinea_msg que definiremos en .data
            "ENDM\n\n"
        );
        asmProcedimientos.append("; --- FIN MACROS ---\n\n");
        
        // --- INICIO PROCEDIMIENTOS ---
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
            "PROC_MostrarNumeroDecimal PROC FAR\n" +
            "	push bp\n" +
            "	mov bp, sp\n" +
            "	push ax\n" +
            "	push bx\n" +
            "	push cx\n" +
            "	push dx\n" +
            "\n" +
            "	mov ax, [bp+6]\n" +
            "	mov bx, 10\n" +
            "	xor cx, cx\n" +
            "\n" +
            "	cmp ax, 0\n" +
            "	jne convertir_digitos_num_local_pmnd_procs\n" +
            "	mov dl, '0'\n" +
            "	mov ah, 02h\n" +
            "	int 21h\n" +
            "	jmp fin_imprimir_num_local_pmnd_procs\n" +
            "\n" +
            "convertir_digitos_num_local_pmnd_procs:\n" +
            "convertir_loop_num_local_pmnd_procs:\n" +
            "	xor dx, dx\n" +
            "	div bx\n" +
            "	push dx\n" +
            "	inc cx\n" +
            "	cmp ax, 0\n" +
            "	jne convertir_loop_num_local_pmnd_procs\n" +
            "\n" +
            "imprimir_loop_num_local_pmnd_procs:\n" +
            "	pop dx\n" +
            "	add dl, '0'\n" +
            "	mov ah, 02h\n" +
            "	int 21h\n" +
            "	loop imprimir_loop_num_local_pmnd_procs\n" +
            "\n" +
            "fin_imprimir_num_local_pmnd_procs:\n" +
            "	pop dx\n" +
            "	pop cx\n" +
            "	pop bx\n" +
            "	pop ax\n" +
            "	pop bp\n" +
            "	retf 2\n" +
            "PROC_MostrarNumeroDecimal ENDP\n\n"
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
        // (Aquí se agregarían los otros procedimientos como PROC_SolicitarNombreValidado)
        asmProcedimientos.append("; --- FIN PROCEDIMIENTOS ---\n");
    }

    /**
     * Escribe el archivo .asm final combinando la plantilla base,
     * los datos acumulados, el código y los procedimientos.
     */
    public void escribirAsmFinal(TablaSimbolos tablaSimbolos) throws IOException {
        if (this.archivoAsmPath == null || this.baseAsmTemplate == null) {
            return; // No hacer nada si no hay ruta o plantilla
        }

        StringBuilder datosSegmento = new StringBuilder();
        
        // 1. Declarar Mensajes y literales de string
        datosSegmento.append("\t; --- Mensajes y Literales ---\n");
        datosSegmento.append("\tsaltoLinea_msg db 0Dh, 0Ah, '$'\n"); // Para SALTO_LINEA
        datosSegmento.append("\tmsg_true db \"TRUE$\"\n");
        datosSegmento.append("\tmsg_false db \"FALSE$\"\n");
        datosSegmento.append("\tmsg_prompt_bool db \"Ingrese 1 para TRUE, 0 para FALSE: $\"\n");
        
        for (Map.Entry<String, String> entry : stringLiteralToAsmLabel.entrySet()) {
            String literal = entry.getKey(); // Es "hola mundo"
            String label = entry.getValue(); // Es msg1
            datosSegmento.append(String.format("\t%s db \"%s$\"\n", label, literal));
        }

        // 2. Declarar todas las variables y literales (IDs) usados
        datosSegmento.append("\n\t; --- Variables y Literales del Programa ---\n");
        for (String id : allIdsUsadosGlobal) {
            SymbolTableEntry entry = tablaSimbolos.findEntryById(id);
            if (entry != null) {
                String valorAsm = "?";
                String comentario = entry.variable; // ej: a, "hola", 10

                if (entry.tipo.equals("int") || entry.tipo.equals("bool")) {
                    if (entry.variable.matches(reglasRegexCache.get("VAR_NAME"))) { // Es 'a'
                        SymbolTableEntry valorEntry = tablaSimbolos.findEntryById(entry.valor);
                        if (valorEntry != null) {
                             valorAsm = valorEntry.variable; // "10"
                        } else if (entry.valor.matches("-?[0-9]+")) {
                             valorAsm = entry.valor; // Caso: INT a := 10 (valor es "10")
                        } else {
                            valorAsm = "?"; // Variable declarada sin inicializar
                        }
                    } else { // Es un literal '10'
                        valorAsm = entry.variable; // "10"
                    }

                    if (valorAsm.equalsIgnoreCase("TRUE")) valorAsm = "1";
                    else if (valorAsm.equalsIgnoreCase("FALSE")) valorAsm = "0";
                    
                    if (valorAsm.matches("-?[0-9]+")) {
                         datosSegmento.append(String.format("\t%s dw %s \t; %s\n", entry.id, valorAsm, comentario));
                    }
                }
            }
        }

        // 3. Declarar todos los temporales
        datosSegmento.append("\n\t; --- Temporales ---\n");
        for (String temporal : allTemporalesUsadosGlobal) {
            datosSegmento.append(String.format("\t%s dw ?\n", temporal));
        }
        
        // 4. Inyectar todo en la plantilla
        String asmFinal = this.baseAsmTemplate;
        
        asmFinal = asmFinal.replace("datos segment para public 'data'",
                                  "datos segment para public 'data'\n" + datosSegmento.toString() + asmDatosGlobal.toString());
        
        asmFinal = asmFinal.replace(";codigo",
                                  ";--- Inicio del codigo principal ---\n\n" 
                                  + asmCodigoGlobal.toString() 
                                  + "\n;--- Fin del codigo principal ---\n"
                                  + "\n\n" + asmProcedimientos.toString());
        
        // 5. Escribir en el archivo de salida
        try (PrintWriter out = new PrintWriter(new FileWriter(this.archivoAsmPath))) {
            out.print(asmFinal);
        }
    }
    
    /**
     * Obtiene (o crea) una etiqueta de ensamblador para un literal de cadena.
     * Ej: "hola" -> "msg1"
     */
    private String getStringLiteralLabel(String literal) {
        // Quita las comillas, ej: "\"hola\"" -> "hola"
        String unquotedLiteral = literal.substring(1, literal.length() - 1);
        
        if (stringLiteralToAsmLabel.containsKey(unquotedLiteral)) {
            return stringLiteralToAsmLabel.get(unquotedLiteral);
        }
        
        String newLabel = "msg" + (stringLabelCounter++);
        stringLiteralToAsmLabel.put(unquotedLiteral, newLabel);
        return newLabel;
    }
    
    /**
     * Procesa una expresión con el parser, agrega el código y los IDs
     * a los acumuladores globales, y reporta si fue exitoso.
     * @return true si la sintaxis y semántica del parser son correctas, false si no.
     */
    private boolean procesarYAcumularExpresion(String expresion, String context, LRParser parser) {
        List<String> expressionTokens = getExpressionTokens(expresion);
        outputTables.println("Análisis Sintáctico (" + context + "): '" + expresion + "'");
        
        boolean sintaxisYSemanticaValida = parser.parse(expressionTokens);
        
        if (sintaxisYSemanticaValida) {
            // Acumular el código de la expresión (ej: t1 = a + b)
            asmCodigoGlobal.append(parser.getAsmCodigo());
            
            // Registrar los IDs y temporales usados
            allIdsUsadosGlobal.addAll(parser.getIdsEncontrados());
            allTemporalesUsadosGlobal.addAll(parser.getTemporalesDeclarados());
        }
        return sintaxisYSemanticaValida;
    }

    // --- FIN MÉTODOS DE GENERACIÓN ASM ---


    // --- INICIO MÉTODOS DE VALIDACIÓN (DE TU VERSIÓN) ---

    private Map<String, String> cargarReglasRegexEnMemoria() {
        // Carga las regex para validación estructural
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
        reglas.put("FOR_STMT", "^for\\s*\\((.*?)\\s*;\\s*(.*?)\\s*;\\s*(.*?)\\)\\s*\\{");
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
        
        // --- NUEVO: Buscar por ID ---
        SymbolTableEntry entry = this.tablaSimbolosGlobal.findEntryById(trimmedExpression);
        if (entry != null) {
            if (entry.tipo.equalsIgnoreCase("STR") || entry.tipo.equalsIgnoreCase("string")) return "string";
            if (entry.tipo.equalsIgnoreCase("INT")) return "int";
            if (entry.tipo.equalsIgnoreCase("BOOL")) return "bool";
        }

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
                         // No agregar error aquí si es una expresión compleja
                    }
                } else if (!rhsType.equals("int")) {
                    errorsEnLinea.add("Error de tipo: Operador de asignación compuesta '" + op +
                            "' requiere que el valor a la derecha ('" + rhsExpression + "') sea de tipo INT, pero es "
                            + rhsType + " " + context + ".");
                }
            }
        }
    }
    // --- FIN MÉTODOS DE VALIDACIÓN ---


    // --- MÉTODO PRINCIPAL (FUSIÓN DE AMBAS VERSIONES) ---
    public void validarEstructuraConRegex(String codigoLimpioFormateado, LRParser parser) {
        Map<String, String> reglas = this.reglasRegexCache; 
        Map<String, String> declaredVariablesTypeMap = new HashMap<>();
        int globalBraceBalance = 0;
        List<String> globalStructuralErrors = new ArrayList<>();

        boolean mainFunctionDeclared = false;
        boolean currentlyInMainFunctionBlock = false;
        int mainFunctionBlockBraceDepth = 0;

        // Reseteo de variables de switch
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

            // Lógica de balance de llaves
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
            // --- FIN Lógica balance de llaves ---


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
                
                // --- Lógica de SWITCH (Validación) ---
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
                        
                        // Validar variables usadas en la expresión
                        errorsEnLinea.addAll(checkExpressionVariables(switchExpressionText, "en expresión de switch", reglas, declaredVariablesTypeMap, currentLineNumber));

                        // Si las variables están bien, parsear para ASM y semántica de tipos
                        if(errorsEnLinea.isEmpty()) {
                            boolean sintaxisValida = procesarYAcumularExpresion(switchExpressionText, "expresión de switch", parser);
                            if (!sintaxisValida) {
                                errorsEnLinea.add("Error de sintaxis o semántica en la expresión del switch: '" + switchExpressionText + "'.");
                            }
                        }
                        
                        this.currentSwitchExpressionType = getExpressionType(switchExpressionText, reglas, declaredVariablesTypeMap);
                        if (this.currentSwitchExpressionType.equals("UNKNOWN")
                                && switchExpressionText.matches(varOrIdCorePattern)
                                && !declaredVariablesTypeMap.containsKey(switchExpressionText)) {
                            // Error de variable no declarada ya fue reportado por checkExpressionVariables
                        } else if (this.currentSwitchExpressionType.equals("UNKNOWN") && errorsEnLinea.isEmpty()) {
                            errorsEnLinea.add("Error semántico: La expresión del switch '" + switchExpressionText
                                    + "' no es una variable declarada ni un literal simple (STR, INT, BOOL) cuyo tipo se pueda determinar para la comparación de casos.");
                        }
                    } else {
                        errorsEnLinea.addAll(
                                checkBalancedSymbols(lineaActual, currentLineNumber, "en declaración de switch"));
                    }
                } else if (this.currentlyInSwitchBlock) {
                    // (Toda la lógica de case, default, detener... validación sin ASM por ahora)
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
                // --- FIN Lógica de SWITCH ---

                if (!reglaCoincidioEstaLinea) {
                    // --- Lógica de FOR (Validación) ---
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
                                
                                errorsEnLinea.addAll(checkExpressionVariables(rhsAssigned, contextFor, reglas, declaredVariablesTypeMap, currentLineNumber));
                                if(errorsEnLinea.isEmpty()) {
                                    if (!procesarYAcumularExpresion(rhsAssigned, "inicialización de for", parser)) {
                                        errorsEnLinea.add("Error de sintaxis o semántica en la expresión de inicialización del for: '" + rhsAssigned + "'.");
                                    }
                                }

                                performAssignmentTypeChecks(varName, ":=", rhsAssigned, contextFor, reglas,
                                        declaredVariablesTypeMap, errorsEnLinea);
                            } else if (initAssignMatcher.matches()) {
                                String lhsVar = initAssignMatcher.group(1);
                                String op = initAssignMatcher.group(2);
                                String rhsEx = initAssignMatcher.group(3).trim();
                                errorsEnLinea.addAll(checkVariableUsage(lhsVar, contextFor + " (LHS asignación)",
                                        reglas, declaredVariablesTypeMap));
                                
                                errorsEnLinea.addAll(checkExpressionVariables(rhsEx, contextFor, reglas, declaredVariablesTypeMap, currentLineNumber));
                                if(errorsEnLinea.isEmpty()) {
                                    if (!procesarYAcumularExpresion(rhsEx, "asignación de for", parser)) {
                                        errorsEnLinea.add("Error de sintaxis o semántica en la expresión de asignación del for: '" + rhsEx + "'.");
                                    }
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
                            errorsEnLinea.addAll(checkExpressionVariables(conditionPart, contextFor, reglas, declaredVariablesTypeMap, currentLineNumber));
                            if(errorsEnLinea.isEmpty()) {
                                if (!procesarYAcumularExpresion(conditionPart, "condición de for", parser)) {
                                    errorsEnLinea.add("Error de sintaxis o semántica en la expresión de condición del for: '" + conditionPart + "'.");
                                }
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
                                
                                errorsEnLinea.addAll(checkExpressionVariables(rhsEx, contextFor, reglas, declaredVariablesTypeMap, currentLineNumber));
                                if(errorsEnLinea.isEmpty()) {
                                    if (!procesarYAcumularExpresion(rhsEx, "actualización de for", parser)) {
                                        errorsEnLinea.add("Error de sintaxis o semántica en la expresión de actualización del for: '" + rhsEx + "'.");
                                    }
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
                    // --- Lógica de VAR_DECL_NO_INIT ---
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
                        
                        // Generar ASM solo si no hay errores
                        if (errorsEnLinea.isEmpty()) {
                            SymbolTableEntry entry = this.tablaSimbolosGlobal.findVariableEntry(varName);
                             if(entry != null) {
                                 allIdsUsadosGlobal.add(entry.id);
                             }
                        }
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    // --- Lógica de VAR_DECL_CON_INIT ---
                    matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_CON_INIT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        String varDeclaredType = matcher.group(1);
                        String varName = matcher.group(2);
                        String rhsAssigned = matcher.group(3);

                        if (declaredVariablesTypeMap.containsKey(varName)) {
                            errorsEnLinea.add("Error Semántico: Variable '" + varName + "' ya declarada (redefinición).");
                        } else {
                            // Validar variables en RHS (si es una variable)
                            errorsEnLinea.addAll(checkExpressionVariables(rhsAssigned, "en inicialización de variable", reglas, declaredVariablesTypeMap, currentLineNumber));
                            
                            String rhsType = getExpressionType(rhsAssigned, reglas, declaredVariablesTypeMap);
                            String varDeclaredTypeLower = varDeclaredType.toLowerCase();
                            if(varDeclaredTypeLower.equals("str")) varDeclaredTypeLower = "string";

                            if (!rhsType.equals("UNKNOWN") && !varDeclaredTypeLower.equals(rhsType)) {
                                // Error de tipo, reportar ADVERTENCIA y no asignar
                                System.err.println("ADVERTENCIA: Se ignoró la asignación para la variable '" + varName +
                                                  "' por incompatibilidad de tipos. Se esperaba " + varDeclaredType +
                                                  " pero se encontró " + rhsType + ". La variable quedará solo declarada.");

                                // Corregir tabla de símbolos
                                SymbolTableEntry varEntry = this.tablaSimbolosGlobal.findVariableEntry(varName);
                                if (varEntry != null) {
                                    String incorrectLiteralId = varEntry.valor;
                                    SymbolTableEntry literalEntry = this.tablaSimbolosGlobal.findEntryById(incorrectLiteralId);
                                    
                                    varEntry.valor = varEntry.variable; // Asignar 'var1' a sí mismo
                                    varEntry.tipo = varDeclaredTypeLower; // Corregir tipo
                                    
                                    // Eliminar el literal incorrecto ("23") de la tabla si existe
                                    if (literalEntry != null) {
                                        this.tablaSimbolosGlobal.removeNewVariableEntry(literalEntry);
                                    }
                                }
                                declaredVariablesTypeMap.put(varName, varDeclaredType);

                                // Generar ASM solo para la declaración (sin asignación)
                                if (errorsEnLinea.isEmpty()) { // Aún puede haber error de re-declaración
                                    SymbolTableEntry lhsEntry = this.tablaSimbolosGlobal.findVariableEntry(varName);
                                    if (lhsEntry != null) {
                                        allIdsUsadosGlobal.add(lhsEntry.id); 
                                    }
                                }
                            } else {
                                // Tipos coinciden o RHS es una expresión compleja (UNKNOWN)
                                declaredVariablesTypeMap.put(varName, varDeclaredType);
                                errorsEnLinea.addAll(checkVariableUsage(varName, "en declaración (nombre)", reglas, declaredVariablesTypeMap));
                                
                                // Realizar chequeo de asignación (para el caso de expresiones complejas)
                                performAssignmentTypeChecks(varName, ":=", rhsAssigned, "en inicialización de variable", reglas, declaredVariablesTypeMap, errorsEnLinea);
                                
                                // Generar ASM solo si no hay errores de ningún tipo
                                if (errorsEnLinea.isEmpty()) {
                                    SymbolTableEntry lhsEntry = this.tablaSimbolosGlobal.findVariableEntry(varName);
                                    SymbolTableEntry rhsEntry = this.tablaSimbolosGlobal.findVariableEntry(rhsAssigned);
                                    
                                    if (lhsEntry != null) {
                                        allIdsUsadosGlobal.add(lhsEntry.id); 
                                        
                                        String rhsId = null;
                                        if (rhsEntry != null) {
                                            rhsId = rhsEntry.id;
                                            allIdsUsadosGlobal.add(rhsId);
                                        } else {
                                            rhsId = this.tablaSimbolosGlobal.obtenerIdParaLiteral(rhsAssigned);
                                            if(rhsId != null) allIdsUsadosGlobal.add(rhsId);
                                        }

                                        if (rhsId != null) {
                                             asmCodigoGlobal.append("\t; ASIGNACION INICIAL: " + lineaActual + "\n");
                                             asmCodigoGlobal.append("\tmov ax, " + rhsId + "\n");
                                             asmCodigoGlobal.append("\tmov " + lhsEntry.id + ", ax\n\n");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Lógica de INPUT ---
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
                        
                        // Generar ASM solo si no hay errores
                        if (errorsEnLinea.isEmpty() && lhsVar != null && !lhsVar.isEmpty()) {
                            SymbolTableEntry lhsEntry = this.tablaSimbolosGlobal.findVariableEntry(lhsVar);
                            if (lhsEntry != null) {
                                asmCodigoGlobal.append("\t; INPUT: " + lineaActual + "\n");
                                allIdsUsadosGlobal.add(lhsEntry.id);

                                String promptLiteral = matcher.group(2);
                                if (promptLiteral != null && !promptLiteral.isEmpty()) {
                                    String promptLabel = getStringLiteralLabel(promptLiteral);
                                    asmCodigoGlobal.append("\tIMPRIMIR_MSG " + promptLabel + "\n");
                                }

                                switch(inputTypes[k]) {
                                    case "INT":
                                        asmCodigoGlobal.append("\tcall PROC_CapturarNumeroDecimal\n");
                                        asmCodigoGlobal.append("\tmov " + lhsEntry.id + ", ax\n");
                                        break;
                                    case "BOOL":
                                        asmCodigoGlobal.append("\tIMPRIMIR_MSG msg_prompt_bool\n");
                                        asmCodigoGlobal.append("\tcall PROC_CapturarNumeroDecimal\n");
                                        asmCodigoGlobal.append("\tmov " + lhsEntry.id + ", ax\n");
                                        break;
                                    case "STR":
                                        String bufferLabel = lhsEntry.id + "_buffer";
                                        asmDatosGlobal.append(String.format("\t%s db 255, 0, 255 dup(0)\n", bufferLabel));
                                        asmCodigoGlobal.append("\tlea ax, " + bufferLabel + "\n");
                                        asmCodigoGlobal.append("\tpush ax\n");
                                        // Asumir que existe PROC_SolicitarNombreValidado
                                        // asmCodigoGlobal.append("\tcall PROC_SolicitarNombreValidado\n"); 
                                        asmCodigoGlobal.append("\t; (Llamada a PROC_SolicitarNombreValidado omitida)\n");
                                        break;
                                }
                                asmCodigoGlobal.append("\tSALTO_LINEA\n\n");
                            }
                        }
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    // --- Lógica de ASSIGNMENT ---
                    matcher = Pattern.compile(reglas.getOrDefault("ASSIGNMENT", "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidioEstaLinea = true;
                        String lhsVar = matcher.group(1);
                        String op = matcher.group(2);
                        String rhsExpression = matcher.group(3).trim();
                        
                        // 1. Validar LHS
                        errorsEnLinea.addAll(
                                checkVariableUsage(lhsVar, "en LHS de asignación", reglas, declaredVariablesTypeMap));
                        // 2. Validar variables en RHS
                        errorsEnLinea.addAll(checkExpressionVariables(rhsExpression, "en RHS de asignación", reglas, declaredVariablesTypeMap, currentLineNumber));
                        // 3. Validar tipos de asignación
                        performAssignmentTypeChecks(lhsVar, op, rhsExpression, "en asignación", reglas,
                                declaredVariablesTypeMap, errorsEnLinea);
                        
                        // 4. Generar ASM solo si todo es válido
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
                                        String asmOp = "";
                                        if(op.equals("+=")) asmOp = "add";
                                        if(op.equals("-=")) asmOp = "sub";
                                        
                                        if (!asmOp.isEmpty()) {
                                            asmCodigoGlobal.append("\tmov ax, " + lhsEntry.id + "\n");
                                            asmCodigoGlobal.append("\t" + asmOp + " ax, " + temporalResultado + "\n");
                                            asmCodigoGlobal.append("\tmov " + lhsEntry.id + ", ax\n\n");
                                        }
                                        // (mul y div compuestas son más complejas)
                                    }
                                }
                            } else {
                                 errorsEnLinea.add("Error de sintaxis o semántica en la expresión de asignación: '" + rhsExpression + "'.");
                            }
                        }
                    }
                }

                if (!reglaCoincidioEstaLinea) {
                    // --- Lógica de IF, WHILE, PRINT, ELSE, END, DO ---
                    String[] cStructs = { "IF_STMT", "WHILE_STMT", "DO_WHILE_TAIL_ONLY_STMT", "PRINT_STMT",
                            "DO_WHILE_CLOSURE_LINE_STMT", "ELSE_STMT",
                            "BLOCK_END_ELSE_STMT", "DO_WHILE_DO_STMT", "BLOCK_END" };
                    for (String key : cStructs) {
                        matcher = Pattern.compile(reglas.getOrDefault(key, "^$")).matcher(lineaActual);
                        if (matcher.matches()) {
                            reglaCoincidioEstaLinea = true;
                            String contextMsg = "en " + key.replace("_STMT", "").toLowerCase();

                            if (key.equals("IF_STMT") || key.equals("WHILE_STMT")) {
                                String expression = matcher.group(1);
                                
                                // 1. Validar variables
                                errorsEnLinea.addAll(checkExpressionVariables(expression, contextMsg, reglas, declaredVariablesTypeMap, currentLineNumber));
                                
                                // 2. Generar ASM si es válido
                                if (errorsEnLinea.isEmpty()) {
                                    boolean asmGenerado = procesarYAcumularExpresion(expression, "condición " + key, parser);
                                    
                                    if (asmGenerado) {
                                        String endLabel = key.replace("_STMT", "").toLowerCase() + "_end_" + (labelCounter++);
                                        asmCodigoGlobal.append("\t; INICIO " + key + ": " + expression + "\n");
                                        asmCodigoGlobal.append("\tmov ax, " + parser.getFinalTemporalResult() + "\n");
                                        asmCodigoGlobal.append("\tcmp ax, 0\n"); // Compara si es FALSE
                                        asmCodigoGlobal.append("\tje " + endLabel + "\n"); // Salta si es FALSE
                                        controlFlowStack.push(endLabel);
                                    } else {
                                        errorsEnLinea.add("Error de sintaxis o semántica en la expresión de la condición: '" + expression + "'.");
                                    }
                                }
                                
                            } else if (key.equals("PRINT_STMT")) {
                                String expression = matcher.group(1);
                                
                                // 1. Validar variables
                                if (!expression.trim().isEmpty()) {
                                    errorsEnLinea.addAll(checkExpressionVariables(expression, contextMsg, reglas, declaredVariablesTypeMap, currentLineNumber));
                                }
                                
                                // 2. Generar ASM si es válido
                                if (errorsEnLinea.isEmpty() && !expression.trim().isEmpty()) {
                                    boolean asmGenerado = procesarYAcumularExpresion(expression, "argumento de print", parser);
                                    
                                    if (asmGenerado) {
                                        String argId = parser.getFinalTemporalResult();
                                        String argType = getExpressionType(argId, reglas, declaredVariablesTypeMap);
                                        
                                        asmCodigoGlobal.append("\t; PRINT: " + lineaActual + "\n");
                                        
                                        if (argType.equals("string")) {
                                            SymbolTableEntry entry = tablaSimbolosGlobal.findEntryById(argId);
                                            if (entry != null) {
                                                String label = getStringLiteralLabel(entry.variable);
                                                asmCodigoGlobal.append("\tIMPRIMIR_MSG " + label + "\n");
                                            }
                                        } else if (argType.equals("int")) {
                                            asmCodigoGlobal.append("\tpush " + argId + "\n");
                                            asmCodigoGlobal.append("\tcall PROC_MostrarNumeroDecimal\n");
                                        } else if (argType.equals("bool")) {
                                            String falseLabel = "bool_pr_false_" + (labelCounter++);
                                            String endLabel = "bool_pr_end_" + (labelCounter++);
                                            asmCodigoGlobal.append("\tmov ax, " + argId + "\n");
                                            asmCodigoGlobal.append("\tcmp ax, 0\n");
                                            asmCodigoGlobal.append("\tje " + falseLabel + "\n");
                                            asmCodigoGlobal.append("\tIMPRIMIR_MSG msg_true\n");
                                            asmCodigoGlobal.append("\tjmp " + endLabel + "\n");
                                            asmCodigoGlobal.append(falseLabel + ":\n");
                                            asmCodigoGlobal.append("\tIMPRIMIR_MSG msg_false\n");
                                            asmCodigoGlobal.append(endLabel + ":\n");
                                        }
                                        asmCodigoGlobal.append("\tSALTO_LINEA\n\n");
                                    } else {
                                        errorsEnLinea.add("Error de sintaxis o semántica en el argumento del print: '" + expression + "'.");
                                    }
                                }
                                
                            } else if (key.equals("BLOCK_END_ELSE_STMT")) {
                                // ASM para manejar el salto del if
                                if (!controlFlowStack.isEmpty()) {
                                    String endIfLabel = controlFlowStack.pop();
                                    String endElseLabel = "else_end_" + (labelCounter++);
                                    asmCodigoGlobal.append("\tjmp " + endElseLabel + "\n");
                                    asmCodigoGlobal.append(endIfLabel + ":\n");
                                    controlFlowStack.push(endElseLabel);
                                } else {
                                    errorsEnLinea.add("Error de sintaxis: 'else' sin un 'if' correspondiente.");
                                }

                            } else if (key.equals("BLOCK_END")) {
                                if (globalBraceBalance == this.switchBlockEntryDepth - 1) {
                                    // (Cierre de switch, ya manejado en la lógica de switch)
                                } else if (!controlFlowStack.isEmpty()) {
                                    // Cierre de if, else, o while
                                    String endLabel = controlFlowStack.pop();
                                    asmCodigoGlobal.append(endLabel + ":\n\n");
                                }
                            }
                            
                            // (Validaciones de DO_WHILE)
                            if (key.equals("DO_WHILE_CLOSURE_LINE_STMT") || key.equals("DO_WHILE_TAIL_ONLY_STMT")) {
                                String expression = matcher.group(1);
                                errorsEnLinea.addAll(checkExpressionVariables(expression, contextMsg, reglas, declaredVariablesTypeMap, currentLineNumber));
                                if(errorsEnLinea.isEmpty()) {
                                    if (!procesarYAcumularExpresion(expression, "condición " + key, parser)) {
                                        errorsEnLinea.add("Error de sintaxis o semántica en la expresión de la condición: '" + expression + "'.");
                                    }
                                }
                            }
                            
                            break; // Salir del bucle cStructs
                        }
                    }
                }
                
                // --- Lógica de fin de main ---
                mainFunctionBlockBraceDepth = globalBraceBalance;
                if (mainFunctionBlockBraceDepth == 0 && currentlyInMainFunctionBlock) {
                    currentlyInMainFunctionBlock = false;
                }

            } else {
                 // --- Lógica de error por código fuera de Main ---
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

            // --- Lógica de error por línea no reconocida ---
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

            // --- Reportar errores de la línea ---
            if (!errorsEnLinea.isEmpty()) {
                System.err.println("Error(es) en línea " + currentLineNumber + ": " + lineasCodigo[i]);
                for (String error : errorsEnLinea)
                    System.err.println("  - " + error);
                erroresEncontradosEnGeneral = true;
            }
        }

        // --- Lógica de errores estructurales globales ---
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