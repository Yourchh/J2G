package AnalizadorSegmanticoJ2G;
import AnalizadorLexicoJ2G.*;
import java.util.List;


public class app {
    public static void main(String[] args) {
        String archivoEntrada = "J2G/AnalizadorLexicoJ2G/entrada.txt"; // Archivo de entrada con el código fuente a analizar
        String archivoTabsim = "J2G/AnalizadorLexicoJ2G/tabsim.txt"; // Archivo de tabla de símbolos

        TablaSimbolos tablaSimbolos = new TablaSimbolos();
        AnalizadorLexicoCore analizadorLexico = new AnalizadorLexicoCore(tablaSimbolos);
        ValidadorEstructural validador = new ValidadorEstructural(tablaSimbolos);

        System.out.println("Cargando archivo de tabla de símbolos: " + archivoTabsim);
        tablaSimbolos.cargarTabsimDesdeArchivo(archivoTabsim);

        System.out.println("Se cargó el archivo " + archivoEntrada + " el cual se está analizando.");
        String codigoOriginal = AnalizadorLexicoJ2G.J2GAnalizadorApp.leerArchivo(archivoEntrada);
        if (codigoOriginal == null) {
            System.err.println("Error: No se pudo leer el archivo de entrada.");
            return;
        }

        System.out.println("\nINICIANDO PROCESAMIENTO DEL CÓDIGO");

        // --- FASE 1 ---
        System.out.println("\nRESULTADO DE LA FASE 1:");
        System.out.println("Se eliminan comentarios y se tokeniza el código.\n");
        List<String> tokensFase1 = analizadorLexico.fase1_limpiarYTokenizar(codigoOriginal);
        String codigoLimpioFormateado = AnalizadorLexicoJ2G.J2GAnalizadorApp.prettyPrintCode(tokensFase1);
        System.out.println(codigoLimpioFormateado);

        // --- FASE 2 ---
        System.out.println("\nRESULATDO DE LA FASE 2:");
        System.out.println("Se transforman los tokens a IDs y se muestran las nuevas variables detectadas.\n");
        List<String> tokensTransformados = analizadorLexico.fase2_transformarTokens(tokensFase1);
        String codigoTransformadoFormateado = AnalizadorLexicoJ2G.J2GAnalizadorApp.prettyPrintCode(tokensTransformados);
        System.out.println(codigoTransformadoFormateado);

        System.out.println("\nNUEVA TABLA DE SÍMBOLOS:\n");
        AnalizadorLexicoJ2G.J2GAnalizadorApp.mostrarNuevasVariablesConsola(tablaSimbolos.getNuevasVariablesDetectadas());
        
        System.out.println("\nValidación de estructura del código (reglas internas):"); // Mensaje actualizado
        validador.validarEstructuraConRegex(codigoLimpioFormateado); // Ya no se pasa el nombre del archivo de reglas

        /* Ahora se indentificaran cada una de las estructuras de control del codigo para el analises Sintactico y Semantico
        de la condicion definida dentro por ejemplo if (id < 5) teniendo en consideracion el id de la variable y su tipo para
        el analisis semantico */

    }
}
