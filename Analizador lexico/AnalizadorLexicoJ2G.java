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

    // No se necesitan variables de estado de Main a nivel de instancia si se reinician en validarEstructuraConRegex

    public static void main(String[] args) {
        AnalizadorLexicoJ2G analizador = new AnalizadorLexicoJ2G();
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
        
        String archivoReglasRegex = "j2g_regex_rules.txt"; 
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
        regexBuilder.append("(\"(?:\\\\.|[^\"\\\\])*\")"); 

        for (String token : tabsimTokens) {
            regexBuilder.append("|(").append(Pattern.quote(token)).append(")");
        }
        
        regexBuilder.append("|([a-zA-Z_][a-zA-Z0-9_]*)"); 
        regexBuilder.append("|([0-9]+)"); 

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

            if (token.matches("\"(?:\\\\.|[^\"\\\\])*\"")) { 
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
            } else if (token.matches("[0-9]+")) { 
                String id;
                if (literalToIdMap.containsKey(token)) {
                    id = literalToIdMap.get(token);
                } else {
                    id = "id" + nextIdCounter++;
                    literalToIdMap.put(token, id);
                    nuevasVariablesDetectadas.add(new SymbolTableEntry(token, id, "int", token));
                }
                tokenToEmit = id;
            } else if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*")) { 
                String id;
                if (variableToIdMap.containsKey(token)) {
                    id = variableToIdMap.get(token);
                } else {
                    id = "id" + nextIdCounter++;
                    variableToIdMap.put(token, id);
                    String tipoVar = (lastDeclaredType != null) ? lastDeclaredType : "desconocido";
                    String valorParaTabla = token; 
                    if ( (i + 2 < tokensOriginales.size()) && tokensOriginales.get(i+1).equals(":=") ) {
                        String valorAsignadoToken = tokensOriginales.get(i+2);
                        if (variableToIdMap.containsKey(valorAsignadoToken)) { 
                            valorParaTabla = variableToIdMap.get(valorAsignadoToken);
                        } else if (literalToIdMap.containsKey(valorAsignadoToken)) { 
                            valorParaTabla = literalToIdMap.get(valorAsignadoToken);
                        } else {
                             valorParaTabla = valorAsignadoToken; 
                        }
                    }
                    nuevasVariablesDetectadas.add(new SymbolTableEntry(token, id, tipoVar, valorParaTabla));
                }
                tokenToEmit = id;
            }
            transformedTokens.add(tokenToEmit);
        }
        
        for (SymbolTableEntry entry : nuevasVariablesDetectadas) {
            if (!(entry.tipo.equals("int") && entry.variable.equals(entry.valor)) && 
                !(entry.tipo.equals("string") && entry.variable.startsWith("\""))) {
                
                if (variableToIdMap.containsKey(entry.valor)) { 
                    entry.valor = variableToIdMap.get(entry.valor);
                } else if (literalToIdMap.containsKey(entry.valor)) { 
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
                if (!nextToken.equals("else")) {
                    sb.append("\n");
                    atStartOfLine = true;
                }
            } else {
                if (!nextToken.isEmpty() &&
                    !nextToken.equals(";") && !nextToken.equals(",") &&
                    !nextToken.equals(")") && !nextToken.equals("]") && !nextToken.equals("}") && !nextToken.equals("{") &&
                    !token.equals("(") && !token.equals("[") &&
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
        for(SymbolTableEntry entry : nuevasVariablesDetectadas){
            if(entry.tipo.equals("int") || entry.tipo.equals("string") || entry.tipo.equals("bool")){
                 displayOrderMap.putIfAbsent(entry.id, entry); 
            }
        }
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
        String varOrIdRegex = "([a-z][a-zA-Z0-9_]{0,63}|id[0-9]+)";
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
            out.println("VAR_DECL_NO_INIT=^(INT|STR|BOOL)\\s+" + varOrIdRegex + "\\s*;");
            out.println("VAR_DECL_CON_INIT=^(INT|STR|BOOL)\\s+" + varOrIdRegex + "\\s*:=\\s*((\"(?:\\\\.|[^\"\\\\])*\")|([0-9]+)|(TRUE|FALSE|true|false)|" + varOrIdRegex + ")\\s*;");
            out.println("ASSIGNMENT=^" + varOrIdRegex + "\\s*(:=|\\+=|-=|\\*=|\\/=)\\s*(.+)\\s*;");

            out.println("\n# Funciones Predefinidas de Input");
            out.println("INPUT_STR_STMT=^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Str\\(\\)\\s*;");
            out.println("INPUT_INT_STMT=^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Int\\(\\)\\s*;");
            out.println("INPUT_BOOL_STMT=^(?:" + varOrIdRegex + "\\s*:=\\s*)?Input\\s*" + optionalPromptRegex + "\\.Bool\\(\\)\\s*;");

            out.println("\n# Estructuras de Control");
            out.println("IF_STMT=^if\\s*\\((.+)\\)\\s*\\{"); 
            out.println("ELSE_STMT=^else\\s*\\{");
            out.println("BLOCK_END_ELSE_STMT=^\\}\\s*else\\s*\\{"); 
            out.println("SWITCH_STMT=^sw\\s*\\((.+)\\)\\s*\\{");
            out.println("CASE_STMT=^caso\\s+(\"(?:\\\\.|[^\"\\\\])*\"|" + varOrIdRegex + ")\\s*:"); 
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
        if (expression == null || expression.trim().isEmpty()) return errors;

        List<String> localTabsimTokens = new ArrayList<>(palabrasReservadasYSimbolosConocidos);
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

    public void validarEstructuraConRegex(String codigoLimpioFormateado, String archivoReglasRegex) {
        Map<String, String> reglas = new HashMap<>();
        Map<String, String> declaredVariablesTypeMap = new HashMap<>(); 
        int globalBraceBalance = 0; 
        List<String> globalStructuralErrors = new ArrayList<>(); 

        // Variables de estado para la validación del bloque Main
        boolean mainFunctionDeclared = false;
        boolean currentlyInMainFunctionBlock = false;
        int mainFunctionBlockBraceDepth = 0; // Profundidad de llaves *dentro* del bloque Main

        try (BufferedReader br = new BufferedReader(new FileReader(archivoReglasRegex))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.trim().isEmpty() || linea.startsWith("#")) continue;
                String[] partes = linea.split("=", 2);
                if (partes.length == 2) reglas.put(partes[0].trim(), partes[1].trim());
            }
        } catch (IOException e) { System.err.println("Error al leer archivo de reglas regex: " + e.getMessage()); return; }

        String[] lineasCodigo = codigoLimpioFormateado.split("\n");
        boolean erroresEncontradosEnGeneral = false;
        System.out.println("--- Errores de Estructura Encontrados ---");

        for (int i = 0; i < lineasCodigo.length; i++) {
            String lineaActual = lineasCodigo[i].trim(); 
            String originalLineForBraceCheck = lineasCodigo[i]; 
            if (lineaActual.isEmpty()) continue;

            boolean reglaCoincidioEstaLinea = false;
            List<String> errorsEnLinea = new ArrayList<>();
            Matcher matcher;

            // Actualizar balance global de llaves
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

            // Lógica de validación del bloque Main
            if (lineaActual.matches(reglas.getOrDefault("MAIN_FUNC_START", "^$"))) {
                if (mainFunctionDeclared) { errorsEnLinea.add("Error ESTRUCTURAL: Múltiples definiciones de 'FUNC J2G Main()'."); }
                if (currentlyInMainFunctionBlock) {errorsEnLinea.add("Error ESTRUCTURAL: Definición de 'FUNC J2G Main()' anidada no permitida.");}
                // Código antes de Main (si no está vacío) ya debería haber sido marcado como error
                
                mainFunctionDeclared = true;
                currentlyInMainFunctionBlock = true;
                mainFunctionBlockBraceDepth = 1; // { de Main
                reglaCoincidioEstaLinea = true;
            } else if (currentlyInMainFunctionBlock) {
                // Estamos dentro del bloque Main. Validar sentencias.
                
                // Ajustar profundidad de llaves DENTRO de Main
                // (esto es un poco simplificado, asume que '{' siempre abre un nuevo nivel y '}' lo cierra)
                if (lineaActual.endsWith("{")) { // Una forma simple de detectar apertura de bloque
                    // Evitar doble conteo si MAIN_FUNC_START ya lo hizo (no debería pasar si MAIN_FUNC_START es la primera línea del bloque)
                    // Y evitar contar si es el '{' del propio Main, que ya se contó.
                    // Esta condición es para bloques internos como if {, while {, etc.
                     if (!lineaActual.matches(reglas.getOrDefault("MAIN_FUNC_START", "^$"))) { // No es el { del Main mismo
                        boolean isBlockOpeningStmt = false;
                        String[] blockOpeners = {"IF_STMT", "WHILE_STMT", "FOR_STMT", "DO_WHILE_DO_STMT", "ELSE_STMT", "SWITCH_STMT", "BLOCK_END_ELSE_STMT"};
                        for (String openerKey : blockOpeners) {
                            if (lineaActual.matches(reglas.getOrDefault(openerKey, "^$"))) {
                                isBlockOpeningStmt = true;
                                break;
                            }
                        }
                        if (isBlockOpeningStmt) {
                             mainFunctionBlockBraceDepth++;
                        }
                    }
                }
                // El } del final del bloque Main se maneja más abajo.
                // El } de bloques internos decrementará mainFunctionBlockBraceDepth.

                // VALIDACIONES DE SENTENCIAS (solo si currentlyInMainFunctionBlock es true)
                // 1. Declaraciones
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
                
                // 2. Input Statements
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

                // 3. Asignación General
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
                
                // 4. Otras Estructuras
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

                // Manejo de cierre de bloque DENTRO de Main
                if (lineaActual.matches(reglas.getOrDefault("BLOCK_END", "^$"))) { // Linea es solo "}"
                    mainFunctionBlockBraceDepth--; // Decrementa por el '}'
                    if (mainFunctionBlockBraceDepth == 0) { // Si este '}' cierra el bloque Main
                        currentlyInMainFunctionBlock = false; 
                    } else if (mainFunctionBlockBraceDepth < 0) {
                        // Esto sería un '}' extra dentro de Main que desbalancea el conteo específico de Main
                        // El balance global ya lo detectaría, pero aquí podríamos ser más específicos.
                        // errorsEnLinea.add("Error ESTRUCTURAL: Llave de cierre '}' extra o mal balanceada dentro del bloque Main.");
                        mainFunctionBlockBraceDepth = 0; // Corregir para evitar errores en cascada del contador de Main.
                    }
                    reglaCoincidioEstaLinea = true; // Un "}" es una estructura válida en sí misma.
                }


            } else { // No es MAIN_FUNC_START y no estamos actualmente en el bloque Main
                if (!lineaActual.isEmpty()) { 
                    if (!mainFunctionDeclared) {
                        errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual + "' encontrado antes de la definición de 'FUNC J2G Main()'.");
                    } else { // Main fue declarado, pero ya salimos de su bloque (currentlyInMainFunctionBlock es false)
                        errorsEnLinea.add("Error ESTRUCTURAL: Código '" + lineaActual + "' encontrado después del cierre del bloque 'FUNC J2G Main()'.");
                    }
                }
                // No se considera reglaCoincidioEstaLinea = true aquí, el error es de posicionamiento.
            }

            // Fallback para errores si ninguna regla estructural principal coincidió y no hay errores específicos aún,
            // pero SÍ estamos supuestamente dentro de Main.
            if (!reglaCoincidioEstaLinea && errorsEnLinea.isEmpty() && !lineaActual.isEmpty() && mainFunctionDeclared && currentlyInMainFunctionBlock) {
                 errorsEnLinea.add("Error de estructura general en la línea dentro de Main (no coincide con ninguna regla conocida): " + lineaActual);
            }


            if (!errorsEnLinea.isEmpty()) {
                System.out.println("Error(es) en línea " + (i + 1) + ": " + lineasCodigo[i]); 
                for (String error : errorsEnLinea) System.out.println("  - " + error);
                erroresEncontradosEnGeneral = true;
            }
        } // Fin del bucle for sobre las líneas

        // Validaciones Globales después de procesar todas las líneas
        if (!mainFunctionDeclared) {
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: No se encontró la función principal 'FUNC J2G Main() {}'.");
        } else if (mainFunctionBlockBraceDepth != 0) { 
            // Si mainFunctionBlockBraceDepth > 0, faltan '}' para Main.
            // Si < 0, algo muy raro pasó con el conteo específico de Main, el globalBraceBalance debería haberlo capturado.
            globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: El bloque 'FUNC J2G Main()' no se cerró correctamente. Profundidad de llaves esperada para Main: 0, actual: " + mainFunctionBlockBraceDepth + ".");
        }

        if (globalBraceBalance != 0) { // Chequeo del balance global de llaves
            // No añadir este error si ya se reportó un problema con el cierre del bloque Main, para evitar redundancia.
            if (mainFunctionDeclared && mainFunctionBlockBraceDepth == 0) { // Solo si Main se declaró y cerró bien, pero hay otro desbalance
                 globalStructuralErrors.add("Error ESTRUCTURAL GLOBAL: Desbalance de llaves en el programa. Balance final global: " + globalBraceBalance + " (debería ser 0).");
            } else if (!mainFunctionDeclared) { // Si Main ni siquiera se declaró, el desbalance global es relevante
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