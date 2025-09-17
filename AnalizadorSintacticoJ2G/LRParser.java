package AnalizadorSintacticoJ2G;

import AnalizadorLexicoJ2G.TablaSimbolos;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class ProductionRule {
    String lhs;
    int rhsLength;
    String originalRuleString;

    public ProductionRule(String lhs, int rhsLength, String originalRuleString) {
        this.lhs = lhs;
        this.rhsLength = rhsLength;
        this.originalRuleString = originalRuleString;
    }
}

public class LRParser {

    private static final Map<Integer, Map<String, String>> actionTable = new HashMap<>();
    private static final Map<Integer, Map<String, Integer>> gotoTable = new HashMap<>();
    private static final Map<Integer, ProductionRule> grammarProductions = new HashMap<>();
    private PrintStream outputStream;
    private TablaSimbolos tablaSimbolos;

    public static final String PARSING_TABLE_PATH = "J2G/AnalizadorSintacticoJ2G/matriz_parsing.txt";

    static {
        grammarProductions.put(1, new ProductionRule("S_prime", 1, "S' -> A"));
        grammarProductions.put(2, new ProductionRule("A", 3, "A -> A || B"));
        grammarProductions.put(3, new ProductionRule("A", 1, "A -> B"));
        grammarProductions.put(4, new ProductionRule("B", 3, "B -> B && C"));
        grammarProductions.put(5, new ProductionRule("B", 1, "B -> C"));
        grammarProductions.put(6, new ProductionRule("C", 1, "C -> D"));
        grammarProductions.put(7, new ProductionRule("C", 3, "C -> C == D"));
        grammarProductions.put(8, new ProductionRule("C", 3, "C -> C != D"));
        grammarProductions.put(9, new ProductionRule("C", 3, "C -> C > D"));
        grammarProductions.put(10, new ProductionRule("C", 3, "C -> C < D"));
        grammarProductions.put(11, new ProductionRule("C", 3, "C -> C >= D"));
        grammarProductions.put(12, new ProductionRule("C", 3, "C -> C <= D"));
        grammarProductions.put(13, new ProductionRule("D", 1, "D -> E"));
        grammarProductions.put(14, new ProductionRule("D", 3, "D -> D + E"));
        grammarProductions.put(15, new ProductionRule("D", 3, "D -> D - E"));
        grammarProductions.put(16, new ProductionRule("E", 1, "E -> F"));
        grammarProductions.put(17, new ProductionRule("E", 3, "E -> E * F"));
        grammarProductions.put(18, new ProductionRule("E", 3, "E -> E / F"));
        grammarProductions.put(19, new ProductionRule("E", 3, "E -> E % F"));
        grammarProductions.put(20, new ProductionRule("F", 1, "F -> G"));
        grammarProductions.put(21, new ProductionRule("F", 2, "F -> ! F")); 
        grammarProductions.put(22, new ProductionRule("G", 3, "G -> ( A )"));
        grammarProductions.put(23, new ProductionRule("G", 1, "G -> id"));
        grammarProductions.put(24, new ProductionRule("G", 1, "G -> TRUE"));
        grammarProductions.put(25, new ProductionRule("G", 1, "G -> FALSE"));
        
        try {
            loadParsingTable(PARSING_TABLE_PATH);
        } catch (IOException e) {
            System.err.println("Error fatal al cargar la tabla de parsing desde el archivo: " + e.getMessage());
            throw new RuntimeException("No se pudo cargar la tabla de parsing.", e);
        }
    }
    
    public LRParser(PrintStream outputStream, TablaSimbolos tablaSimbolos) {
        this.outputStream = outputStream;
        this.tablaSimbolos = tablaSimbolos;
    }

    private List<String> createNormalizedList(List<String> originalTokens) {
        List<String> normalized = new ArrayList<>();
        Pattern idPattern = Pattern.compile("^id[0-9]+$");
        for (String token : originalTokens) {
            if (idPattern.matcher(token).matches()) {
                normalized.add("id");
            } else {
                normalized.add(token);
            }
        }
        return normalized;
    }
    
    public boolean parse(List<String> inputTokens) {
        List<String> originalTokens = inputTokens; 
        List<String> normalizedTokens = createNormalizedList(originalTokens);

        if (normalizedTokens.isEmpty() || (normalizedTokens.size() == 1 && normalizedTokens.get(0).equals("$"))) {
            return true;
        }

        Stack<Object> internalStack = new Stack<>();
        Stack<String> semanticStack = new Stack<>();
        internalStack.push(0); 
        semanticStack.push("_");

        int inputPtr = 0; 
        
        outputStream.printf("%-30s | %-30s | %-30s | %-15s\n", "P. SEMANTICA", "PILA", "ENTRADA", "SALIDA");
        outputStream.println(String.join("", Collections.nCopies(110, "-")));

        while (true) {
            int currentState = (Integer) internalStack.peek();
            String currentNormalizedToken = normalizedTokens.get(inputPtr);

            String semanticStackStr = semanticStack.stream().collect(Collectors.joining(" "));
            String stackStr = internalStack.stream()
                                          .filter(item -> item instanceof Integer)
                                          .map(Object::toString)
                                          .collect(Collectors.joining(" "));
            String inputStr = normalizedTokens.subList(inputPtr, normalizedTokens.size()).stream().collect(Collectors.joining(" "));
            
            String action = actionTable.getOrDefault(currentState, new HashMap<>()).get(currentNormalizedToken);

            if (action == null) {
                outputStream.printf("%-30s | %-30s | %-30s | Error: No hay acción para el estado %d y el token '%s'.\n", semanticStackStr, stackStr, inputStr, currentState, currentNormalizedToken);
                return false;
            }
            
            outputStream.printf("%-30s | %-30s | %-30s | %-15s\n", semanticStackStr, stackStr, inputStr, action);

            if (action.startsWith("s")) {
                int nextState = Integer.parseInt(action.substring(1));
                internalStack.push(currentNormalizedToken); 
                internalStack.push(nextState);

                String tokenForSemantic = originalTokens.get(inputPtr);
                String tipo = tablaSimbolos.getTipoDeIdSimplificado(tokenForSemantic);
                semanticStack.push(tipo);

                inputPtr++;
            } else if (action.startsWith("r")) {
                int ruleNumber = Integer.parseInt(action.substring(1));
                ProductionRule rule = grammarProductions.get(ruleNumber);
                if (rule == null) {
                    outputStream.println("Error: Regla de producción desconocida " + ruleNumber);
                    return false;
                }
                
                String t1 = "", t2 = "", t3 = "";
                for (int i = 0; i < rule.rhsLength; i++) {
                    internalStack.pop(); // State
                    internalStack.pop(); // Symbol
                    if (!semanticStack.isEmpty()) {
                         if (i==0) t1 = semanticStack.pop();
                         if (i==1) t2 = semanticStack.pop();
                         if (i==2) t3 = semanticStack.pop();
                    }
                }
                
                int stateBeforeReduce = (Integer) internalStack.peek();
                internalStack.push(rule.lhs);
                int nextState = gotoTable.get(stateBeforeReduce).get(rule.lhs);
                internalStack.push(nextState);

                String resultingType = performSemanticAction(ruleNumber, t1, t2, t3);
                if (resultingType.equals("ERROR")) {
                    outputStream.println("Error Semántico: Incompatibilidad de tipos en la regla " + rule.originalRuleString);
                }
                semanticStack.push(resultingType);
            } else if (action.equals("acc")) {
                outputStream.println("Entrada aceptada.");
                return true;
            } else {
                outputStream.println("Error: Acción desconocida en la tabla: " + action);
                return false;
            }
        }
    }

     private String performSemanticAction(int ruleNumber, String t1, String t2, String t3) {
         switch (ruleNumber) {
            case 3: case 5: case 6: case 13: case 16: case 20: // Propagación
            case 23: return t1; // G -> id
            case 24: case 25: return "b"; // G -> TRUE/FALSE
            
            case 22: return t2; // G -> ( A )

            case 2: case 4: // A -> A || B, B -> B && C
                if (t1.equals("b") && t3.equals("b")) return "b";
                return "ERROR";

            case 7: case 8: // C -> C == D, C -> C != D
                if (t1.equals(t3) && !t1.equals("_") && !t1.equals("ERROR")) return "b";
                return "ERROR";

            case 9: case 10: case 11: case 12: // C -> C > D, etc.
                if (t1.equals("i") && t3.equals("i")) return "b";
                return "ERROR";

            case 14: case 15: case 17: case 18: case 19: // Operaciones Aritméticas
                if (t1.equals("i") && t3.equals("i")) return "i";
                return "ERROR";

            case 21: // F -> ! F
                if (t1.equals("b")) return "b";
                return "ERROR";

            default: return "_";
        }
    }
    
    public static void loadParsingTable(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 4) {
                    int state = Integer.parseInt(parts[0]);
                    String type = parts[1];
                    String symbol = parts[2];
                    String value = parts[3];

                    if ("T".equalsIgnoreCase(type)) {
                        actionTable.computeIfAbsent(state, k -> new HashMap<>()).put(symbol, value);
                    } else if ("N".equalsIgnoreCase(type)) {
                        gotoTable.computeIfAbsent(state, k -> new HashMap<>()).put(symbol, Integer.parseInt(value));
                    }
                }
            }
        } catch (NumberFormatException | IOException e) {
            System.err.println("Error al leer la tabla de parsing " + filePath + ": " + e.getMessage());
            throw new IOException("Error en el formato del archivo de la tabla de parsing.", e);
        }
    }
}