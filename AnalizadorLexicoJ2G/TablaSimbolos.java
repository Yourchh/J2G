/**
 * Clase que maneja la tabla de símbolos para el analizador léxico.
 * Almacena y gestiona símbolos, variables, literales y sus identificadores.
 *
 * La clase mantiene:
 * - Una tabla base de símbolos
 * - Un registro de nuevas variables detectadas
 * - Mapeos de variables a IDs
 * - Mapeos de literales a IDs
 * - Un contador para generar IDs únicos
 * - Una lista de palabras reservadas y símbolos conocidos
 *
 * Los símbolos se cargan desde un archivo y se pueden agregar nuevos elementos
 * durante el análisis. La clase proporciona métodos para:
 * - Cargar la tabla desde archivo
 * - Generar nuevos IDs
 * - Agregar y consultar variables y literales
 * - Actualizar valores después del análisis
 */

package AnalizadorLexicoJ2G;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TablaSimbolos {
    private Map<String, SymbolTableEntry> tabsimBase;
    private List<SymbolTableEntry> nuevasVariablesDetectadas;
    private Map<String, String> variableToIdMap; // IDs para variables de usuario
    private Map<String, String> literalToIdMap;  // IDs para literales (números, strings)
    private int nextIdCounter;
    private List<String> palabrasReservadasYSimbolosConocidos;

    public TablaSimbolos() {
        this.tabsimBase = new HashMap<>();
        this.nuevasVariablesDetectadas = new ArrayList<>();
        this.variableToIdMap = new LinkedHashMap<>();
        this.literalToIdMap = new LinkedHashMap<>();
        this.nextIdCounter = 1;
        this.palabrasReservadasYSimbolosConocidos = new ArrayList<>();
    }

    public void cargarTabsimDesdeArchivo(String archivoTabsimPath) {
        try (BufferedReader br = new BufferedReader(new FileReader(archivoTabsimPath))) {
            String linea;
            br.readLine(); // Omitir cabecera
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split("\t");
                if (partes.length >= 3) { 
                    String variable = partes[0].trim();
                    String id = partes[1].trim();
                    String tipo = partes[2].trim();
                    String valor = (partes.length == 4) ? partes[3].trim() : "";
                    SymbolTableEntry entry = new SymbolTableEntry(variable, id, tipo, valor);
                    this.tabsimBase.put(variable, entry); 
                    this.palabrasReservadasYSimbolosConocidos.add(variable);
                }
            }
        } catch (IOException e) {
            System.err.println("Error al cargar " + archivoTabsimPath + ": " + e.getMessage());
        }
    }

    public String generarProximoId() {
        return "id" + nextIdCounter++;
    }

    public void agregarNuevaVariable(SymbolTableEntry entry) {
        this.nuevasVariablesDetectadas.add(entry);
    }
    
    public void agregarVariableConId(String token, String id) {
        this.variableToIdMap.put(token, id);
    }

    public String obtenerIdParaVariable(String token) {
        return this.variableToIdMap.get(token);
    }

    public boolean contieneVariable(String token) {
        return this.variableToIdMap.containsKey(token);
    }

    public void agregarLiteralConId(String token, String id) {
        this.literalToIdMap.put(token, id);
    }

    public String obtenerIdParaLiteral(String token) {
        return this.literalToIdMap.get(token);
    }
    
    public boolean contieneLiteral(String token) {
        return this.literalToIdMap.containsKey(token);
    }

    public List<SymbolTableEntry> getNuevasVariablesDetectadas() {
        return nuevasVariablesDetectadas;
    }

    public List<String> getPalabrasReservadasYSimbolosConocidos() {
        return palabrasReservadasYSimbolosConocidos;
    }
    
    public Map<String, SymbolTableEntry> getTabsimBase() {
        return tabsimBase;
    }

    public void actualizarValoresDeVariablesPostAnalisis() {
        for (SymbolTableEntry entry : nuevasVariablesDetectadas) {
            if (!(entry.tipo.equals("int") && entry.variable.equals(entry.valor)) && 
                !(entry.tipo.equals("string") && entry.variable.startsWith("\""))) {
                
                if (variableToIdMap.containsKey(entry.valor)) { 
                    entry.valor = variableToIdMap.get(entry.valor);
                } else if (literalToIdMap.containsKey(entry.valor)) { 
                    entry.valor = literalToIdMap.get(entry.valor);
                }
            }
        }
    }
}
