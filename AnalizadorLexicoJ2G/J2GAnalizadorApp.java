package AnalizadorLexicoJ2G;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class J2GAnalizadorApp {

    public static void main(String[] args) {
        String archivoEntrada = "J2G/AnalizadorLexicoJ2G/entrada.txt";
        String archivoTabsim = "J2G/AnalizadorLexicoJ2G/tabsim.txt";

        // --- INICIO DE LA CONFIGURACIÓN ---
        // Configura la salida para que todo se imprima en la consola.
        PrintStream outConsola = System.out;
        PrintStream errConsola = System.err;

        // Se redirige la salida de error estándar a la consola de salida normal
        // para asegurar que los mensajes de error aparezcan en orden con el resto del proceso.
        System.setErr(outConsola);

        // --- INICIO DEL ANÁLISIS ---
        TablaSimbolos tablaSimbolos = new TablaSimbolos();
        AnalizadorLexicoCore analizadorLexico = new AnalizadorLexicoCore(tablaSimbolos);
        
        // Se crea el LRParser y el ValidadorEstructural, pasándoles la salida de la consola.

        outConsola.println("Cargando archivo de tabla de símbolos: " + archivoTabsim);
        tablaSimbolos.cargarTabsimDesdeArchivo(archivoTabsim);

        outConsola.println("Se cargó el archivo " + archivoEntrada + " el cual se está analizando.");
        String codigoOriginal = leerArchivo(archivoEntrada);
        if (codigoOriginal == null) {
            errConsola.println("Error: No se pudo leer el archivo de entrada.");
            return;
        }

        outConsola.println("\nINICIANDO PROCESAMIENTO DEL CÓDIGO");

        // --- FASE 1 ---
        outConsola.println("\nRESULTADO DE LA FASE 1:");
        outConsola.println("Se eliminan comentarios y se tokeniza el código.\n");
        List<String> tokensFase1 = analizadorLexico.fase1_limpiarYTokenizar(codigoOriginal);
        String codigoLimpioFormateado = prettyPrintCode(tokensFase1);
        outConsola.println(codigoLimpioFormateado);

        // --- FASE 2 ---
        outConsola.println("\nRESULATDO DE LA FASE 2:");
        outConsola.println("Se transforman los tokens a IDs y se muestran las nuevas variables detectadas.\n");
        List<String> tokensTransformados = analizadorLexico.fase2_transformarTokens(tokensFase1);
        String codigoTransformadoFormateado = prettyPrintCode(tokensTransformados);
        outConsola.println(codigoTransformadoFormateado);

        outConsola.println("\nNUEVA TABLA DE SÍMBOLOS:\n");
        mostrarNuevasVariablesConsola(tablaSimbolos.getNuevasVariablesDetectadas());
        
        outConsola.println("\nEl análisis ha finalizado. Revisa la consola para ver todos los resultados.");
    }

    // --- MODIFICACIÓN: Cambiado a public y sin trim() ---
    public static String leerArchivo(String nombreArchivo) {
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
        return contenido.toString();
    }

    public static String prettyPrintCode(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;
        final String indentUnit = "  "; 
        boolean atStartOfLine = true;

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String prevToken = (i > 0) ? tokens.get(i-1) : "";
            String nextToken = (i + 1 < tokens.size()) ? tokens.get(i + 1) : "";

            if (token.equals("}")) {
                indentLevel = Math.max(0, indentLevel - 1);
                if (!atStartOfLine) { 
                    sb.append("\n");
                }
                appendIndent(sb, indentLevel, indentUnit);
            } else if (atStartOfLine) {
                if (!(token.equals("else") && prevToken.equals("}"))) {
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
            } else if (token.equals(":") && 
                       (prevToken.equals("caso") || 
                        prevToken.equals("por_defecto") ||
                        (i > 1 && tokens.get(i-2).equals("caso") && (prevToken.matches("\"(?:\\\\.|[^\"\\\\])*\"") || prevToken.matches("[a-zA-Z_][a-zA-Z0-9_]*") || prevToken.matches("[0-9]+")))
                       )) {
                sb.append("\n");
                atStartOfLine = true;

            } else if (token.equals("}")) {
                if (!nextToken.equals("else")) {
                    sb.append("\n");
                    atStartOfLine = true;
                }
            } else {
                if (!nextToken.isEmpty() &&
                    !nextToken.equals(";") &&
                    !nextToken.equals(",") &&
                    !nextToken.equals(")") &&
                    !nextToken.equals("]") &&
                    !nextToken.equals("}") &&
                    !nextToken.equals("{") &&
                    !token.equals("(") &&
                    !token.equals("[") &&
                    !(token.equals("Input") && nextToken.equals(".")) &&
                    !(prevToken.equals("Input") && token.equals(".")) &&
                    !nextToken.equals(".") &&
                    !(nextToken.equals(":") && (token.equals("caso") || token.equals("por_defecto") || token.matches("\"(?:\\\\.|[^\"\\\\])*\"") || token.matches("[a-zA-Z_][a-zA-Z0-9_]*") || token.matches("[0-9]+")))
                   ) {
                    sb.append(" ");
                }
            }
        }
        return sb.toString().replaceAll("\\s+\n", "\n").replaceAll("\n\\s*\n", "\n").trim();
    }

    public static void appendIndent(StringBuilder sb, int indentLevel, String indentUnit) {
        for (int j = 0; j < indentLevel; j++) {
            sb.append(indentUnit);
        }
    }

    public static void mostrarNuevasVariablesConsola(List<SymbolTableEntry> nuevasVariables) {
        System.out.println(new String(new char[110]).replace("\0", "-"));
        System.out.println(String.format("%-40s | %-10s | %-10s | %-40s", "VARIABLE", "ID", "TIPO", "VALOR"));
        System.out.println(new String(new char[110]).replace("\0", "-"));
        
        Map<String, SymbolTableEntry> displayOrderMap = new LinkedHashMap<>();
        for(SymbolTableEntry entry : nuevasVariables){
            if(entry.tipo.equals("int") || entry.tipo.equals("string") || entry.tipo.equals("bool")){
                 displayOrderMap.putIfAbsent(entry.id, entry); 
            }
        }
        for(SymbolTableEntry entry : nuevasVariables){
            if(!(entry.tipo.equals("int") || entry.tipo.equals("string") || entry.tipo.equals("bool"))){
                 displayOrderMap.putIfAbsent(entry.id, entry);
            }
        }
        for (SymbolTableEntry entry : displayOrderMap.values()) {
            System.out.println(entry);
        }
        System.out.println(new String(new char[10]).replace("\0", "-"));
    }
}