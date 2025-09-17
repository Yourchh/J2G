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
    private Map<String, String> variableToIdMap;
    private Map<String, String> literalToIdMap;
    private int nextIdCounter;
    private List<String> palabrasReservadasYSimbolosConocidos;
    private Map<String, String> tokenToTypeMap = null;

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
            br.readLine();
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
        // **LA CORRECCIÓN CLAVE ESTÁ AQUÍ**
        // Se anula el mapa para forzar su reconstrucción con la nueva variable.
        this.tokenToTypeMap = null; 
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

    public String getTipoDeIdSimplificado(String token) {
        if (tokenToTypeMap == null) {
            tokenToTypeMap = new HashMap<>();
            for (SymbolTableEntry entry : nuevasVariablesDetectadas) {
                tokenToTypeMap.put(entry.variable, entry.tipo);
                tokenToTypeMap.put(entry.id, entry.tipo);
            }
        }

        String tipo = tokenToTypeMap.getOrDefault(token, "desconocido");

        switch (tipo.toLowerCase()) {
            case "int": return "i";
            case "string": return "s";
            case "str": return "s"; // Alias para robustez
            case "bool": return "b";
        }
        
        if (token.matches("\"(?:\\\\.|[^\"\\\\])*\"")) return "s";
        if (token.matches("[0-9]+")) return "i";
        if (token.equalsIgnoreCase("true") || token.equalsIgnoreCase("false") || token.equalsIgnoreCase("TRUE") || token.equalsIgnoreCase("FALSE")) return "b";
        
        return "_";
    }
}