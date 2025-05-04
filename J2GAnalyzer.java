import java.util.*;
import java.util.regex.*;

public class J2GAnalyzer {

    private static final List<Map<String, String>> TAMSIM = new ArrayList<>();

    // Eliminar comentarios, espacios extras y líneas vacías
    private static String preprocess(String code) {
        code = code.replaceAll("//.*", ""); // Eliminar comentarios
        code = code.replaceAll("(?m)^\\s+", "").replaceAll("(?m)\\s+$", "").replaceAll("\\n{2,}", "\n");
        return code.trim();
    }

    // Verificar que el código esté dentro de FUNC J2G Main() { }
    private static String extractMainBlock(String code) {
        String mainPattern = "^\\s*FUNC\\s+J2G\\s+Main\\s*\\(\\s*\\)\\s*\\{(.*)\\}\\s*$";
        Pattern pattern = Pattern.compile(mainPattern, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(code);
        if (matcher.matches()) {
            return matcher.group(1).trim(); // Extraer contenido dentro de FUNC J2G Main() { }
        } else {
            throw new IllegalArgumentException("Error: El código no está encapsulado dentro de 'FUNC J2G Main() { }'.");
        }
    }

    // Analizar una línea de código y agregar a TAMSIM si aplica
    private static boolean analyzeLine(String line) {
        // Expresiones regulares para validaciones
        String intDeclaration = "^\\s*INT\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(\\s*:=\\s*-?\\d+)?\\s*;$";
        String strDeclaration = "^\\s*STR\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(\\s*:=\\s*\"[^\"]*\")?\\s*;$";
        String boolDeclaration = "^\\s*BOOL\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(\\s*:=\\s*(TRUE|FALSE|true|false))?\\s*;$";
        String assignment = "^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:=\\s*(\"[^\"]*\"|\\d+(\\s*[\\+\\-\\*/%]\\s*\\d+)*|[a-zA-Z_][a-zA-Z0-9_]*(\\s*[\\+\\-\\*/%]\\s*[a-zA-Z_][a-zA-Z0-9_]*)*)\\s*;$";
        String printCall = "^\\s*Print\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\\".*?\\\")\\s*\\)\\s*;$";

        // Validar línea contra patrones
        if (line.matches(intDeclaration)) {
            addToTAMSIM(line, "INT");
            return true;
        } else if (line.matches(strDeclaration)) {
            addToTAMSIM(line, "STR");
            return true;
        } else if (line.matches(boolDeclaration)) {
            addToTAMSIM(line, "BOOL");
            return true;
        } else if (line.matches(assignment)) {
            System.out.println("Asignación válida: " + line);
            return true;
        } else if (line.matches(printCall)) {
            System.out.println("Llamada Print válida: " + line);
            return true;
        } else {
            System.err.println("Error léxico: línea inválida -> " + line);
            return false;
        }
    }

    // Agregar una entrada a TAMSIM
    private static void addToTAMSIM(String line, String type) {
        Pattern pattern = Pattern.compile(type + "\\s+([a-zA-Z_][a-zA-Z0-9_]*)(\\s*:=\\s*.+)?;");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            String identifier = matcher.group(1);
            for (Map<String, String> entry : TAMSIM) {
                if (entry.get("identifier").equals(identifier)) {
                    System.err.println("Error: Identificador duplicado -> " + identifier);
                    return;
                }
            }
            Map<String, String> entry = new HashMap<>();
            entry.put("identifier", identifier);
            entry.put("type", type);
            TAMSIM.add(entry);
            System.out.println("Añadido a TAMSIM: " + entry);
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Ingrese el código fuente de J2G (finalice con una línea vacía):");
        StringBuilder codeBuilder = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).isEmpty()) {
            codeBuilder.append(line).append("\n");
        }
        scanner.close();

        // Etapa 1: Preprocesamiento
        String code = preprocess(codeBuilder.toString());
        System.out.println("Código después del preprocesamiento:\n" + code);

        // Verificar y extraer el bloque principal
        String mainBlock;
        try {
            mainBlock = extractMainBlock(code);
            System.out.println("\nBloque principal extraído:\n" + mainBlock);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return;
        }

        // Etapa 2: Análisis Léxico
        System.out.println("\nIniciando análisis léxico...");
        String[] lines = mainBlock.split("\\n");
        for (String codeLine : lines) {
            analyzeLine(codeLine);
        }

        // Mostrar TAMSIM
        System.out.println("\nTabla TAMSIM generada:");
        for (Map<String, String> entry : TAMSIM) {
            System.out.println(entry);
        }
    }
}