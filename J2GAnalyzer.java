import java.io.*;
import java.util.*;
import java.util.regex.*;
//import java.util.stream.Collectors;

public class J2GAnalyzer {

    // TABSIM for user-defined variables and constants encountered during analysis
    private static final List<Map<String, String>> USER_SYMBOLS_TABSIM = new ArrayList<>();
    private static int nextUserSymbolIdCounter = 1; // Counter for generating id1, id2, ...

    // File to persist/load user symbols (optional, can start fresh each time)
    private static final String USER_SYMBOLS_FILE_INPUT = "user_tabsim_input.txt"; // Load previous state from here
    private static final String USER_SYMBOLS_FILE_OUTPUT = "user_tabsim_output.txt"; // Save final state here


    private static void loadUserSymbolsTABSIM(String fileName) {
        USER_SYMBOLS_TABSIM.clear();
        nextUserSymbolIdCounter = 1; // Reset counter
        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("Archivo de entrada de símbolos de usuario no encontrado: " + fileName + ". Iniciando con tabla vacía.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            int maxIdFound = 0;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("\t");
                if (parts.length >= 4) {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("VARIABLE", parts[0].trim());
                    String idFromFile = parts[1].trim();
                    entry.put("ID", idFromFile);
                    entry.put("TIPO", parts[2].trim());
                    entry.put("VALOR", parts[3].trim()); // VALOR aquí es el valor literal o expresión original
                    USER_SYMBOLS_TABSIM.add(entry);

                    // Update nextUserSymbolIdCounter based on loaded IDs
                    if (idFromFile.startsWith("id")) {
                        try {
                            int numPart = Integer.parseInt(idFromFile.substring(2));
                            if (numPart > maxIdFound) {
                                maxIdFound = numPart;
                            }
                        } catch (NumberFormatException e) {
                            // Ignore if 'id' is not followed by a number
                        }
                    }
                }
            }
            nextUserSymbolIdCounter = maxIdFound + 1;
            System.out.println("Símbolos de usuario cargados desde " + fileName + ". Próximo ID: " + nextUserSymbolIdCounter);
        } catch (IOException e) {
            System.err.println("Error al cargar el archivo de símbolos de usuario " + fileName + ": " + e.getMessage());
        }
    }

    private static void saveUserSymbolsTABSIM(String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write("VARIABLE\tID\tTIPO\tVALOR_ORIGINAL_O_EXPRESION\n"); // Header
            for (Map<String, String> entry : USER_SYMBOLS_TABSIM) {
                writer.write(entry.get("VARIABLE") + "\t" +
                             entry.get("ID") + "\t" +
                             entry.get("TIPO") + "\t" +
                             entry.get("VALOR")); // VALOR es el valor/expresión original
                writer.newLine();
            }
            System.out.println("Tabla de símbolos de usuario guardada en " + fileName);
        } catch (IOException e) {
            System.err.println("Error al guardar tabla de símbolos de usuario en " + fileName + ": " + e.getMessage());
        }
    }

    private static String preprocess(String code) {
        String[] lines = code.split("\\r?\\n"); // Handles Windows and Unix line endings
        StringBuilder processedCode = new StringBuilder();
        for (String line : lines) {
            line = line.replaceAll("//.*", "").trim(); // Eliminar comentarios
            line = line.replaceAll("\\s{2,}", " "); // Normalizar múltiples espacios a uno solo
            if (!line.isEmpty()) {
                processedCode.append(line).append("\n");
            }
        }
        return processedCode.toString().trim();
    }

    private static String validateAndExtractMainBlock(String code) {
        String mainPatternStr = "^FUNC\\s+J2G\\s+Main\\s*\\(\\s*\\)\\s*\\{(.*)\\}\\s*$";
        Pattern pattern = Pattern.compile(mainPatternStr, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(code);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        } else {
            throw new IllegalArgumentException("Error: El código no está encapsulado correctamente dentro de 'FUNC J2G Main () { ... }'.");
        }
    }

    private static String getOrCreateUserSymbolIDForLiteral(String literalValue, String type) {
        String normalizedType = type.toLowerCase();
        // TRUE/FALSE se mantienen como están, no se les asigna idN
        if (normalizedType.equals("bool") && (literalValue.equalsIgnoreCase("TRUE") || literalValue.equalsIgnoreCase("FALSE"))) {
            return literalValue.toUpperCase();
        }

        for (Map<String, String> entry : USER_SYMBOLS_TABSIM) {
            if (entry.get("TIPO").equals(normalizedType) &&
                entry.get("VALOR").equals(literalValue) &&
                entry.get("VARIABLE").equals(literalValue)) { // Para literales, VARIABLE y VALOR son el mismo literal
                return entry.get("ID");
            }
        }
        String identifier = "id" + nextUserSymbolIdCounter++;
        Map<String, String> entry = new HashMap<>();
        entry.put("VARIABLE", literalValue); // El "nombre" de la variable para un literal es el literal mismo
        entry.put("ID", identifier);
        entry.put("TIPO", normalizedType);
        entry.put("VALOR", literalValue);    // El valor almacenado es el literal mismo
        USER_SYMBOLS_TABSIM.add(entry);
        return identifier;
    }

    private static void addVariableToUserSymbolsTABSIM(String variableName, String type, String initialValueLiteralOrExpr) {
        for (Map<String, String> entry : USER_SYMBOLS_TABSIM) {
            if (entry.get("VARIABLE").equals(variableName)) {
                System.err.println("Advertencia: Variable '" + variableName + "' ya declarada. Se ignora la redeclaración.");
                return;
            }
        }

        String varId = "id" + nextUserSymbolIdCounter++;
        Map<String, String> entry = new HashMap<>();
        entry.put("VARIABLE", variableName);
        entry.put("ID", varId);
        entry.put("TIPO", type.toLowerCase());
        entry.put("VALOR", initialValueLiteralOrExpr != null ? initialValueLiteralOrExpr : ""); // Almacena la expresión/literal original
        USER_SYMBOLS_TABSIM.add(entry);
    }

    private static void updateVariableValueInUserSymbolsTABSIM(String variableName, String valueExpression) {
        for (Map<String, String> entry : USER_SYMBOLS_TABSIM) {
            if (entry.get("VARIABLE").equals(variableName)) {
                entry.put("VALOR", valueExpression); // Almacena la nueva expresión/literal original
                return;
            }
        }
        System.err.println("Error: Variable '" + variableName + "' no declarada, no se puede asignar valor.");
    }

    static class ParseResult {
        String type;
        String rawMatchedCode;
        int linesConsumed;
        Map<String, String> parts = new HashMap<>();

        public ParseResult(String type, String rawMatchedCode, int linesConsumed) {
            this.type = type;
            this.rawMatchedCode = rawMatchedCode;
            this.linesConsumed = linesConsumed;
        }
    }

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
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static String extractBlockContent(String[] lines, int startLineIdx, int endLineIdx, int openingBracePos, int closingBracePos) {
        StringBuilder content = new StringBuilder();
        if (startLineIdx == endLineIdx) {
            if (openingBracePos + 1 <= closingBracePos) { // Asegurar que hay contenido
                 return lines[startLineIdx].substring(openingBracePos + 1, closingBracePos).trim();
            } else {
                return ""; // Bloque vacío {}
            }
        }
        content.append(lines[startLineIdx].substring(openingBracePos + 1).trim());
        if (content.length() > 0) content.append("\n");

        for (int i = startLineIdx + 1; i < endLineIdx; i++) {
            content.append(lines[i].trim()).append("\n");
        }

        if (closingBracePos > 0) {
            content.append(lines[endLineIdx].substring(0, closingBracePos).trim());
        }
        // Quitar el último \n si el bloque termina vacío después de los trims
        String result = content.toString().trim();
        return result.isEmpty() ? "" : result + "\n"; // Añadir un \n si no está vacío para consistencia
    }


    private static ParseResult matchIfStructure(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        Pattern pattern = Pattern.compile("^if\\s*\\((.*?)\\)\\s*\\{?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String condition = matcher.group(1).trim();
            int bodyStartLineIndex = currentIndex;
            int openingBracePosInBodyStartLine = line.lastIndexOf('{');

            if (openingBracePosInBodyStartLine == -1) {
                if (currentIndex + 1 < lines.length && lines[currentIndex + 1].trim().equals("{")) {
                    bodyStartLineIndex = currentIndex + 1;
                    openingBracePosInBodyStartLine = 0;
                } else {
                    System.err.println("Error sintaxis 'if': Falta '{' para el bloque if. Línea: " + (currentIndex + 1));
                    return null;
                }
            }

            int ifBlockEndLine = findClosingBraceLineIndex(lines, bodyStartLineIndex, openingBracePosInBodyStartLine);
            if (ifBlockEndLine == -1) {
                 System.err.println("Error sintaxis 'if': Bloque if no cerrado con '}'. Iniciado en línea: " + (bodyStartLineIndex + 1));
                 return null;
            }

            String ifBody = extractBlockContent(lines, bodyStartLineIndex, ifBlockEndLine,
                                                openingBracePosInBodyStartLine, lines[ifBlockEndLine].lastIndexOf('}'));

            StringBuilder rawCodeBuilder = new StringBuilder();
            for (int k = currentIndex; k <= ifBlockEndLine; k++) rawCodeBuilder.append(lines[k]).append("\n");
            int linesConsumedTotal = ifBlockEndLine - currentIndex + 1;

            String elseBody = null;
            if (ifBlockEndLine + 1 < lines.length) {
                String nextLineForElse = lines[ifBlockEndLine + 1].trim();
                Pattern elsePattern = Pattern.compile("^else\\s*\\{?", Pattern.CASE_INSENSITIVE);
                Matcher elseMatcher = elsePattern.matcher(nextLineForElse);

                if (elseMatcher.find()) {
                    int elseBodyStartLineIndex = ifBlockEndLine + 1;
                    int elseOpeningBracePos = nextLineForElse.lastIndexOf('{');

                    if (elseOpeningBracePos == -1) {
                        if (ifBlockEndLine + 2 < lines.length && lines[ifBlockEndLine + 2].trim().equals("{")) {
                            elseBodyStartLineIndex = ifBlockEndLine + 2;
                            elseOpeningBracePos = 0;
                        } else {
                             System.err.println("Error sintaxis 'else': Falta '{' para el bloque else. Línea: " + (elseBodyStartLineIndex +1));
                             // No consumimos el 'else' si no tiene bloque
                        }
                    }
                    
                    if (elseOpeningBracePos != -1) { // Solo si encontramos una llave para el else
                        int elseBlockEndLine = findClosingBraceLineIndex(lines, elseBodyStartLineIndex, elseOpeningBracePos);
                        if (elseBlockEndLine != -1) {
                            elseBody = extractBlockContent(lines, elseBodyStartLineIndex, elseBlockEndLine,
                                                           elseOpeningBracePos, lines[elseBlockEndLine].lastIndexOf('}'));
                            for (int k = ifBlockEndLine + 1; k <= elseBlockEndLine; k++) rawCodeBuilder.append(lines[k]).append("\n");
                            linesConsumedTotal += (elseBlockEndLine - (ifBlockEndLine + 1) + 1);
                        } else {
                             System.err.println("Error sintaxis 'else': Bloque else no cerrado con '}'. Iniciado en línea: " + (elseBodyStartLineIndex+1));
                        }
                    }
                }
            }
            ParseResult result = new ParseResult("IF", rawCodeBuilder.toString().trim(), linesConsumedTotal);
            result.parts.put("condition", condition);
            result.parts.put("body", ifBody);
            if (elseBody != null) result.parts.put("elseBody", elseBody);
            return result;
        }
        return null;
    }

    private static ParseResult matchSwitchStructure(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        Pattern pattern = Pattern.compile("^sw\\s*\\((.*?)\\)\\s*\\{?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String expression = matcher.group(1).trim();
            int bodyStartLineIndex = currentIndex;
            int openingBracePosInBodyStartLine = line.lastIndexOf('{');

            if (openingBracePosInBodyStartLine == -1) {
                if (currentIndex + 1 < lines.length && lines[currentIndex + 1].trim().equals("{")) {
                    bodyStartLineIndex = currentIndex + 1;
                    openingBracePosInBodyStartLine = 0;
                } else { return null; }
            }
            
            int blockEndLine = findClosingBraceLineIndex(lines, bodyStartLineIndex, openingBracePosInBodyStartLine);
            if (blockEndLine == -1) return null;

            String blockContent = extractBlockContent(lines, bodyStartLineIndex, blockEndLine, openingBracePosInBodyStartLine, lines[blockEndLine].lastIndexOf('}'));
            
            StringBuilder rawCode = new StringBuilder();
            for(int k=currentIndex; k <= blockEndLine; k++) rawCode.append(lines[k]).append("\n");

            ParseResult result = new ParseResult("SWITCH", rawCode.toString().trim(), blockEndLine - currentIndex + 1);
            result.parts.put("expression", expression);
            result.parts.put("casesRaw", blockContent);
            return result;
        }
        return null;
    }
    
    private static ParseResult matchForStructure(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        Pattern pattern = Pattern.compile("^for\\s*\\((.*?);(.*?);(.*?)\\)\\s*\\{?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String init = matcher.group(1).trim();
            String condition = matcher.group(2).trim();
            String increment = matcher.group(3).trim();
            
            int bodyStartLineIndex = currentIndex;
            int openingBracePosInBodyStartLine = line.lastIndexOf('{');

            if (openingBracePosInBodyStartLine == -1) {
                if (currentIndex + 1 < lines.length && lines[currentIndex + 1].trim().equals("{")) {
                    bodyStartLineIndex = currentIndex + 1;
                    openingBracePosInBodyStartLine = 0;
                } else { return null; }
            }

            int blockEndLine = findClosingBraceLineIndex(lines, bodyStartLineIndex, openingBracePosInBodyStartLine);
            if (blockEndLine == -1) return null;

            String body = extractBlockContent(lines, bodyStartLineIndex, blockEndLine, openingBracePosInBodyStartLine, lines[blockEndLine].lastIndexOf('}'));
            
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
        if (currentIndex > 0 && line.matches("^while\\s*\\(.*?\\)\\s*;") && lines[currentIndex-1].trim().endsWith("}")) { 
            return null; 
        }

        Pattern pattern = Pattern.compile("^while\\s*\\((.*?)\\)\\s*\\{?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            String condition = matcher.group(1).trim();
            int bodyStartLineIndex = currentIndex;
            int openingBracePosInBodyStartLine = line.lastIndexOf('{');

            if (openingBracePosInBodyStartLine == -1) {
                if (currentIndex + 1 < lines.length && lines[currentIndex + 1].trim().equals("{")) {
                    bodyStartLineIndex = currentIndex + 1;
                    openingBracePosInBodyStartLine = 0;
                } else { return null; }
            }
            
            int blockEndLine = findClosingBraceLineIndex(lines, bodyStartLineIndex, openingBracePosInBodyStartLine);
            if (blockEndLine == -1) return null;

            String body = extractBlockContent(lines, bodyStartLineIndex, blockEndLine, openingBracePosInBodyStartLine, lines[blockEndLine].lastIndexOf('}'));
            
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
        Pattern doStartPattern = Pattern.compile("^do\\s*\\{?", Pattern.CASE_INSENSITIVE);
        Matcher doStartMatcher = doStartPattern.matcher(firstLine);

        if (!doStartMatcher.find()) return null;

        int bodyStartLineIndex = currentIndex;
        int openingBracePosInBodyStartLine = firstLine.lastIndexOf('{');

        if (openingBracePosInBodyStartLine == -1) {
            if (currentIndex + 1 < lines.length && lines[currentIndex + 1].trim().equals("{")) {
                bodyStartLineIndex = currentIndex + 1;
                openingBracePosInBodyStartLine = 0;
            } else { return null; }
        }

        int bodyEndLine = findClosingBraceLineIndex(lines, bodyStartLineIndex, openingBracePosInBodyStartLine);
        if (bodyEndLine == -1) return null; 

        if (bodyEndLine + 1 >= lines.length) return null; 

        String whileLine = lines[bodyEndLine + 1].trim();
        Pattern whilePattern = Pattern.compile("^while\\s*\\((.*?)\\)\\s*;", Pattern.CASE_INSENSITIVE);
        Matcher whileMatcher = whilePattern.matcher(whileLine);

        if (whileMatcher.matches()) {
            String condition = whileMatcher.group(1).trim();
            String body = extractBlockContent(lines, bodyStartLineIndex, bodyEndLine,
                                            openingBracePosInBodyStartLine, lines[bodyEndLine].lastIndexOf('}'));
            
            StringBuilder rawCode = new StringBuilder();
            for(int k=currentIndex; k <= bodyEndLine + 1; k++) rawCode.append(lines[k]).append("\n");

            int linesConsumed = (bodyEndLine + 1) - currentIndex + 1;
            ParseResult result = new ParseResult("DO_WHILE", rawCode.toString().trim(), linesConsumed);
            result.parts.put("condition", condition);
            result.parts.put("body", body);
            return result;
        }
        return null;
    }
    
    private static ParseResult matchPrintStatement(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        Pattern pattern = Pattern.compile("^Print\\s*\\((.*?)\\)\\s*;", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
            ParseResult result = new ParseResult("PRINT", line, 1);
            result.parts.put("arguments", matcher.group(1).trim());
            return result;
        }
        return null;
    }

    private static ParseResult matchCaseLabel(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        Pattern pattern = Pattern.compile("^caso\\s+(.+?)\\s*:", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);
        if (matcher.matches()) {
            ParseResult result = new ParseResult("CASE_LABEL", line, 1);
            result.parts.put("expression", matcher.group(1).trim());
            return result;
        }
        return null;
    }

    private static ParseResult matchDefaultLabel(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        if (line.matches("^por_defecto\\s*:")) {
            return new ParseResult("DEFAULT_LABEL", line, 1);
        }
        return null;
    }

    private static ParseResult matchDetenerStatement(String[] lines, int currentIndex) {
        String line = lines[currentIndex].trim();
        if (line.matches("^detener\\s*;")) {
            return new ParseResult("DETENER_STMT", line, 1);
        }
        return null;
    }

    private static boolean processSimpleLineForTABSIM(String line) {
        String trimmedLine = line.trim();
        Pattern declPattern = Pattern.compile("^(INT|STR|BOOL)\\s+([a-z][a-zA-Z0-9_]*)\\s*(?:\\:=\\s*(.+?))?\\s*;$", Pattern.CASE_INSENSITIVE);
        Matcher declMatcher = declPattern.matcher(trimmedLine);
        if (declMatcher.matches()) {
            String type = declMatcher.group(1);
            String varName = declMatcher.group(2);
            String value = declMatcher.group(3) != null ? declMatcher.group(3).trim() : null;
            addVariableToUserSymbolsTABSIM(varName, type, value);
            return true;
        }

        Pattern assignPattern = Pattern.compile("^([a-z][a-zA-Z0-9_]*)\\s*\\:=\\s*(.+?)\\s*;$");
        Matcher assignMatcher = assignPattern.matcher(trimmedLine);
        if (assignMatcher.matches()) {
            String varName = assignMatcher.group(1);
            String value = assignMatcher.group(2).trim();
            updateVariableValueInUserSymbolsTABSIM(varName, value);
            return true;
        }
        return false;
    }
    
    private static String replaceIdentifiers(String lineInput) {
        String line = lineInput;

        Pattern stringLiteralPattern = Pattern.compile("\"(.*?)\"");
        Matcher stringMatcher = stringLiteralPattern.matcher(line);
        StringBuffer sbTemp = new StringBuffer();
        List<String> stringLiteralsFound = new ArrayList<>();
        int placeholderIndex = 0;
        while (stringMatcher.find()) {
            String literalContentWithQuotes = stringMatcher.group(0);
            stringLiteralsFound.add(literalContentWithQuotes);
            stringMatcher.appendReplacement(sbTemp, "__STRING_PLACEHOLDER_" + (placeholderIndex++) + "__");
        }
        stringMatcher.appendTail(sbTemp);
        line = sbTemp.toString();

        Pattern numPattern = Pattern.compile("\\b(\\d+)\\b");
        Matcher numMatcher = numPattern.matcher(line);
        sbTemp = new StringBuffer();
        while (numMatcher.find()) {
            String numLiteral = numMatcher.group(1);
            String id = getOrCreateUserSymbolIDForLiteral(numLiteral, "int");
            numMatcher.appendReplacement(sbTemp, id);
        }
        numMatcher.appendTail(sbTemp);
        line = sbTemp.toString();

        List<Map<String, String>> sortedUserSymbols = new ArrayList<>(USER_SYMBOLS_TABSIM);
        sortedUserSymbols.sort((a, b) -> b.get("VARIABLE").length() - a.get("VARIABLE").length());

        for (Map<String, String> userSymbol : sortedUserSymbols) {
            String varName = userSymbol.get("VARIABLE");
            if (!varName.startsWith("\"") && !varName.matches("\\d+|TRUE|FALSE") && !varName.isEmpty() ) {
                line = line.replaceAll("\\b" + Pattern.quote(varName) + "\\b", userSymbol.get("ID"));
            }
        }
        
        for (int i = 0; i < stringLiteralsFound.size(); i++) {
            String literalWithQuotes = stringLiteralsFound.get(i);
            String id = getOrCreateUserSymbolIDForLiteral(literalWithQuotes, "string");
            line = line.replace("__STRING_PLACEHOLDER_" + i + "__", id);
        }
        return line;
    }

    private static String transformCodeWithIdentifiers(ParseResult structure) {
        if (structure == null) return "";
        String transformed;

        switch (structure.type) {
            case "IF":
                String cond = replaceIdentifiers(structure.parts.get("condition"));
                String body = analyzeCodeBlock(structure.parts.get("body").split("\\r?\\n"));
                transformed = "if (" + cond + ") {\n" + body + "}"; // body ya tiene \n al final si no está vacío
                if (structure.parts.containsKey("elseBody")) {
                    String elseBody = analyzeCodeBlock(structure.parts.get("elseBody").split("\\r?\\n"));
                    transformed += " else {\n" + elseBody + "}";
                }
                return transformed + "\n";
            case "SWITCH":
                String expr = replaceIdentifiers(structure.parts.get("expression"));
                String casesTransformed = analyzeCodeBlock(structure.parts.get("casesRaw").split("\\r?\\n"));
                return "sw (" + expr + ") {\n" + casesTransformed + "}\n";
            case "FOR":
                String init = replaceIdentifiers(structure.parts.get("init")); 
                String forCond = replaceIdentifiers(structure.parts.get("condition"));
                String incr = replaceIdentifiers(structure.parts.get("increment"));
                String forBody = analyzeCodeBlock(structure.parts.get("body").split("\\r?\\n"));
                return "for (" + init + "; " + forCond + "; " + incr + ") {\n" + forBody + "}\n";
            case "WHILE":
                String whileCond = replaceIdentifiers(structure.parts.get("condition"));
                String whileBody = analyzeCodeBlock(structure.parts.get("body").split("\\r?\\n"));
                return "while (" + whileCond + ") {\n" + whileBody + "}\n";
            case "DO_WHILE":
                String doWhileBody = analyzeCodeBlock(structure.parts.get("body").split("\\r?\\n"));
                String doWhileCond = replaceIdentifiers(structure.parts.get("condition"));
                return "do {\n" + doWhileBody + "} while (" + doWhileCond + ");\n";
            case "PRINT":
                String args = replaceIdentifiers(structure.parts.get("arguments"));
                return "Print(" + args + ");\n";
            case "CASE_LABEL":
                String caseExpr = replaceIdentifiers(structure.parts.get("expression"));
                return "caso " + caseExpr + ":\n";
            case "DEFAULT_LABEL":
                return "por_defecto:\n";
            case "DETENER_STMT":
                return "detener;\n";
            case "DECLARATION":
            case "ASSIGNMENT":
            case "INPUT_ASSIGN":
                return replaceIdentifiers(structure.rawMatchedCode.trim()) + "\n";
            case "EMPTY":
                return "\n";
            case "UNMATCHED_BRACE":
                 return structure.rawMatchedCode.trim() + " // ADVERTENCIA: LLAVE SUELTA\n";
            default: // UNKNOWN or ERROR_SYNTAX types
                return replaceIdentifiers(structure.rawMatchedCode.trim()) + " // TIPO DESCONOCIDO O ERROR DE SINTAXIS (" + structure.type + ")\n";
        }
    }

    private static ParseResult parseNextStructureOrLine(String[] lines, int currentIndex) {
        if (currentIndex >= lines.length) return null; // No more lines
        String currentLineOriginal = lines[currentIndex]; 
        String currentLineTrimmed = currentLineOriginal.trim();

        if (currentLineTrimmed.isEmpty()) {
            return new ParseResult("EMPTY", currentLineOriginal, 1);
        }

        ParseResult structureResult;
        structureResult = matchIfStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchSwitchStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchForStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchDoWhileStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchWhileStructure(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchPrintStatement(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchCaseLabel(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchDefaultLabel(lines, currentIndex); if (structureResult != null) return structureResult;
        structureResult = matchDetenerStatement(lines, currentIndex); if (structureResult != null) return structureResult;
        
        Pattern declPattern = Pattern.compile("^(INT|STR|BOOL)\\s+([a-z][a-zA-Z0-9_]*)\\s*(?:\\:=\\s*(.+?))?\\s*;$", Pattern.CASE_INSENSITIVE);
        Matcher vdMatcher = declPattern.matcher(currentLineTrimmed);
        if (vdMatcher.matches()) {
            return new ParseResult("DECLARATION", currentLineOriginal, 1);
        }

        Pattern assignPattern = Pattern.compile("^([a-z][a-zA-Z0-9_]*)\\s*\\:=\\s*(.+?)\\s*;$");
        Matcher assignMatcher = assignPattern.matcher(currentLineTrimmed);
        if (assignMatcher.matches()) {
            String rhs = assignMatcher.group(2).trim();
            if (rhs.matches("^Input\\s*\\(.*?\\)\\.(Int|Str|Bool)\\s*\\(\\s*\\)$")) {
                 return new ParseResult("INPUT_ASSIGN", currentLineOriginal, 1);
            }
            return new ParseResult("ASSIGNMENT", currentLineOriginal, 1);
        }
        
        if (currentLineTrimmed.equals("}")) {
             System.err.println("Advertencia: Llave de cierre '}' encontrada fuera de una estructura esperada en la línea original: " + (currentIndex + 1) + " -> " + currentLineOriginal );
             return new ParseResult("UNMATCHED_BRACE", currentLineOriginal, 1);
        }
        return new ParseResult("UNKNOWN", currentLineOriginal, 1);
    }

    private static String analyzeCodeBlock(String[] blockLines) {
        StringBuilder transformedCodeBlock = new StringBuilder();
        int i = 0;
        while (i < blockLines.length) {
            if (blockLines[i] == null || blockLines[i].trim().isEmpty() && i == blockLines.length -1 && transformedCodeBlock.length() == 0) {
                // Avoid processing trailing empty lines if blockLines comes from a split that might produce them
                // Or if the block itself is empty after extraction
                i++;
                continue;
            }
            ParseResult result = parseNextStructureOrLine(blockLines, i);
            if (result != null) {
                if ("DECLARATION".equals(result.type) || "ASSIGNMENT".equals(result.type) || "INPUT_ASSIGN".equals(result.type)) {
                    processSimpleLineForTABSIM(result.rawMatchedCode.trim());
                } else if ("FOR".equals(result.type) && result.parts.containsKey("init")) {
                    String initPart = result.parts.get("init").trim();
                    // Asegurar que termine en ; para processSimpleLineForTABSIM
                    if (!initPart.endsWith(";")) initPart += ";";
                    processSimpleLineForTABSIM(initPart);
                }
                transformedCodeBlock.append(transformCodeWithIdentifiers(result));
                i += result.linesConsumed;
            } else { 
                // Should not happen if parseNextStructureOrLine always returns something or null at end of lines
                if (i < blockLines.length) { // Check to prevent error on empty blockLines
                     transformedCodeBlock.append(blockLines[i]).append(" // ERROR DE PARSEO INTERNO CRÍTICO\n");
                }
                i++; 
            }
        }
        // Remove trailing newline if the last transformed part added one and the block is otherwise empty
        String finalBlock = transformedCodeBlock.toString();
        if (finalBlock.endsWith("\n\n")) return finalBlock.substring(0, finalBlock.length() -1);
        return finalBlock;
    }

    public static void main(String[] args) {
        // Cargar símbolos de usuario si existen de una ejecución previa
        loadUserSymbolsTABSIM(USER_SYMBOLS_FILE_INPUT);

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
            saveUserSymbolsTABSIM(USER_SYMBOLS_FILE_OUTPUT);
            return;
        }

        System.out.println("\n--- Iniciando Análisis y Transformación ---");
        String[] linesToAnalyze = mainBlockContent.split("\\r?\\n"); // Split por newline
        String transformedCode = analyzeCodeBlock(linesToAnalyze);
        
        System.out.println("\n--- Código Transformado (con IDs) ---");
        System.out.println(transformedCode.trim()); // .trim() para quitar posible último salto de línea extra

        saveUserSymbolsTABSIM(USER_SYMBOLS_FILE_OUTPUT);
        System.out.println("\n--- Contenido Final de USER_SYMBOLS_TABSIM ---");
        for (Map<String, String> entry : USER_SYMBOLS_TABSIM) {
            System.out.println(entry.get("VARIABLE") + "\t" + entry.get("ID") + "\t" + entry.get("TIPO") + "\t" + entry.get("VALOR"));
        }
         System.out.println("\nAnálisis completado por: Yourchh el " + "2025-05-12 14:51:33" + " UTC");
    }
}