import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class SymbolTableEntry {
    String variable;
    String id;
    String tipo;
    String valor;

    public SymbolTableEntry(String variable, String id, String tipo, String valor) {
        this.variable = variable;
        this.id = id;
        this.tipo = tipo;
        this.valor = valor;
    }

    @Override
    public String toString() {
        return String.format("%-20s | %-10s | %-10s | %-20s", variable, id, tipo, valor);
    }
}

public class AnalizadorLexicoJ2G {

    private Map<String, SymbolTableEntry> tabsimBase = new HashMap<>();
    private List<SymbolTableEntry> nuevasVariablesDetectadas = new ArrayList<>();
    private Map<String, String> variableToIdMap = new LinkedHashMap<>();
    private Map<String, String> literalToIdMap = new LinkedHashMap<>(); 
    private int nextIdCounter = 1;
    private List<String> palabrasReservadasYSimbolosConocidos = new ArrayList<>(); 

    public static void main(String[] args) {
        AnalizadorLexicoJ2G analizador = new AnalizadorLexicoJ2G();
        // Using user-provided absolute paths
        String archivoEntrada = "/Users/jorgeandreshernandezpelayo/Documents/.yorch/Escuela/Codigos/J2G/Analizador lexico/entrada.txt"; 
        String archivoTabsim = "/Users/jorgeandreshernandezpelayo/Documents/.yorch/Escuela/Codigos/J2G/Analizador lexico/tabsim.txt";

        System.out.println("Cargando archivo de tabla de símbolos: " + archivoTabsim);
        analizador.cargarTabsim(archivoTabsim);

        System.out.println("Se cargó el archivo " + archivoEntrada + " el cual se está analizando.");
        String codigoOriginal = analizador.leerArchivo(archivoEntrada);
        if (codigoOriginal == null) {
            System.err.println("Error: No se pudo leer el archivo de entrada.");
            return;
        }

        System.out.println("\nINICIANDO PROCESAMIENTO DEL CÓDIGO");

        // --- FASE 1 ---
        System.out.println("\nRESULTADO DE LA FASE 1:");
        System.out.println("Se eliminan comentarios y se tokeniza el código.\n");
        List<String> tokensFase1 = analizador.fase1_limpiarYTokenizar(codigoOriginal);
        String codigoLimpioFormateado = analizador.prettyPrintCode(tokensFase1);
        System.out.println(codigoLimpioFormateado);

        // --- FASE 2 ---
        System.out.println("\nRESULATDO DE LA FASE 2:");
        System.out.println("Se transforman los tokens a IDs y se muestran las nuevas variables detectadas.\n");
        List<String> tokensTransformados = analizador.fase2_transformarTokens(tokensFase1);
        String codigoTransformadoFormateado = analizador.prettyPrintCode(tokensTransformados);
        System.out.println(codigoTransformadoFormateado);

        System.out.println("\nNUEVA TABLA DE SÍMBOLOS:\n");
        analizador.mostrarNuevasVariables();
        
        String archivoReglasRegex = "j2g_regex_rules.txt"; // Will be created in the current execution directory
        analizador.generarReglasRegex(archivoReglasRegex);
        
        System.out.println("\nValidación de estructura del código (usando reglas de " + archivoReglasRegex + "):");
        analizador.validarEstructuraConRegex(codigoLimpioFormateado, archivoReglasRegex);
    }

    private String leerArchivo(String nombreArchivo) {
        StringBuilder contenido = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(nombreArchivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                contenido.append(linea).append("\n");
            }
        } catch (IOException e) {
            System.err.println("Error al leer el archivo " + nombreArchivo + ": " + e.getMessage());
            return null;
        }
        return contenido.toString().trim();
    }

    public void cargarTabsim(String archivoTabsim) {
        try (BufferedReader br = new BufferedReader(new FileReader(archivoTabsim))) {
            String linea;
            br.readLine(); 
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split("\t");
                if (partes.length >= 3) { 
                    String variable = partes[0].trim();
                    String id = partes[1].trim();
                    String tipo = partes[2].trim();
                    String valor = (partes.length == 4) ? partes[3].trim() : "";
                    SymbolTableEntry entry = new SymbolTableEntry(variable, id, tipo, valor);
                    tabsimBase.put(variable, entry); 
                    palabrasReservadasYSimbolosConocidos.add(variable);
                }
            }
        } catch (IOException e) {
            System.err.println("Error al cargar tabsim.txt: " + e.getMessage());
        }
    }

    public List<String> fase1_limpiarYTokenizar(String codigo) {
        String codigoSinComentarios = codigo.replaceAll("/\\*[\\s\\S]*?\\*/", "").replaceAll("//.*", "");
        List<String> tabsimTokens = new ArrayList<>(palabrasReservadasYSimbolosConocidos);
        tabsimTokens.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));

        StringBuilder regexBuilder = new StringBuilder();
        regexBuilder.append("(\"(?:\\\\.|[^\"\\\\])*\")"); // String literals

        for (String token : tabsimTokens) {
            regexBuilder.append("|(").append(Pattern.quote(token)).append(")");
        }
        
        regexBuilder.append("|([a-zA-Z_][a-zA-Z0-9_]*)"); // Identifiers
        regexBuilder.append("|([0-9]+)"); // Numbers

        Pattern tokenPattern = Pattern.compile(regexBuilder.toString());
        Matcher matcher = tokenPattern.matcher(codigoSinComentarios);
        
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) { 
                if (matcher.group(i) != null) {
                    tokens.add(matcher.group(i));
                    break; 
                }
            }
        }
        return tokens;
    }

    public List<String> fase2_transformarTokens(List<String> tokensOriginales) {
        List<String> transformedTokens = new ArrayList<>();
        String lastDeclaredType = null; 

        for (int i = 0; i < tokensOriginales.size(); i++) {
            String token = tokensOriginales.get(i);
            String tokenToEmit = token;

            if (token.equals("INT") || token.equals("STR") || token.equals("BOOL")) {
                lastDeclaredType = token.toLowerCase();
            } else if (token.equals(";")) {
                lastDeclaredType = null; 
            }

            if (token.matches("\"(?:\\\\.|[^\"\\\\])*\"")) { // String literal
                String id;
                String stringContent = token.substring(1, token.length() - 1).replace("\\\"", "\""); 
                if (literalToIdMap.containsKey(token)) { 
                    id = literalToIdMap.get(token);
                } else {
                    id = "id" + nextIdCounter++;
                    literalToIdMap.put(token, id); 
                    nuevasVariablesDetectadas.add(new SymbolTableEntry(token, id, "string", stringContent));
                }
                tokenToEmit = id;
            }
            else if (palabrasReservadasYSimbolosConocidos.contains(token)) {
                // Keyword or known symbol, do not transform
            } else if (token.matches("[0-9]+")) { // Number literal
                String id;
                if (literalToIdMap.containsKey(token)) {
                    id = literalToIdMap.get(token);
                } else {
                    id = "id" + nextIdCounter++;
                    literalToIdMap.put(token, id);
                    nuevasVariablesDetectadas.add(new SymbolTableEntry(token, id, "int", token));
                }
                tokenToEmit = id;
            } else if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*")) { // Identifier
                String id;
                if (variableToIdMap.containsKey(token)) {
                    id = variableToIdMap.get(token);
                } else {
                    id = "id" + nextIdCounter++;
                    variableToIdMap.put(token, id);
                    String tipoVar = (lastDeclaredType != null) ? lastDeclaredType : "desconocido";
                    String valorParaTabla = token; 
                    // Check for immediate assignment to populate 'valor' in symbol table
                    if ( (i + 2 < tokensOriginales.size()) && tokensOriginales.get(i+1).equals(":=") ) {
                        String valorAsignadoToken = tokensOriginales.get(i+2);
                        // If assigned value is already an ID (from a literal or another var)
                        if (variableToIdMap.containsKey(valorAsignadoToken)) { 
                            valorParaTabla = variableToIdMap.get(valorAsignadoToken);
                        } else if (literalToIdMap.containsKey(valorAsignadoToken)) { 
                            valorParaTabla = literalToIdMap.get(valorAsignadoToken);
                        } else {
                             valorParaTabla = valorAsignadoToken; // Store original token if not an ID
                        }
                    }
                    nuevasVariablesDetectadas.add(new SymbolTableEntry(token, id, tipoVar, valorParaTabla));
                }
                tokenToEmit = id;
            }
            transformedTokens.add(tokenToEmit);
        }
        
        // Second pass to update 'valor' for variables assigned other variables
        for (SymbolTableEntry entry : nuevasVariablesDetectadas) {
            // Skip literals (int, string) whose 'variable' is the literal itself
            if (!(entry.tipo.equals("int") && entry.variable.equals(entry.valor)) && 
                !(entry.tipo.equals("string") && entry.variable.startsWith("\""))) {
                
                // If the 'valor' field currently holds a variable name that was later mapped to an ID
                if (variableToIdMap.containsKey(entry.valor)) { 
                    entry.valor = variableToIdMap.get(entry.valor);
                } else if (literalToIdMap.containsKey(entry.valor)) { // Or if it holds a literal that was mapped
                    entry.valor = literalToIdMap.get(entry.valor);
                }
            }
        }
        return transformedTokens;
    }
    
    private String prettyPrintCode(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;
        final String indentUnit = "  "; 
        boolean atStartOfLine = true;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String nextToken = (i + 1 < tokens.size()) ? tokens.get(i + 1) : "";

            if (token.equals("}")) {
                indentLevel = Math.max(0, indentLevel - 1);
                if (!atStartOfLine) { 
                    sb.append("\n");
                }
                appendIndent(sb, indentLevel, indentUnit);
            } else if (atStartOfLine) {
                // Avoid indenting 'else' if it immediately follows a '}' on the new line
                if (!(token.equals("else") && i > 0 && tokens.get(i-1).equals("}"))) {
                    appendIndent(sb, indentLevel, indentUnit);
                }
            }

            sb.append(token);
            atStartOfLine = false;

            if (token.equals("{")) {
                sb.append("\n");
                indentLevel++;
                atStartOfLine = true;
            } else if (token.equals(";")) {
                sb.append("\n");
                atStartOfLine = true;
            } else if (token.equals("}")) {
                // If 'else' is next, don't add newline yet, 'else' block will handle its placement
                if (!nextToken.equals("else")) {
                    sb.append("\n");
                    atStartOfLine = true;
                }
            } else {
                // Add space if next token is not a punctuation that should be close
                if (!nextToken.isEmpty() &&
                    !nextToken.equals(";") && !nextToken.equals(",") &&
                    !nextToken.equals(")") && !nextToken.equals("]") && !nextToken.equals("}") && !nextToken.equals("{") &&
                    !token.equals("(") && !token.equals("[") &&
                    // Avoid space for "Input . Method"
                    !(token.equals("Input") && nextToken.equals(".")) && 
                    !(i > 0 && tokens.get(i-1).equals("Input") && token.equals(".")) && 
                    !nextToken.equals(".") 
                   ) {
                    sb.append(" ");
                }
            }
        }
        return sb.toString().replaceAll("\\s+\n", "\n").replaceAll("\n\\s*\n", "\n").trim();
    }

    private void appendIndent(StringBuilder sb, int indentLevel, String indentUnit) {
        for (int j = 0; j < indentLevel; j++) {
            sb.append(indentUnit);
        }
    }

    public void mostrarNuevasVariables() {
        System.out.println(String.format("%-20s | %-10s | %-10s | %-20s", "VARIABLE", "ID", "TIPO", "VALOR"));
        System.out.println(new String(new char[65]).replace("\0", "-"));
        
        Map<String, SymbolTableEntry> displayOrderMap = new LinkedHashMap<>();
        // Literals first
        for(SymbolTableEntry entry : nuevasVariablesDetectadas){
            if(entry.tipo.equals("int") || entry.tipo.equals("string") || entry.tipo.equals("bool")){
                 displayOrderMap.putIfAbsent(entry.id, entry); 
            }
        }
        // Then other variables
        for(SymbolTableEntry entry : nuevasVariablesDetectadas){
            if(!(entry.tipo.equals("int") || entry.tipo.equals("string") || entry.tipo.equals("bool"))){
                 displayOrderMap.putIfAbsent(entry.id, entry);
            }
        }

        for (SymbolTableEntry entry : displayOrderMap.values()) {
            System.out.println(entry);
        }
    }
    
    public void generarReglasRegex(String nombreArchivo) {
        // Regex for variable names and IDs
        String varOrIdRegex = "([a-z][a-zA-Z0-9_]{0,63}|id[0-9]+)";
        // Regex for an optional string literal prompt: (\STRING_LITERAL\)?
        String optionalPromptRegex = "(?:\\((\"(?:\\\\.|[^\"\\\\])*\")\\))?";


        try (PrintWriter out = new PrintWriter(new FileWriter(nombreArchivo))) {
            out.println("### Archivo de Expresiones Regulares para J2G ###");
            out.println("\n# Definición de Variables");
            out.println("VAR_NAME=^[a-z][a-zA-Z0-9_]{0,63}$"); 
            out.println("ID_NAME=^id[0-9]+$");                
            out.println("VALID_LHS_VAR=^" + varOrIdRegex + "$"); 

            out.println("\n# Literales y Constantes");
            out.println("STRING_LITERAL=\"(?:\\\\.|[^\"\\\\])*\"");
            out.println("NUMBER_LITERAL=[0-9]+");
            out.println("BOOLEAN_LITERAL=(TRUE|FALSE|true|false)"); 
            out.println("ANY_LITERAL_OR_VAR=(" + varOrIdRegex + "|(\"(?:\\\\.|[^\"\\\\])*\")|([0-9]+)|(TRUE|FALSE|true|false))");


            out.println("\n# Estructura Principal del Programa");
            out.println("MAIN_FUNC_START=^FUNC\\s+J2G\\s+Main\\s*\\(\\s*\\)\\s*\\{"); 
            out.println("BLOCK_END=^\\}$"); 


            out.println("\n# Declaración de Variables");
            // Group 1: Tipo (INT|STR|BOOL), Group 2: Nombre de variable
            out.println("VAR_DECL_NO_INIT=^(INT|STR|BOOL)\\s+" + varOrIdRegex + "\\s*;");
            // Group 1: Tipo, Group 2: Nombre, Group 3: Valor completo, Group 4: String lit, Group 5: Num lit, Group 6: Bool lit, Group 7: Var/ID en RHS
            out.println("VAR_DECL_CON_INIT=^(INT|STR|BOOL)\\s+" + varOrIdRegex + "\\s*:=\\s*((\"(?:\\\\.|[^\"\\\\])*\")|([0-9]+)|(TRUE|FALSE|true|false)|" + varOrIdRegex + ")\\s*;");
            
            // Group 1: LHS Var/ID, Group 2: Operador, Group 3: RHS Expresión
            out.println("ASSIGNMENT=^" + varOrIdRegex + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*(.+)\\s*;");


            out.println("\n# Funciones Predefinidas de Input");
            // INPUT_..._STMT Regexes:
            // Optional Group 1: LHS var/ID for assignment
            // Optional Group 2 (nested in optional prompt group): String literal for prompt
            out.println("INPUT_STR_STMT=^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Str\\(\\)\\s*;");
            out.println("INPUT_INT_STMT=^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Int\\(\\)\\s*;");
            out.println("INPUT_BOOL_STMT=^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Bool\\(\\)\\s*;");


            out.println("\n# Estructuras de Control");
            out.println("IF_STMT=^if\\s*\\((.+)\\)\\s*\\{"); 
            out.println("ELSE_STMT=^else\\s*\\{");
            out.println("BLOCK_END_ELSE_STMT=^\\}\\s*else\\s*\\{"); 
            
            out.println("SWITCH_STMT=^sw\\s*\\((.+)\\)\\s*\\{");
            out.println("CASE_STMT=^caso\\s+(\"(?:\\\\.|[^\"\\\\])*\"|" + varOrIdRegex + ")\\s*:"); // Case can be string or var/id
            out.println("DEFAULT_STMT=^por_defecto\\s*:");
            out.println("DETENER_STMT=^detener\\s*;");

            out.println("FOR_STMT=^for\\s*\\((.*);\\s*(.*);\\s*(.*)\\)\\s*\\{"); 
            out.println("WHILE_STMT=^while\\s*\\((.+)\\)\\s*\\{");
            out.println("DO_WHILE_DO_STMT=^do\\s*\\{");
            out.println("DO_WHILE_WHILE_STMT=^\\}\\s*while\\s*\\((.+)\\)\\s*;");

            out.println("\n# Funciones Predefinidas");
            out.println("PRINT_STMT=^Print\\s*\\((.*?)\\)\\s*;"); 

        } catch (IOException e) {
            System.err.println("Error al generar archivo de reglas regex: " + e.getMessage());
        }
    }
    
    private boolean isValidSourceVarName(String name, Map<String, String> reglas) {
        return name.matches(reglas.getOrDefault("VAR_NAME", "^[a-z][a-zA-Z0-9_]{0,63}$"));
    }

    private boolean isGeneratedIdName(String name, Map<String, String> reglas) {
        return name.matches(reglas.getOrDefault("ID_NAME", "^id[0-9]+$"));
    }
    
    private List<String> checkVariableUsage(String varName, String context, Map<String, String> reglas, Map<String, String> declaredVariablesTypeMap) {
        List<String> errors = new ArrayList<>();
        if (palabrasReservadasYSimbolosConocidos.contains(varName)) {
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
        if (expression == null || expression.trim().isEmpty()) {
            return errors;
        }

        List<String> localTabsimTokens = new ArrayList<>(palabrasReservadasYSimbolosConocidos);
        localTabsimTokens.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));

        StringBuilder regexBuilder = new StringBuilder();
        int currentGroup = 1; 

        final int STRING_LITERAL_GROUP = currentGroup++;
        regexBuilder.append("(\"(?:\\\\.|[^\"\\\\])*\")"); 

        Map<Integer, String> keywordOperatorGroupMap = new HashMap<>();
        for (String token : localTabsimTokens) {
            keywordOperatorGroupMap.put(currentGroup, token);
            regexBuilder.append("|(").append(Pattern.quote(token)).append(")");
            currentGroup++;
        }

        final int IDENTIFIER_GROUP = currentGroup++;
        regexBuilder.append("|([a-zA-Z_][a-zA-Z0-9_]*)"); 

        final int NUMBER_GROUP = currentGroup++;
        regexBuilder.append("|([0-9]+)"); 

        Pattern tokenPattern = Pattern.compile(regexBuilder.toString());
        Matcher matcher = tokenPattern.matcher(expression);

        while (matcher.find()) {
            if (matcher.group(STRING_LITERAL_GROUP) != null) {
                continue;
            }
            if (matcher.group(NUMBER_GROUP) != null) {
                continue;
            }

            boolean isKeywordOrOperator = false;
            for (int groupIndex : keywordOperatorGroupMap.keySet()) {
                if (matcher.group(groupIndex) != null) {
                    isKeywordOrOperator = true;
                    break;
                }
            }
            if (isKeywordOrOperator) {
                continue;
            }

            if (matcher.group(IDENTIFIER_GROUP) != null) {
                String varName = matcher.group(IDENTIFIER_GROUP);
                // Do not validate "Input", "Str", "Int", "Bool" as variables in this context
                if (!varName.equals("Input") && !varName.equals("Str") && !varName.equals("Int") && !varName.equals("Bool")) {
                    errors.addAll(checkVariableUsage(varName, context, reglas, declaredVariablesTypeMap));
                }
            }
        }
        return errors;
    }

    public void validarEstructuraConRegex(String codigoLimpioFormateado, String archivoReglasRegex) {
        Map<String, String> reglas = new HashMap<>();
        Map<String, String> declaredVariablesTypeMap = new HashMap<>(); 
        int braceBalance = 0; 
        List<String> globalStructuralErrors = new ArrayList<>(); 

        try (BufferedReader br = new BufferedReader(new FileReader(archivoReglasRegex))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.trim().isEmpty() || linea.startsWith("#")) continue;
                String[] partes = linea.split("=", 2);
                if (partes.length == 2) reglas.put(partes[0].trim(), partes[1].trim());
            }
        } catch (IOException e) {
            System.err.println("Error al leer archivo de reglas regex: " + e.getMessage());
            return;
        }

        String[] lineasCodigo = codigoLimpioFormateado.split("\n");
        boolean erroresGlobalesEncontrados = false;
        System.out.println("--- Errores de Estructura Encontrados ---");

        for (int i = 0; i < lineasCodigo.length; i++) {
            String lineaActual = lineasCodigo[i].trim(); 
            String originalLineForBraceCheck = lineasCodigo[i]; 
            
            if (lineaActual.isEmpty()) continue;

            boolean reglaCoincidio = false;
            List<String> errorsEnLinea = new ArrayList<>();
            Matcher matcher;

            for (char ch : originalLineForBraceCheck.toCharArray()) {
                if (ch == '{') braceBalance++;
                else if (ch == '}') braceBalance--;
            }
            if (braceBalance < 0 && !lineaActual.matches(reglas.getOrDefault("BLOCK_END_ELSE_STMT","^$"))) { // BLOCK_END_ELSE_STMT naturally has } else {
                boolean alreadyReported = errorsEnLinea.stream().anyMatch(err -> err.contains("Llave de cierre '}' inesperada"));
                if(!alreadyReported) {
                    errorsEnLinea.add("Error de sintaxis: Llave de cierre '}' inesperada o sin pareja de apertura en o antes de esta línea.");
                }
                braceBalance = 0; 
            }

            // 1. Declaraciones
            matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_NO_INIT", "^$")).matcher(lineaActual);
            if (matcher.matches()) {
                reglaCoincidio = true;
                String varDeclaredType = matcher.group(1);
                String varName = matcher.group(2);
                if (!isValidSourceVarName(varName, reglas) && !isGeneratedIdName(varName, reglas)) {
                    errorsEnLinea.add("Nombre de variable inválido '" + varName + "' en declaración sin inicialización.");
                } else {
                    if (declaredVariablesTypeMap.containsKey(varName)) {
                        errorsEnLinea.add("Variable '" + varName + "' ya declarada.");
                    } else {
                        declaredVariablesTypeMap.put(varName, varDeclaredType); 
                    }
                }
            }

            if (!reglaCoincidio) {
                matcher = Pattern.compile(reglas.getOrDefault("VAR_DECL_CON_INIT", "^$")).matcher(lineaActual);
                if (matcher.matches()) {
                    reglaCoincidio = true;
                    String varDeclaredType = matcher.group(1); 
                    String varName = matcher.group(2); 
                    
                    if (!isValidSourceVarName(varName, reglas) && !isGeneratedIdName(varName, reglas)) {
                        errorsEnLinea.add("Nombre de variable inválido '" + varName + "' en declaración con inicialización.");
                    } else {
                        if (declaredVariablesTypeMap.containsKey(varName)) {
                            errorsEnLinea.add("Variable '" + varName + "' ya declarada.");
                        } else {
                            declaredVariablesTypeMap.put(varName, varDeclaredType);
                        }
                    }

                    String assignedValueType = "UNKNOWN";
                    String stringLiteralValue = matcher.group(4); 
                    String numberLiteralValue = matcher.group(5); 
                    String booleanLiteralValue = matcher.group(6); 
                    String rhsVarName = matcher.group(7); 

                    if (stringLiteralValue != null) assignedValueType = "STR";
                    else if (numberLiteralValue != null) assignedValueType = "INT";
                    else if (booleanLiteralValue != null) assignedValueType = "BOOL";
                    else if (rhsVarName != null && !rhsVarName.isEmpty()) {
                        errorsEnLinea.addAll(checkVariableUsage(rhsVarName, "en lado derecho de inicialización", reglas, declaredVariablesTypeMap));
                        assignedValueType = declaredVariablesTypeMap.getOrDefault(rhsVarName, "UNKNOWN_VAR_TYPE");
                    }

                    if (!assignedValueType.startsWith("UNKNOWN") && declaredVariablesTypeMap.containsKey(varName)) {
                        if (!varDeclaredType.equals(assignedValueType)) {
                             errorsEnLinea.add("Error de tipo: Variable '" + varName + "' ("+varDeclaredType+") no puede inicializarse con tipo " + assignedValueType + ".");
                        }
                    }
                }
            }
            
            // 2. Input Statements (antes de asignación general)
            String[] inputTypes = {"STR", "INT", "BOOL"};
            String[] inputMethods = {"Str", "Int", "Bool"};

            for (int k = 0; k < inputTypes.length && !reglaCoincidio; k++) {
                matcher = Pattern.compile(reglas.getOrDefault("INPUT_" + inputTypes[k] + "_STMT", "^$")).matcher(lineaActual);
                if (matcher.matches()) {
                    reglaCoincidio = true;
                    String lhsVar = matcher.group(1); // Puede ser null si no hay asignación
                    // String prompt = matcher.group(2); // Prompt, no necesita validación de variable

                    if (lhsVar != null && !lhsVar.isEmpty()) {
                        List<String> lhsErrors = checkVariableUsage(lhsVar, "en lado izquierdo de asignación con Input()." + inputMethods[k] + "()", reglas, declaredVariablesTypeMap);
                        errorsEnLinea.addAll(lhsErrors);

                        if (lhsErrors.isEmpty() && declaredVariablesTypeMap.containsKey(lhsVar)) {
                            String lhsDeclaredType = declaredVariablesTypeMap.get(lhsVar);
                            if (!lhsDeclaredType.equals(inputTypes[k])) {
                                errorsEnLinea.add("Error de tipo: Variable '" + lhsVar + "' (" + lhsDeclaredType + ") no puede asignarse con Input()." + inputMethods[k] + "() que devuelve " + inputTypes[k] + ".");
                            }
                        }
                    }
                    // Si no hay lhsVar, es una llamada a Input().Method() sin asignación, lo cual es estructuralmente válido por la regex.
                    break; 
                }
            }


            // 3. Asignación General
            if (!reglaCoincidio) {
                matcher = Pattern.compile(reglas.getOrDefault("ASSIGNMENT", "^$")).matcher(lineaActual);
                if (matcher.matches()) {
                    reglaCoincidio = true;
                    String lhsVar = matcher.group(1); 
                    String operator = matcher.group(2);
                    String rhsExpression = matcher.group(3); 

                    List<String> lhsErrors = checkVariableUsage(lhsVar, "en lado izquierdo de asignación", reglas, declaredVariablesTypeMap);
                    errorsEnLinea.addAll(lhsErrors);
                    
                    errorsEnLinea.addAll(checkExpressionVariables(rhsExpression, "en lado derecho de asignación", reglas, declaredVariablesTypeMap));

                    if (operator.equals(":=") && lhsErrors.isEmpty() && declaredVariablesTypeMap.containsKey(lhsVar)) {
                        String lhsType = declaredVariablesTypeMap.get(lhsVar);
                        String rhsType = "UNKNOWN_EXPR_TYPE"; 

                        Matcher rhsSingleTokenMatcher = Pattern.compile("^\\s*(" + reglas.getOrDefault("ANY_LITERAL_OR_VAR","") + ")\\s*$").matcher(rhsExpression.trim());
                        if(rhsSingleTokenMatcher.matches()){
                             String rhsCleanToken = rhsSingleTokenMatcher.group(1); // El grupo capturado por ANY_LITERAL_OR_VAR
                            if (rhsCleanToken.matches(reglas.getOrDefault("STRING_LITERAL", "^$"))) rhsType = "STR";
                            else if (rhsCleanToken.matches(reglas.getOrDefault("NUMBER_LITERAL", "^$"))) rhsType = "INT";
                            else if (rhsCleanToken.matches(reglas.getOrDefault("BOOLEAN_LITERAL", "^$"))) rhsType = "BOOL";
                            else if (declaredVariablesTypeMap.containsKey(rhsCleanToken)) rhsType = declaredVariablesTypeMap.get(rhsCleanToken);
                            else if (isValidSourceVarName(rhsCleanToken, reglas) || isGeneratedIdName(rhsCleanToken, reglas)) {
                                rhsType = "UNKNOWN_VAR_TYPE";
                            }
                        }
                        
                        if (!rhsType.startsWith("UNKNOWN")) {
                            if (!lhsType.equals(rhsType)) {
                                errorsEnLinea.add("Error de tipo: Variable '" + lhsVar + "' (" + lhsType + ") no puede asignarse con tipo " + rhsType + ".");
                            }
                        }
                    } else if (operator.matches("\\+=|-=|\\*=|/=")) { 
                        if (lhsErrors.isEmpty() && declaredVariablesTypeMap.containsKey(lhsVar) && !declaredVariablesTypeMap.get(lhsVar).equals("INT")) {
                            errorsEnLinea.add("Error de tipo: Operador '" + operator + "' solo puede usarse con variables INT. '" + lhsVar + "' es " + declaredVariablesTypeMap.get(lhsVar) + ".");
                        }
                    }
                }
            }
            
            // 4. Estructuras de Control y Print
            if(!reglaCoincidio) {
                String[] controlStructs = {"IF_STMT", "WHILE_STMT", "PRINT_STMT", "SWITCH_STMT", "FOR_STMT", "DO_WHILE_WHILE_STMT"};
                String[] contextMessages = {"en condición de if", "en condición de while", "en argumentos de Print", "en expresión de switch", "en expresiones de for", "en condición de do-while"};

                for(int k=0; k < controlStructs.length && !reglaCoincidio; k++){
                    matcher = Pattern.compile(reglas.getOrDefault(controlStructs[k], "^$")).matcher(lineaActual);
                    if (matcher.matches()) {
                        reglaCoincidio = true;
                        if (matcher.groupCount() > 0) { // Si la regex tiene un grupo para la expresión/condición
                             String expressionPart = matcher.group(1);
                             if (controlStructs[k].equals("FOR_STMT")) { // For tiene 3 expresiones
                                 errorsEnLinea.addAll(checkExpressionVariables(matcher.group(1), contextMessages[k] + " (inicialización)", reglas, declaredVariablesTypeMap));
                                 errorsEnLinea.addAll(checkExpressionVariables(matcher.group(2), contextMessages[k] + " (condición)", reglas, declaredVariablesTypeMap));
                                 errorsEnLinea.addAll(checkExpressionVariables(matcher.group(3), contextMessages[k] + " (incremento)", reglas, declaredVariablesTypeMap));
                             } else {
                                errorsEnLinea.addAll(checkExpressionVariables(expressionPart, contextMessages[k], reglas, declaredVariablesTypeMap));
                             }
                        }
                        break;
                    }
                }
            }
            
            // 5. Fallback para errores si ninguna regla estructural principal coincidió y no hay errores específicos aún
            if (!reglaCoincidio && errorsEnLinea.isEmpty()) { 
                Matcher assignmentAttemptMatcher = Pattern.compile("^(.*?)\\s*(:=|\\+=|-=|\\*=|/=)\\s*(.*?);$").matcher(lineaActual);
                if (assignmentAttemptMatcher.matches()) {
                    String lhsCandidate = assignmentAttemptMatcher.group(1).trim();
                    String rhsCandidate = assignmentAttemptMatcher.group(3).trim();

                    if (!isValidSourceVarName(lhsCandidate, reglas) && !isGeneratedIdName(lhsCandidate, reglas)) {
                        errorsEnLinea.add("Nombre de variable inválido '" + lhsCandidate + "' en lado izquierdo de posible asignación.");
                    } else { 
                        if (isValidSourceVarName(lhsCandidate, reglas) && !declaredVariablesTypeMap.containsKey(lhsCandidate)) {
                            errorsEnLinea.add("Variable '" + lhsCandidate + "' no declarada en lado izquierdo de posible asignación.");
                        }
                    }
                    errorsEnLinea.addAll(checkExpressionVariables(rhsCandidate, "en lado derecho de posible asignación", reglas, declaredVariablesTypeMap));
                    
                    if (errorsEnLinea.isEmpty()) { 
                        errorsEnLinea.add("Estructura de asignación general inválida.");
                    }
                } else {
                    boolean isKnownSimpleStructure = false;
                    String[] simpleStructures = {"MAIN_FUNC_START", "BLOCK_END", "ELSE_STMT", "BLOCK_END_ELSE_STMT", 
                                                 "CASE_STMT", "DEFAULT_STMT", "DETENER_STMT", "DO_WHILE_DO_STMT"}; 
                    for(String simpleKey : simpleStructures){
                        if(lineaActual.matches(reglas.getOrDefault(simpleKey, "^$"))){
                            isKnownSimpleStructure = true;
                            break;
                        }
                    }
                    if(!isKnownSimpleStructure){
                        errorsEnLinea.add("Error de estructura general en la línea (no coincide con ninguna regla conocida).");
                    }
                }
            }

            if (!errorsEnLinea.isEmpty()) {
                System.out.println("Error(es) en línea " + (i + 1) + ": " + lineasCodigo[i]); 
                for (String error : errorsEnLinea) {
                    System.out.println("  - " + error);
                }
                erroresGlobalesEncontrados = true;
            }
        }

        if (braceBalance > 0) {
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Faltan " + braceBalance + " llave(s) de cierre '}'.");
        } else if (braceBalance < 0) { 
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Hay " + (-braceBalance) + " llave(s) de cierre '}' en exceso o sin pareja de apertura.");
        }

        if (!globalStructuralErrors.isEmpty()) {
            for (String error : globalStructuralErrors) {
                System.out.println(error);
            }
            erroresGlobalesEncontrados = true;
        }

        if (!erroresGlobalesEncontrados && globalStructuralErrors.isEmpty()) {
            System.out.println("No se encontraron errores de estructura.");
        }
        System.out.println("---------------------------------------");
    }
}