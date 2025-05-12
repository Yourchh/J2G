import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

// ProductionRule (si la usas aquí, sino puede ser de tu LRParser)
// class ProductionRule { ... }

public class J2GAnalyzer {

    private static final String TABSIM_FILE = "tabsim.txt";
    private static final String TABSIM_OUTPUT_FILE = "tabsim_output.txt";
    private static final List<Map<String, String>> TABSIM = new ArrayList<>();
    private static int nextIdCounter = 1; // Renombrado para claridad, usado por TABSIM

    // --- Métodos existentes de TABSIM y preprocesamiento (loadTABSIM, saveTABSIM, preprocess, validateAndExtractMainBlock) ---
    // Se asume que estos métodos están como los proporcionaste.
    // Solo haré un pequeño ajuste a addConstantToTABSIM y addVariableToTABSIM para usar nextIdCounter

    private static void loadTABSIM(String fileName) {
        TABSIM.clear(); // Limpiar para cargas múltiples si es necesario
        nextIdCounter = 1; // Reiniciar contador
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            int maxId = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 4) {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("VARIABLE", parts[0].trim());
                    entry.put("ID", parts[1].trim());
                    entry.put("TIPO", parts[2].trim());
                    entry.put("VALOR", parts[3].trim());
                    TABSIM.add(entry);
                    try {
                        if (parts[1].trim().startsWith("id")) {
                            int currentIdNum = Integer.parseInt(parts[1].trim().substring(2));
                            if (currentIdNum > maxId) {
                                maxId = currentIdNum;
                            }
                        }
                    } catch (NumberFormatException e) { /* ignorar si el ID no es numérico */ }
                }
            }
            nextIdCounter = maxId + 1; // Asegurar que los nuevos IDs sean únicos
        } catch (IOException e) {
            System.err.println("Error al cargar el archivo " + fileName + ": " + e.getMessage());
        }
    }
    
    private static void saveTABSIM(String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (Map<String, String> entry : TABSIM) {
                writer.write(entry.get("VARIABLE") + "\t" + entry.get("ID") + "\t" + entry.get("TIPO") + "\t" + entry.get("VALOR"));
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error al guardar TABSIM en " + fileName + ": " + e.getMessage());
        }
    }
     private static String preprocess(String code) {
        String[] lines = code.split("\\n");
        StringBuilder processedCode = new StringBuilder();

        for (String line : lines) {
            line = line.replaceAll("//.*", "").trim(); // Eliminar comentarios
            line = line.replaceAll("\\s{2,}", " "); // Normalizar espacios
            if (!line.isEmpty()) {
                processedCode.append(line).append("\n");
            }
        }
        return processedCode.toString().trim();
    }

    private static String validateAndExtractMainBlock(String code) {
        // Usar Pattern.quote para las llaves si se interpretan como metacaracteres de regex.
        // Sin embargo, aquí se usan literalmente. El DOTALL es importante.
        String mainPatternStr = "^FUNC\\s+J2G\\s+Main\\s*\\(\\s*\\)\\s*\\{(.*)\\}\\s*$";
        Pattern pattern = Pattern.compile(mainPatternStr, Pattern.DOTALL | Pattern.CASE_INSENSITIVE); // Case insensitive para FUNC J2G Main
        Matcher matcher = pattern.matcher(code);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        } else {
            throw new IllegalArgumentException("Error: El código no está encapsulado correctamente dentro de 'FUNC J2G Main () { ... }'.");
        }
    }


    private static String addConstantToTABSIM(String value, String type) {
        for (Map<String, String> entry : TABSIM) {
            if (entry.get("TIPO").equalsIgnoreCase(type) && entry.get("VALOR").equals(value)) { // Comparar valor directamente
                 // Y si es una "variable" que representa la constante
                if (entry.get("VARIABLE").equals(value)) return entry.get("ID");
            }
        }
        String identifier = "id" + nextIdCounter++;
        Map<String, String> entry = new HashMap<>();
        entry.put("VARIABLE", value); // Para constantes, la "variable" es el valor mismo
        entry.put("ID", identifier);
        entry.put("TIPO", type.toLowerCase());
        entry.put("VALOR", value);
        TABSIM.add(entry);
        // System.out.println("Constante/Cadena añadida a TABSIM: " + entry);
        return identifier;
    }

    private static void addVariableToTABSIM(String variableName, String type, String initialValueLiteral) {
        for (Map<String, String> entry : TABSIM) {
            if (entry.get("VARIABLE").equals(variableName)) {
                System.err.println("Advertencia: Variable '" + variableName + "' ya declarada. Se ignora la redeclaración.");
                return;
            }
        }
        String varId = "id" + nextIdCounter++;
        Map<String, String> entry = new HashMap<>();
        entry.put("VARIABLE", variableName);
        entry.put("ID", varId);
        entry.put("TIPO", type.toLowerCase());

        if (initialValueLiteral != null && !initialValueLiteral.isEmpty()) {
            // El valor inicial es un literal que debe ser reemplazado por su ID de TABSIM (si es constante/cadena)
            // o es una expresión que será manejada por replaceIdentifiers más tarde.
            // Por ahora, si es un literal simple, lo añadimos como constante.
            // Esto es una simplificación; un análisis de expresiones completo sería necesario aquí.
            if (type.equalsIgnoreCase("STR") && initialValueLiteral.startsWith("\"") && initialValueLiteral.endsWith("\"")) {
                entry.put("VALOR", addConstantToTABSIM(initialValueLiteral, "string"));
            } else if ((type.equalsIgnoreCase("INT") || type.equalsIgnoreCase("BOOL")) && initialValueLiteral.matches("\\d+|TRUE|FALSE")) {
                 entry.put("VALOR", addConstantToTABSIM(initialValueLiteral, type));
            } else {
                 // Es una expresión o variable, se resolverá más tarde. Guardamos el literal/expresión por ahora.
                 // O mejor, dejamos VALOR como el ID de la variable misma si no hay un valor literal simple.
                 // Para el reemplazo, la asignación se encargará.
                 entry.put("VALOR", initialValueLiteral); // Se reemplazará después
            }
        } else {
            entry.put("VALOR", ""); // Sin valor inicial
        }
        TABSIM.add(entry);
        // System.out.println("Variable añadida a TABSIM: " + entry);
    }
    
    private static void updateVariableValueInTABSIM(String variableName, String valueExpression) {
        for (Map<String, String> entry : TABSIM) {
            if (entry.get("VARIABLE").equals(variableName)) {
                // El valueExpression será procesado por replaceIdentifiers.
                // Aquí solo actualizamos el campo VALOR en TABSIM con la expresión (que luego será ids).
                entry.put("VALOR", valueExpression); // Se reemplazará después
                // System.out.println("Valor actualizado en TABSIM para " + variableName + ": " + valueExpression);
                return;
            }
        }
        System.err.println("Error: Variable '" + variableName + "' no declarada, no se puede asignar valor.");
    }

    // --- Fin de métodos existentes ---

    // Clase auxiliar para el resultado del parseo de estructuras
    static class ParseResult {
        String type; // IF, SWITCH, FOR, WHILE, DO_WHILE, PRINT, INPUT_ASSIGN, DECLARATION, ASSIGNMENT, EMPTY, UNKNOWN
        String rawMatchedCode;
        int linesConsumed;
        Map<String, String> parts = new HashMap<>(); // "condition", "body", "elseBody", "expression", "init", "increment", "casesRaw"

        public ParseResult(String type, String rawMatchedCode, int linesConsumed) {
            this.type = type;
            this.rawMatchedCode = rawMatchedCode;
            this.linesConsumed = linesConsumed;
        }
    }

    /**
     * Encuentra el índice de la línea que contiene la llave de cierre '}'
     * correspondiente a la llave de apertura '{' en la línea de inicio.
     * @param lines Array de todas las líneas del bloque.
     * @param startLineIndex Índice de la línea donde comienza la búsqueda del bloque (debe contener '{').
     * @param openingBraceCharIndexEnStartLine Índice del carácter '{' en la línea de inicio.
     * @return Índice de la línea con la '}' de cierre, o -1 si hay error.
     */
    private static int findClosingBraceLineIndex(String[] lines, int startLineIndex, int openingBraceCharIndexInStartLine) {
        int braceDepth = 0;
        for (int i = startLineIndex; i < lines.length; i++) {
            String line = lines[i];
            int charStartIndex = (i == startLineIndex) ? openingBraceCharIndexInStartLine : 0;
            for (int j = charStartIndex; j < line.length(); j++) {
                if (line.charAt(j) == '{') {
                    braceDepth++;
                } else if (line.charAt(j) == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        return i; // Línea donde se cierra el bloque principal
                    }
                }
            }
        }
        return -1; // Llave de cierre no encontrada
    }

    /**
     * Extrae el contenido de un bloque delimitado por llaves.
     * @param lines El array completo de líneas.
     * @param firstLineOfBlockContent El contenido de la primera línea DENTRO del bloque (después de '{').
     * @param startLineIdx El índice de la línea que contiene la '{' de apertura.
     * @param endLineIdx El índice de la línea que contiene la '}' de cierre.
     * @param openingBracePos La posición del '{' en la startLineIdx.
     * @param closingBracePos La posición del '}' en la endLineIdx.
     * @return El contenido del bloque como un solo String.
     */
    private static String extractBlockContent(String[] lines, int startLineIdx, int endLineIdx, int openingBracePos, int closingBracePos) {
        StringBuilder content = new StringBuilder();
        if (startLineIdx == endLineIdx) { // Bloque en una sola línea: { contenido }
            return lines[startLineIdx].substring(openingBracePos + 1, closingBracePos).trim();
        }
        // Primera línea del contenido
        content.append(lines[startLineIdx].substring(openingBracePos + 1).trim());
        if (!content.toString().isEmpty()) content.append("\n");

        // Líneas intermedias
        for (int i = startLineIdx + 1; i < endLineIdx; i++) {
            content.append(lines[i].trim()).append("\n");
        }

        // Última línea del contenido
        if (closingBracePos > 0) {
             content.append(lines[endLineIdx].substring(0, closingBracePos).trim());
        }
        return content.toString().trim();
    }


    private static ParseResult matchIfStructure(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        Pattern pattern = Pattern.compile("^if\\s*\\((.*?)\\)\\s*\\{");
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String condition = matcher.group(1).trim();
            int openingBracePos = line.lastIndexOf('{');
            int ifBlockEndLine = findClosingBraceLineIndex(lines, currentIndex, openingBracePos);

            if (ifBlockEndLine == -1) return null; // Error: if sin cierre

            String ifBody = extractBlockContent(lines, currentIndex, ifBlockEndLine, openingBracePos, lines[ifBlockEndLine].indexOf('}'));
            
            StringBuilder rawCode = new StringBuilder();
            for(int k=currentIndex; k <= ifBlockEndLine; k++) rawCode.append(lines[k]).append("\n");
            int linesConsumed = ifBlockEndLine - currentIndex + 1;

            // Verificar else
            String elseBody = null;
            if (ifBlockEndLine + 1 < lines.length) {
                String nextLine = lines[ifBlockEndLine + 1].trim();
                if (nextLine.startsWith("else\\s*\\{")) { // Simplificado, puede ser `else {`
                     Pattern elsePattern = Pattern.compile("^else\\s*\\{");
                     Matcher elseMatcher = elsePattern.matcher(nextLine);
                     if(elseMatcher.find()){
                        int elseOpeningBracePos = nextLine.lastIndexOf('{');
                        int elseBlockEndLine = findClosingBraceLineIndex(lines, ifBlockEndLine + 1, elseOpeningBracePos);
                        if (elseBlockEndLine != -1) {
                            elseBody = extractBlockContent(lines, ifBlockEndLine + 1, elseBlockEndLine, elseOpeningBracePos, lines[elseBlockEndLine].indexOf('}'));
                            for(int k=ifBlockEndLine+1; k <= elseBlockEndLine; k++) rawCode.append(lines[k]).append("\n");
                            linesConsumed += (elseBlockEndLine - (ifBlockEndLine + 1) + 1);
                        } else return null; // Error: else sin cierre
                     }
                }
            }
            ParseResult result = new ParseResult("IF", rawCode.toString().trim(), linesConsumed);
            result.parts.put("condition", condition);
            result.parts.put("body", ifBody);
            if (elseBody != null) result.parts.put("elseBody", elseBody);
            return result;
        }
        return null;
    }

    private static ParseResult matchSwitchStructure(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        Pattern pattern = Pattern.compile("^sw\\s*\\((.*?)\\)\\s*\\{");
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String expression = matcher.group(1).trim();
            int openingBracePos = line.lastIndexOf('{');
            int blockEndLine = findClosingBraceLineIndex(lines, currentIndex, openingBracePos);

            if (blockEndLine == -1) return null; 

            String blockContent = extractBlockContent(lines, currentIndex, blockEndLine, openingBracePos, lines[blockEndLine].indexOf('}'));
            
            StringBuilder rawCode = new StringBuilder();
            for(int k=currentIndex; k <= blockEndLine; k++) rawCode.append(lines[k]).append("\n");

            ParseResult result = new ParseResult("SWITCH", rawCode.toString().trim(), blockEndLine - currentIndex + 1);
            result.parts.put("expression", expression);
            result.parts.put("casesRaw", blockContent); // El parseo de casos es más complejo, se deja crudo por ahora
            return result;
        }
        return null;
    }
    
    private static ParseResult matchForStructure(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        Pattern pattern = Pattern.compile("^for\\s*\\((.*?);(.*?);(.*?)\\)\\s*\\{");
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String init = matcher.group(1).trim();
            String condition = matcher.group(2).trim();
            String increment = matcher.group(3).trim();
            
            int openingBracePos = line.lastIndexOf('{');
            int blockEndLine = findClosingBraceLineIndex(lines, currentIndex, openingBracePos);

            if (blockEndLine == -1) return null;

            String body = extractBlockContent(lines, currentIndex, blockEndLine, openingBracePos, lines[blockEndLine].indexOf('}'));
            
            StringBuilder rawCode = new StringBuilder();
            for(int k=currentIndex; k <= blockEndLine; k++) rawCode.append(lines[k]).append("\n");

            ParseResult result = new ParseResult("FOR", rawCode.toString().trim(), blockEndLine - currentIndex + 1);
            result.parts.put("init", init);
            result.parts.put("condition", condition);
            result.parts.put("increment", increment);
            result.parts.put("body", body);
            return result;
        }
        return null;
    }

       private static ParseResult matchWhileStructure(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        // Asegurarse de no confundir con do-while si "while" está al final de la línea
        // CORRECCIÓN AQUÍ: Añadir `currentIndex > 0`
        if (currentIndex > 0 && line.endsWith(");") && lines[currentIndex-1].trim().endsWith("}")) { 
            // Esto parece ser la parte 'while (condicion);' de un do-while,
            // así que no lo procesamos como un bucle while independiente aquí.
            return null; 
        }

        Pattern pattern = Pattern.compile("^while\\s*\\((.*?)\\)\\s*\\{?", Pattern.CASE_INSENSITIVE); // Permitir '{' opcional en la misma línea
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String condition = matcher.group(1).trim();
            int openingBracePosInCurrentLine = line.lastIndexOf('{');
            int bodyStartLineIndex = currentIndex;
            int effectiveOpeningBracePos = openingBracePosInCurrentLine; // Posición del '{' en su línea

            // Comprobar si la llave está en la siguiente línea
            if (openingBracePosInCurrentLine == -1) {
                if (currentIndex + 1 < lines.length && lines[currentIndex + 1].trim().equals("{")) {
                    bodyStartLineIndex = currentIndex + 1; // El cuerpo comienza en la siguiente línea
                    effectiveOpeningBracePos = 0; // La llave está al inicio de lines[bodyStartLineIndex]
                } else {
                     System.err.println("Error de sintaxis: Bucle 'while' sin '{' de apertura en la misma línea o en la siguiente.");
                    return new ParseResult("ERROR_SYNTAX_WHILE", line, 1); // Error de sintaxis
                }
            }
            
            int blockEndLine = findClosingBraceLineIndex(lines, bodyStartLineIndex, effectiveOpeningBracePos);

            if (blockEndLine == -1) {
                System.err.println("Error de sintaxis: Bucle 'while' con '{' de apertura sin '}' de cierre correspondiente.");
                return new ParseResult("ERROR_SYNTAX_WHILE_NO_CLOSE", line, 1); // Error de sintaxis
            }

            String body = extractBlockContent(lines, bodyStartLineIndex, blockEndLine, effectiveOpeningBracePos, lines[blockEndLine].lastIndexOf('}'));
            
            StringBuilder rawCode = new StringBuilder();
            for(int k=currentIndex; k <= blockEndLine; k++) rawCode.append(lines[k]).append("\n");

            ParseResult result = new ParseResult("WHILE", rawCode.toString().trim(), blockEndLine - currentIndex + 1);
            result.parts.put("condition", condition);
            result.parts.put("body", body);
            return result;
        }
        return null;
    }

    private static ParseResult matchDoWhileStructure(String[] lines, int currentIndex) {
        String firstLine = lines[currentIndex].trim();
        if (!firstLine.startsWith("do\\s*\\{")) { // Simplificado, puede ser `do {`
             Pattern doPattern = Pattern.compile("^do\\s*\\{");
             Matcher doMatcher = doPattern.matcher(firstLine);
             if(!doMatcher.find()) return null;
        }


        int openingBracePos = firstLine.lastIndexOf('{');
        int bodyEndLine = findClosingBraceLineIndex(lines, currentIndex, openingBracePos);

        if (bodyEndLine == -1) return null; // do sin cierre de cuerpo

        // La siguiente línea DEBE ser el while(condicion);
        if (bodyEndLine + 1 >= lines.length) return null; // No hay línea para el while

        String whileLine = lines[bodyEndLine + 1].trim();
        Pattern whilePattern = Pattern.compile("^while\\s*\\((.*?)\\)\\s*;");
        Matcher whileMatcher = whilePattern.matcher(whileLine);

        if (whileMatcher.matches()) {
            String condition = whileMatcher.group(1).trim();
            String body = extractBlockContent(lines, currentIndex, bodyEndLine, openingBracePos, lines[bodyEndLine].indexOf('}'));
            
            StringBuilder rawCode = new StringBuilder();
            for(int k=currentIndex; k <= bodyEndLine + 1; k++) rawCode.append(lines[k]).append("\n");

            ParseResult result = new ParseResult("DO_WHILE", rawCode.toString().trim(), (bodyEndLine + 1) - currentIndex + 1);
            result.parts.put("condition", condition);
            result.parts.put("body", body);
            return result;
        }
        return null;
    }
    
    private static ParseResult matchPrintStatement(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        Pattern pattern = Pattern.compile("^Print\\s*\\((.*?)\\)\\s*;");
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
            ParseResult result = new ParseResult("PRINT", line, 1);
            result.parts.put("arguments", matcher.group(1).trim());
            return result;
        }
        return null;
    }

    // analyzeLine original, ahora enfocado en declaración/asignación y efectos en TABSIM
    private static boolean processSimpleLine(String line) {
        String trimmedLine = line.trim();
        // Variable Declaration: INT var := val; o INT var;
        Pattern declPattern = Pattern.compile("^(INT|STR|BOOL)\\s+([a-z][a-zA-Z0-9_]*)\\s*(?:\\:=\\s*(.+))?\\s*;$", Pattern.CASE_INSENSITIVE);
        Matcher declMatcher = declPattern.matcher(trimmedLine);
        if (declMatcher.matches()) {
            String type = declMatcher.group(1);
            String varName = declMatcher.group(2);
            String value = declMatcher.group(3) != null ? declMatcher.group(3).trim() : null;
            addVariableToTABSIM(varName, type, value);
            return true;
        }

        // Assignment: var := val;
        Pattern assignPattern = Pattern.compile("^([a-z][a-zA-Z0-9_]*)\\s*\\:=\\s*(.+)\\s*;$");
        Matcher assignMatcher = assignPattern.matcher(trimmedLine);
        if (assignMatcher.matches()) {
            String varName = assignMatcher.group(1);
            String value = assignMatcher.group(2).trim();
            updateVariableValueInTABSIM(varName, value); // Actualiza TABSIM con la expresión/valor crudo
            return true;
        }
        return false;
    }
    
    // Reemplazar variables, valores, constantes y cadenas por identificadores
    private static String replaceIdentifiers(String lineInput) {
        String line = lineInput; // Trabajar con una copia

        // 1. Proteger cadenas literales y reemplazarlas al final
        Pattern stringLiteralPattern = Pattern.compile("\"(.*?)\"");
        Matcher stringMatcher = stringLiteralPattern.matcher(line);
        List<String> foundStrings = new ArrayList<>();
        StringBuffer sb = new StringBuffer();
        while(stringMatcher.find()){
            foundStrings.add(stringMatcher.group(1)); // Guardar contenido sin comillas
            stringMatcher.appendReplacement(sb, "__STRING_PLACEHOLDER__");
        }
        stringMatcher.appendTail(sb);
        line = sb.toString();

        // 2. Reemplazar variables de TABSIM (las más largas primero para evitar reemplazos parciales)
        List<Map<String, String>> sortedTabsim = new ArrayList<>(TABSIM);
        sortedTabsim.sort((a,b) -> b.get("VARIABLE").length() - a.get("VARIABLE").length());

        for (Map<String, String> entry : sortedTabsim) {
            String varName = entry.get("VARIABLE");
            // Solo reemplazar si NO es un literal de cadena (que ya está en TABSIM como constante)
            // y si no es un número (que también se tratará como constante)
            if (!varName.startsWith("\"") && !varName.matches("\\d+|TRUE|FALSE")) {
                 // Asegurar que sea una palabra completa y no parte de otra palabra o palabra clave
                 // Evitar reemplazar "id" si es parte de "identificador"
                 line = line.replaceAll("\\b" + Pattern.quote(varName) + "\\b", entry.get("ID"));
            }
        }
        
        // 3. Reemplazar constantes numéricas y booleanas por sus IDs
        Pattern numBoolPattern = Pattern.compile("\\b(\\d+|TRUE|FALSE)\\b", Pattern.CASE_INSENSITIVE);
        Matcher numBoolMatcher = numBoolPattern.matcher(line);
        sb = new StringBuffer();
        while(numBoolMatcher.find()){
            String constant = numBoolMatcher.group(1);
            String type = constant.matches("\\d+") ? "int" : "bool";
            String id = addConstantToTABSIM(constant.toUpperCase(), type); // TRUE/FALSE en mayúsculas
            numBoolMatcher.appendReplacement(sb, id);
        }
        numBoolMatcher.appendTail(sb);
        line = sb.toString();

        // 4. Reemplazar los placeholders de cadena con sus IDs
        for(String strContent : foundStrings){
            String fullStringLiteral = "\"" + strContent + "\"";
            String id = addConstantToTABSIM(fullStringLiteral, "string");
            line = line.replaceFirst("__STRING_PLACEHOLDER__", id);
        }
        return line;
    }


    private static String transformCodeWithIdentifiers(ParseResult structure) {
        if (structure == null) return "";
        String transformed;

        switch (structure.type) {
            case "IF":
                String cond = replaceIdentifiers(structure.parts.get("condition"));
                String body = analyzeCodeBlock(structure.parts.get("body").split("\\n")); // Recursivo
                transformed = "if (" + cond + ") {\n" + body + "}";
                if (structure.parts.containsKey("elseBody")) {
                    String elseBody = analyzeCodeBlock(structure.parts.get("elseBody").split("\\n")); // Recursivo
                    transformed += " else {\n" + elseBody + "}";
                }
                return transformed + "\n";
            case "SWITCH":
                String expr = replaceIdentifiers(structure.parts.get("expression"));
                // El parseo y transformación de casos es complejo, por ahora transformamos el bloque crudo
                String casesTransformed = analyzeCodeBlock(structure.parts.get("casesRaw").split("\\n"));
                return "sw (" + expr + ") {\n" + casesTransformed + "}\n";
            case "FOR":
                String init = replaceIdentifiers(structure.parts.get("init"));
                 // La declaración en init también afecta a TABSIM
                processSimpleLine(structure.parts.get("init") + (structure.parts.get("init").trim().endsWith(";") ? "" : ";"));
                String forCond = replaceIdentifiers(structure.parts.get("condition"));
                String incr = replaceIdentifiers(structure.parts.get("increment"));
                String forBody = analyzeCodeBlock(structure.parts.get("body").split("\\n"));
                return "for (" + init + "; " + forCond + "; " + incr + ") {\n" + forBody + "}\n";
            case "WHILE":
                String whileCond = replaceIdentifiers(structure.parts.get("condition"));
                String whileBody = analyzeCodeBlock(structure.parts.get("body").split("\\n"));
                return "while (" + whileCond + ") {\n" + whileBody + "}\n";
            case "DO_WHILE":
                String doWhileBody = analyzeCodeBlock(structure.parts.get("body").split("\\n"));
                String doWhileCond = replaceIdentifiers(structure.parts.get("condition"));
                return "do {\n" + doWhileBody + "} while (" + doWhileCond + ");\n";
            case "PRINT":
                String args = replaceIdentifiers(structure.parts.get("arguments"));
                return "Print(" + args + ");\n";
            case "DECLARATION":
            case "ASSIGNMENT":
                // Estas líneas ya afectan TABSIM a través de processSimpleLine
                // y ahora solo necesitan la transformación de identificadores.
                return replaceIdentifiers(structure.rawMatchedCode) + "\n";
            case "EMPTY":
                return "\n";
            default:
                return structure.rawMatchedCode + " // TIPO DESCONOCIDO PARA TRANSFORMACION\n";
        }
    }


    private static ParseResult parseNextStructureOrLine(String[] lines, int currentIndex) {
        String currentLineOriginal = lines[currentIndex]; // Mantener original con sus espacios para reconstruir rawMatchedCode
        String currentLineTrimmed = currentLineOriginal.trim();

        if (currentLineTrimmed.isEmpty()) {
            return new ParseResult("EMPTY", currentLineOriginal, 1);
        }

        ParseResult structureResult;
        structureResult = matchIfStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchSwitchStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchForStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        // Importante: while ANTES de do-while para evitar falsos positivos si "while" es parte de la línea de do-while
        structureResult = matchWhileStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchDoWhileStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchPrintStatement(lines, currentIndex); if (structureResult != null) return structureResult;
        
        // Si no es una estructura de control de bloque, intentar declaración o asignación
        Pattern declPattern = Pattern.compile("^(INT|STR|BOOL)\\s+([a-z][a-zA-Z0-9_]*)\\s*(?:\\:=\\s*(.+))?\\s*;$", Pattern.CASE_INSENSITIVE);
        Matcher vdMatcher = declPattern.matcher(currentLineTrimmed);
        if (vdMatcher.matches()) {
            return new ParseResult("DECLARATION", currentLineOriginal, 1);
        }

        Pattern assignPattern = Pattern.compile("^([a-z][a-zA-Z0-9_]*)\\s*\\:=\\s*(.+)\\s*;$");
        Matcher assignMatcher = assignPattern.matcher(currentLineTrimmed);
        if (assignMatcher.matches()) {
             // Aquí podrías verificar específicamente si es una asignación de Input
            // String rhs = assignMatcher.group(2).trim();
            // if (rhs.startsWith("Input(")... ) return new ParseResult("INPUT_ASSIGN", ...);
            return new ParseResult("ASSIGNMENT", currentLineOriginal, 1);
        }
        
        return new ParseResult("UNKNOWN", currentLineOriginal, 1); // No se pudo reconocer
    }

    private static String analyzeCodeBlock(String[] blockLines) {
        StringBuilder transformedCodeBlock = new StringBuilder();
        int i = 0;
        while (i < blockLines.length) {
            ParseResult result = parseNextStructureOrLine(blockLines, i);
            if (result != null) {
                // Primero, procesar declaraciones o asignaciones para efectos en TABSIM
                if ("DECLARATION".equals(result.type) || "ASSIGNMENT".equals(result.type)) {
                    processSimpleLine(result.rawMatchedCode.trim());
                } else if ("FOR".equals(result.type) && result.parts.containsKey("init")) {
                    // La inicialización del for también puede ser una declaración
                     processSimpleLine(result.parts.get("init").trim() + (result.parts.get("init").trim().endsWith(";") ? "" : ";") );
                }
                // Luego, transformar el código (esto puede ser recursivo para los cuerpos de los bloques)
                transformedCodeBlock.append(transformCodeWithIdentifiers(result));
                i += result.linesConsumed;
            } else { // Debería ser manejado por ParseResult("UNKNOWN")
                transformedCodeBlock.append(blockLines[i]).append(" // ERROR DE PARSEO INTERNO\n");
                i++; 
            }
        }
        return transformedCodeBlock.toString();
    }

    public static void main(String[] args) {
        loadTABSIM(TABSIM_FILE); // Cargar tabla de símbolos inicial

        Scanner scanner = new Scanner(System.in);
        System.out.println("Ingrese el código fuente de J2G. Escriba 'END' en una nueva línea para finalizar:");
        StringBuilder codeBuilder = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).equalsIgnoreCase("END")) {
            codeBuilder.append(line).append("\n");
        }
        scanner.close();

        String rawCode = codeBuilder.toString();
        System.out.println("\n--- Código Original ---");
        System.out.println(rawCode);

        String preprocessedCode = preprocess(rawCode);
        System.out.println("\n--- Código Preprocesado ---");
        System.out.println(preprocessedCode);

        String mainBlockContent;
        try {
            mainBlockContent = validateAndExtractMainBlock(preprocessedCode);
            System.out.println("\n--- Bloque Principal Extraído ---");
            System.out.println(mainBlockContent);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            saveTABSIM(TABSIM_OUTPUT_FILE); // Guardar TABSIM incluso si hay error después de cargarla
            return;
        }

        System.out.println("\n--- Iniciando Análisis y Transformación ---");
        String[] linesToAnalyze = mainBlockContent.split("\\n");
        String transformedCode = analyzeCodeBlock(linesToAnalyze);
        
        System.out.println("\n--- Código Transformado (con IDs) ---");
        System.out.println(transformedCode);

        saveTABSIM(TABSIM_OUTPUT_FILE);
        System.out.println("\nTABSIM actualizado guardado en " + TABSIM_OUTPUT_FILE);
        System.out.println("\n--- Contenido Final de TABSIM ---");
        for (Map<String, String> entry : TABSIM) {
            System.out.println(entry.get("VARIABLE") + "\t" + entry.get("ID") + "\t" + entry.get("TIPO") + "\t" + entry.get("VALOR"));
        }
    }
}