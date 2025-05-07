import java.io.*;
import java.util.*;
import java.util.regex.*;

public class J2GAnalyzer {

    private static final String TABSIM_FILE = "tabsim.txt"; // Archivo inicial
    private static final String TABSIM_OUTPUT_FILE = "tabsim_output.txt"; // Archivo de salida
    private static final List<Map<String, String>> TABSIM = new ArrayList<>();
    private static int variableCount = 1; // Contador para generar identificadores únicos (ident1, ident2, ...)

    // Leer TABSIM desde archivo
    private static void loadTABSIM(String fileName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 4) {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("VARIABLE", parts[0].trim());
                    entry.put("ID", parts[1].trim());
                    entry.put("TIPO", parts[2].trim());
                    entry.put("VALOR", parts[3].trim());
                    TABSIM.add(entry);
                }
            }
        } catch (IOException e) {
            System.err.println("Error al cargar el archivo " + fileName + ": " + e.getMessage());
        }
    }

    // Guardar TABSIM en archivo
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

    // Agregar una constante o cadena a TABSIM
    private static String addConstantToTABSIM(String value, String type) {
        // Verificar si el valor ya existe en TABSIM
        for (Map<String, String> entry : TABSIM) {
            if (entry.get("VALOR").equals(value) && entry.get("TIPO").equals(type)) {
                return entry.get("ID"); // Retornar el identificador existente
            }
        }

        // Generar un nuevo identificador para la constante o cadena
        String identifier = "ident" + variableCount++;
        Map<String, String> entry = new HashMap<>();
        entry.put("VARIABLE", value);
        entry.put("ID", identifier);
        entry.put("TIPO", type.toLowerCase());
        entry.put("VALOR", value);
        TABSIM.add(entry);
        System.out.println("Constante o cadena añadida a TABSIM: " + entry);
        return identifier;
    }

    // Preprocesar el código fuente
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

    // Validar que el código está dentro de FUNC J2G Main () { ... }
    private static String validateAndExtractMainBlock(String code) {
        String mainPattern = "^FUNC\\s+J2G\\s+Main\\s*\\(\\s*\\)\\s*\\{(.*)\\}\\s*$";
        Pattern pattern = Pattern.compile(mainPattern, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(code);
        if (matcher.matches()) {
            return matcher.group(1).trim(); // Extraer el contenido dentro del bloque
        } else {
            throw new IllegalArgumentException("Error: El código no está encapsulado dentro de 'FUNC J2G Main () { ... }'.");
        }
    }

    // Reemplazar variables, valores, constantes y cadenas por identificadores
    private static String replaceIdentifiers(String line) {
        // Detectar cadenas literales y protegerlas
        Pattern stringLiteralPattern = Pattern.compile("\"(.*?)\"");
        Matcher matcher = stringLiteralPattern.matcher(line);
        List<String> stringLiterals = new ArrayList<>();
        while (matcher.find()) {
            stringLiterals.add(matcher.group());
        }
        String modifiedLine = line;
        for (String literal : stringLiterals) {
            modifiedLine = modifiedLine.replace(literal, "__STRING_LITERAL__");
        }

        // Reemplazar variables y valores definidos en TABSIM
        for (Map<String, String> entry : TABSIM) {
            String variable = entry.get("VARIABLE");
            String identifier = entry.get("ID");
            modifiedLine = modifiedLine.replaceAll("\\b" + Pattern.quote(variable) + "\\b", identifier);
        }

        // Detectar cadenas literales y agregarlas a TABSIM
        for (String literal : stringLiterals) {
            String identifier = addConstantToTABSIM(literal, "string");
            modifiedLine = modifiedLine.replaceFirst(Pattern.quote("__STRING_LITERAL__"), identifier);
        }

        // Detectar constantes numéricas y agregarlas a TABSIM
        Pattern constantPattern = Pattern.compile("\\b\\d+\\b");
        Matcher constantMatcher = constantPattern.matcher(modifiedLine);
        while (constantMatcher.find()) {
            String constantValue = constantMatcher.group();
            String constantIdentifier = addConstantToTABSIM(constantValue, "int");
            modifiedLine = modifiedLine.replaceAll("\\b" + Pattern.quote(constantValue) + "\\b", constantIdentifier);
        }

        return modifiedLine;
    }

    // Analizar una línea de código
    private static boolean analyzeLine(String line) {
        String variableDeclaration = "^(INT|STR|BOOL)\\s+([a-z][a-zA-Z0-9_]*)\\s*(?:\\:=\\s*(.+))?\\s*;$";
        String assignment = "^([a-z][a-zA-Z0-9_]*)\\s*\\:=\\s*(.+)\\s*;$";

        Matcher matcher = Pattern.compile(variableDeclaration).matcher(line);
        if (matcher.matches()) {
            String type = matcher.group(1);
            String variable = matcher.group(2);
            String value = matcher.group(3) != null ? matcher.group(3).trim() : "";
            addVariableToTABSIM(variable, type, value);
            return true;
        }

        matcher = Pattern.compile(assignment).matcher(line);
        if (matcher.matches()) {
            String variable = matcher.group(1);
            String value = matcher.group(2).trim();
            updateVariableValue(variable, value);
            return true;
        }

        return false;
    }

    // Agregar una nueva variable a TABSIM
    private static void addVariableToTABSIM(String variable, String type, String value) {
        for (Map<String, String> entry : TABSIM) {
            if (entry.get("VARIABLE").equals(variable)) {
                return;
            }
        }

        String identifier = "ident" + variableCount++;
        String valueIdentifier = value.isEmpty() ? "" : "ident" + variableCount++;

        if (!value.isEmpty()) {
            addConstantToTABSIM(value, type); // Agregar el valor como constante
        }

        Map<String, String> entry = new HashMap<>();
        entry.put("VARIABLE", variable);
        entry.put("ID", identifier);
        entry.put("TIPO", type.toLowerCase());
        entry.put("VALOR", valueIdentifier);
        TABSIM.add(entry);
    }

    // Actualizar el valor de una variable existente en TABSIM
    private static void updateVariableValue(String variable, String value) {
        for (Map<String, String> entry : TABSIM) {
            if (entry.get("VARIABLE").equals(variable)) {
                entry.put("VALOR", value);
                return;
            }
        }
    }

    public static void main(String[] args) {
        loadTABSIM(TABSIM_FILE);

        Scanner scanner = new Scanner(System.in);
        System.out.println("Ingrese el código fuente de J2G. Escriba 'END' en una nueva línea para finalizar:");
        StringBuilder codeBuilder = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).equalsIgnoreCase("END")) {
            codeBuilder.append(line).append("\n");
        }
        scanner.close();

        String code = preprocess(codeBuilder.toString());
        System.out.println("\nCódigo después del preprocesamiento:\n" + code);

        String mainBlock;
        try {
            mainBlock = validateAndExtractMainBlock(code);
            System.out.println("\nBloque principal extraído:\n" + mainBlock);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

        System.out.println("\nIniciando análisis léxico...");
        String[] lines = mainBlock.split("\\n");
        StringBuilder transformedCode = new StringBuilder();
        for (String codeLine : lines) {
            analyzeLine(codeLine);
            transformedCode.append(replaceIdentifiers(codeLine)).append("\n");
        }

        System.out.println("\nCódigo transformado:\n" + transformedCode);
        saveTABSIM(TABSIM_OUTPUT_FILE);

        System.out.println("\nTABSIM actualizado guardado en " + TABSIM_OUTPUT_FILE);
        for (Map<String, String> entry : TABSIM) {
            System.out.println(entry);
        }
    }
}