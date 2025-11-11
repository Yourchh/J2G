package AnalizadorSemanticoJ2G;

import AnalizadorLexicoJ2G.*;
import AnalizadorSintacticoJ2G.LRParser;
import java.io.PrintStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class app {
    public static void main(String[] args) {
        String archivoEntrada = "J2G/AnalizadorLexicoJ2G/entrada.txt";
        String archivoTabsim = "J2G/AnalizadorLexicoJ2G/tabsim.txt";
        String archivoErrores = "J2G/AnalizadorSemanticoJ2G/errores.txt";
        String archivoProcesado = "J2G/AnalizadorSemanticoJ2G/codigo_procesado.txt";
        String archivoTablas = "J2G/AnalizadorSemanticoJ2G/tablas_analisis.txt";
        // --- RUTA DEL ARCHIVO DE SALIDA ---
        String archivoAsm = "J2G/AnalizadorSemanticoJ2G/salida_ensamblador.asm";

        // Mensaje de éxito movido al final
        
        try (
            PrintStream outErrors = new PrintStream(new File(archivoErrores));
            PrintStream outProcessedCode = new PrintStream(new File(archivoProcesado));
            PrintStream outTables = new PrintStream(new File(archivoTablas))
            // --- outAsm eliminado de aquí ---
        ) {
            System.setErr(outErrors);
            System.setOut(outProcessedCode);

            TablaSimbolos tablaSimbolos = new TablaSimbolos();
            AnalizadorLexicoCore analizadorLexico = new AnalizadorLexicoCore(tablaSimbolos);
            LRParser parser = new LRParser(outTables, tablaSimbolos); 
            
            // --- MODIFICACIÓN: Pasar la RUTA de ASM, no el PrintStream ---
            AnalizadorLexicoJ2G.ValidadorEstructural validador = new AnalizadorLexicoJ2G.ValidadorEstructural(tablaSimbolos, analizadorLexico, outTables, archivoAsm);

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
            
            // --- FASE 3 (Validación Semántica y Generación ASM) ---
            System.out.println("\nValidación de estructura del código (reglas internas):");
            validador.validarEstructuraConRegex(codigoLimpioFormateado, parser);
            
            System.out.println(codigoTransformadoFormateado);

            System.out.println("\nNUEVA TABLA DE SÍMBOLOS:\n");
            AnalizadorLexicoJ2G.J2GAnalizadorApp.mostrarNuevasVariablesConsola(tablaSimbolos.getNuevasVariablesDetectadas());

            // --- NUEVA LLAMADA: Escribir el archivo ASM completo al final ---
            validador.escribirAsmFinal(tablaSimbolos);
            
            // --- Mensaje final ---
            System.out.println("\nEl análisis ha finalizado. Revise los archivos 'errores.txt', 'codigo_procesado.txt', 'tablas_analisis.txt' y 'salida_ensamblador.asm' para ver los resultados.");


        } catch (FileNotFoundException e) {
            System.err.println("Error: No se pudo crear el archivo de salida: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Error al escribir el archivo ASM final: " + e.getMessage());
        }
    }
}