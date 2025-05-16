package AnalizadorLexicoJ2G;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class J2GAnalizadorApp {

    public static void main(String[] args) {
        String archivoEntrada = "/Users/jorgeandreshernandezpelayo/Documents/.yorch/Escuela/Codigos/J2G/AnalizadorLexicoJ2G/entrada.txt"; 
        String archivoTabsim = "/Users/jorgeandreshernandezpelayo/Documents/.yorch/Escuela/Codigos/J2G/AnalizadorLexicoJ2G/tabsim.txt";
        // Ya no se necesita archivoReglasRegex

        TablaSimbolos tablaSimbolos = new TablaSimbolos();
        AnalizadorLexicoCore analizadorLexico = new AnalizadorLexicoCore(tablaSimbolos);
        ValidadorEstructural validador = new ValidadorEstructural(tablaSimbolos);

        System.out.println("Cargando archivo de tabla de símbolos: " + archivoTabsim);
        tablaSimbolos.cargarTabsimDesdeArchivo(archivoTabsim);

        System.out.println("Se cargó el archivo " + archivoEntrada + " el cual se está analizando.");
        String codigoOriginal = leerArchivo(archivoEntrada);
        if (codigoOriginal == null) {
            System.err.println("Error: No se pudo leer el archivo de entrada.");
            return;
        }

        System.out.println("\nINICIANDO PROCESAMIENTO DEL CÓDIGO");

        // --- FASE 1 ---
        System.out.println("\nRESULTADO DE LA FASE 1:");
        System.out.println("Se eliminan comentarios y se tokeniza el código.\n");
        List<String> tokensFase1 = analizadorLexico.fase1_limpiarYTokenizar(codigoOriginal);
        String codigoLimpioFormateado = prettyPrintCode(tokensFase1);
        System.out.println(codigoLimpioFormateado);

        // --- FASE 2 ---
        System.out.println("\nRESULATDO DE LA FASE 2:");
        System.out.println("Se transforman los tokens a IDs y se muestran las nuevas variables detectadas.\n");
        List<String> tokensTransformados = analizadorLexico.fase2_transformarTokens(tokensFase1);
        String codigoTransformadoFormateado = prettyPrintCode(tokensTransformados);
        System.out.println(codigoTransformadoFormateado);

        System.out.println("\nNUEVA TABLA DE SÍMBOLOS:\n");
        mostrarNuevasVariablesConsola(tablaSimbolos.getNuevasVariablesDetectadas());
        
        System.out.println("\nValidación de estructura del código (reglas internas):"); // Mensaje actualizado
        validador.validarEstructuraConRegex(codigoLimpioFormateado); // Ya no se pasa el nombre del archivo de reglas
    }

    private static String leerArchivo(String nombreArchivo) {
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

    private static String prettyPrintCode(List<String> tokens) {
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

    private static void appendIndent(StringBuilder sb, int indentLevel, String indentUnit) {
        for (int j = 0; j < indentLevel; j++) {
            sb.append(indentUnit);
        }
    }

    private static void mostrarNuevasVariablesConsola(List<SymbolTableEntry> nuevasVariables) {
        System.out.println(new String(new char[90]).replace("\0", "-"));
        System.out.println(String.format("%-30s | %-10s | %-10s | %-30s", "VARIABLE", "ID", "TIPO", "VALOR"));
        System.out.println(new String(new char[90]).replace("\0", "-"));
        
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
        System.out.println(new String(new char[90]).replace("\0", "-"));
    }
}