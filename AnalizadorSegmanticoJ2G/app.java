package AnalizadorSegmanticoJ2G;

import AnalizadorLexicoJ2G.*;
import AnalizadorSintacticoJ2G.LRParser;
import java.io.PrintStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

public class app {
    public static void main(String[] args) {
        String archivoEntrada = "J2G/AnalizadorLexicoJ2G/entrada.txt";
        String archivoTabsim = "J2G/AnalizadorLexicoJ2G/tabsim.txt";
        String archivoErrores = "J2G/AnalizadorSegmanticoJ2G/errores.txt";
        String archivoProcesado = "J2G/AnalizadorSegmanticoJ2G/codigo_procesado.txt";
        String archivoTablas = "J2G/AnalizadorSegmanticoJ2G/tablas_analisis.txt";

        System.out.println("\nEl análisis ha finalizado. Revise los archivos 'errores.txt', 'codigo_procesado.txt' y 'tablas_analisis.txt' para ver los resultados.");

        // Redirigir la salida estándar a los archivos
        try (
            PrintStream outErrors = new PrintStream(new File(archivoErrores));
            PrintStream outProcessedCode = new PrintStream(new File(archivoProcesado));
            PrintStream outTables = new PrintStream(new File(archivoTablas));
        ) {
            System.setErr(outErrors);
            System.setOut(outProcessedCode);

            // Iniciar el análisis del código
            TablaSimbolos tablaSimbolos = new TablaSimbolos();
            AnalizadorLexicoCore analizadorLexico = new AnalizadorLexicoCore(tablaSimbolos);
            LRParser parser = new LRParser(outTables); // Pasar el PrintStream al parser
            ValidadorEstructural validador = new ValidadorEstructural(tablaSimbolos, analizadorLexico, outTables);

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
            
            System.out.println("\nValidación de estructura del código (reglas internas):");
            validador.validarEstructuraConRegex(codigoLimpioFormateado, parser);

        } catch (FileNotFoundException e) {
            System.err.println("Error: No se pudo crear el archivo de salida: " + e.getMessage());
        }

    }
}