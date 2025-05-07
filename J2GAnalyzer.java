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

    // Preprocesar el código fuente
    private static String preprocess(String code) {
        // Dividir el código en líneas para procesarlas individualmente
        String[] lines = code.split("\\n");
        StringBuilder processedCode = new StringBuilder();

        for (String line : lines) {
            // Eliminar comentarios dentro de la línea (lo que sigue después de //)
            line = line.replaceAll("//.*", "").trim();

            // Eliminar múltiples espacios dentro de la línea y normalizar
            line = line.replaceAll("\\s{2,}", " ");

            // Agregar la línea procesada al resultado si no está vacía
            if (!line.isEmpty()) {
                processedCode.append(line).append("\n");
            }
        }

        // Devolver el código procesado manteniendo los saltos de línea
        return processedCode.toString().trim();
    }

    // Verificar que el código está dentro de FUNC J2G Main () { ... }
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

    // Reemplazar variables y valores por identificadores (sin afectar cadenas literales)
    private static String replaceIdentifiers(String line) {
        // Expresión regular para detectar cadenas literales
        Pattern stringLiteralPattern = Pattern.compile("\"(.*?)\"");
        Matcher matcher = stringLiteralPattern.matcher(line);

        // Almacenar las cadenas literales encontradas
        List<String> stringLiterals = new ArrayList<>();
        while (matcher.find()) {
            stringLiterals.add(matcher.group()); // Incluye las comillas
        }

        // Remover temporalmente las cadenas literales de la línea
        String modifiedLine = line;
        for (String literal : stringLiterals) {
            modifiedLine = modifiedLine.replace(literal, "__STRING_LITERAL__");
        }

        // Reemplazar variables y valores fuera de las cadenas literales
        for (Map<String, String> entry : TABSIM) {
            String variable = entry.get("VARIABLE");
            String identifier = entry.get("ID");

            // Reemplazar solo si no está dentro de una cadena literal
            modifiedLine = modifiedLine.replaceAll("\\b" + Pattern.quote(variable) + "\\b", identifier);
        }

        // Restaurar las cadenas literales a su posición original
        for (String literal : stringLiterals) {
            modifiedLine = modifiedLine.replaceFirst(Pattern.quote("__STRING_LITERAL__"), literal);
        }

        return modifiedLine;
    }

    // Analizar una línea de código
    private static boolean analyzeLine(String line) {
        // Expresiones regulares para diferentes elementos
        String variableDeclaration = "^(INT|STR|BOOL)\\s+([a-z][a-zA-Z0-9_]*)\\s*(?:\\:=\\s*(.+))?\\s*;$";
        String assignment = "^([a-z][a-zA-Z0-9_]*)\\s*\\:=\\s*(.+)\\s*;$";

        Matcher matcher = Pattern.compile(variableDeclaration).matcher(line);
        if (matcher.matches()) {
            String type = matcher.group(1); // Tipo de la variable (INT, STR, BOOL)
            String variable = matcher.group(2); // Nombre de la variable
            String value = matcher.group(3) != null ? matcher.group(3).trim() : ""; // Valor inicial (si existe)
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

        System.err.println("Error léxico: línea inválida -> " + line);
        return false;
    }

    // Agregar una nueva variable a TABSIM
    private static void addVariableToTABSIM(String variable, String type, String value) {
        // Verificar si la variable ya existe
        for (Map<String, String> entry : TABSIM) {
            if (entry.get("VARIABLE").equals(variable)) {
                System.err.println("Error: Variable duplicada -> " + variable);
                return;
            }
        }

        // Generar identificador único
        String identifier = "ident" + variableCount++;
        String valueIdentifier = value.isEmpty() ? "" : "ident" + variableCount++;

        // Agregar el valor como una nueva entrada en TABSIM si existe
        if (!value.isEmpty()) {
            Map<String, String> valueEntry = new HashMap<>();
            valueEntry.put("VARIABLE", value);
            valueEntry.put("ID", valueIdentifier);
            valueEntry.put("TIPO", type.toLowerCase());
            valueEntry.put("VALOR", value);
            TABSIM.add(valueEntry);
        }

        // Agregar la variable a TABSIM
        Map<String, String> entry = new HashMap<>();
        entry.put("VARIABLE", variable);
        entry.put("ID", identifier);
        entry.put("TIPO", type.toLowerCase());
        entry.put("VALOR", valueIdentifier);
        TABSIM.add(entry);
        System.out.println("Variable añadida a TABSIM: " + entry);
    }

    // Actualizar el valor de una variable existente en TABSIM
    private static void updateVariableValue(String variable, String value) {
        for (Map<String, String> entry : TABSIM) {
            if (entry.get("VARIABLE").equals(variable)) {
                entry.put("VALOR", value);
                System.out.println("Valor actualizado en TABSIM: " + entry);
                return;
            }
        }
        System.err.println("Error: Variable no encontrada en TABSIM -> " + variable);
    }

    public static void main(String[] args) {
        // Cargar palabras reservadas, operadores y variables desde TABSIM inicial
        loadTABSIM(TABSIM_FILE);

        Scanner scanner = new Scanner(System.in);

        System.out.println("Ingrese el código fuente de J2G. Escriba 'END' en una nueva línea para finalizar:");
        StringBuilder codeBuilder = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).equalsIgnoreCase("END")) {
            codeBuilder.append(line).append("\n");
        }
        scanner.close();

        // Etapa 1: Preprocesamiento
        String code = preprocess(codeBuilder.toString());
        System.out.println("\nCódigo después del preprocesamiento:\n" + code);

        // Validar y extraer el bloque principal
        String mainBlock;
        try {
            mainBlock = validateAndExtractMainBlock(code);
            System.out.println("\nBloque principal extraído:\n" + mainBlock);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

        // Etapa 2: Análisis Léxico y reemplazo de identificadores
        System.out.println("\nIniciando análisis léxico...");
        String[] lines = mainBlock.split("\\n");
        StringBuilder transformedCode = new StringBuilder();
        for (String codeLine : lines) {
            analyzeLine(codeLine);
            transformedCode.append(replaceIdentifiers(codeLine)).append("\n");
        }

        // Mostrar el código transformado
        System.out.println("\nCódigo transformado:\n" + transformedCode);

        // Guardar variables en TABSIM de salida
        saveTABSIM(TABSIM_OUTPUT_FILE);

        // Mostrar TABSIM actualizado
        System.out.println("\nTABSIM actualizado guardado en " + TABSIM_OUTPUT_FILE);
        for (Map<String, String> entry : TABSIM) {
            System.out.println(entry);
        }
    }
}