package AnalizadorSintacticoJ2G;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;

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
    
    public LRParser(PrintStream outputStream) {
        this.outputStream = outputStream;
    }

    private List<String> normalizeTokens(List<String> rawTokens) {
        List<String> normalized = new ArrayList<>();
        Pattern idPattern = Pattern.compile("^id[0-9]+$");
        for (String token : rawTokens) {
            Matcher matcher = idPattern.matcher(token);
            if (matcher.matches()) {
                normalized.add("id");
            } else {
                normalized.add(token);
            }
        }
        return normalized;
    }
    
    public boolean parse(List<String> inputTokens) {
        List<String> tokens = normalizeTokens(inputTokens);
        if (tokens.isEmpty() || (tokens.size() == 1 && tokens.get(0).equals("$"))) {
            return true;
        }

        Stack<Object> internalStack = new Stack<>();
        internalStack.push(0); 

        int inputPtr = 0; 
        
        outputStream.printf("%-30s | %-30s | %-15s\n", "PILA", "ENTRADA", "SALIDA");
        outputStream.println(String.join("", Collections.nCopies(80, "-")));

        while (true) {
            int currentState = -1; 
            for (int i = internalStack.size() - 1; i >= 0; i--) {
                if (internalStack.get(i) instanceof Integer) {
                    currentState = (Integer) internalStack.get(i);
                    break;
                }
            }
            if(currentState == -1){ 
                outputStream.println("Error: Pila de estados interna inválida.");
                return false;
            }

            String currentToken = tokens.get(inputPtr);

            String stackStr = internalStack.stream()
                                          .filter(item -> item instanceof Integer) 
                                          .map(Object::toString)
                                          .collect(Collectors.joining(" "));
            String inputStr = tokens.subList(inputPtr, tokens.size()).stream().collect(Collectors.joining(" "));
            
            Map<String, String> stateActions = actionTable.get(currentState);

            if (stateActions == null) {
                 outputStream.printf("%-30s | %-30s | Error: Estado %d no encontrado en la tabla de acciones.\n", stackStr, inputStr, currentState);
                return false;
            }
            
            String action = stateActions.get(currentToken);

            if (action == null) {
                outputStream.printf("%-30s | %-30s | Error: No hay acción para el estado %d y el token '%s'.\n", stackStr, inputStr, currentState, currentToken);
                return false;
            }
            
            outputStream.printf("%-30s | %-30s | %-15s\n", stackStr, inputStr, action);

            if (action.startsWith("s")) {
                int nextState = Integer.parseInt(action.substring(1));
                internalStack.push(currentToken);
                internalStack.push(nextState);
                inputPtr++;
            } else if (action.startsWith("r")) {
                int ruleNumber = Integer.parseInt(action.substring(1));
                ProductionRule rule = grammarProductions.get(ruleNumber);
                if (rule == null) {
                    outputStream.println("Error: Regla de producción desconocida " + ruleNumber);
                    return false;
                }

                for (int i = 0; i < rule.rhsLength * 2; i++) { 
                    if(!internalStack.isEmpty()) internalStack.pop();
                    else {
                         outputStream.println("Error: Pila interna vacía inesperadamente durante la reducción.");
                         return false;
                    }
                }
                
                int stateBeforeReduce = -1;
                 for(int i = internalStack.size() -1; i >=0; i--){
                    if (internalStack.get(i) instanceof Integer) {
                        stateBeforeReduce = (Integer) internalStack.get(i);
                        break;
                    }
                }
                if(stateBeforeReduce == -1){
                     outputStream.println("Error: Pila de estados interna inválida después de pop en reducción.");
                    return false;
                }

                Map<String, Integer> stateGotos = gotoTable.get(stateBeforeReduce);
                if (stateGotos == null || !stateGotos.containsKey(rule.lhs)) {
                     outputStream.printf("Error: No hay GOTO para el estado %d y el no terminal '%s'.\n", stateBeforeReduce, rule.lhs);
                     return false;
                }
                int nextState = stateGotos.get(rule.lhs);
                internalStack.push(rule.lhs);
                internalStack.push(nextState);
            } else if (action.equals("acc")) {
                outputStream.println("Entrada aceptada.");
                return true;
            } else {
                outputStream.println("Error: Acción desconocida en la tabla: " + action);
                return false;
            }
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
                    } else {
                        System.err.println("Advertencia: Tipo de símbolo desconocido en " + filePath + ": " + line);
                    }
                } else if (!line.trim().isEmpty()){
                    System.err.println("Advertencia: Línea malformada en " + filePath + ": " + line);
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Error de formato numérico al leer " + filePath + ": " + e.getMessage());
            throw e;
        } catch (IOException e) {
            System.err.println("Error de E/S al leer " + filePath + ": " + e.getMessage());
            throw e;
        }
    }
}