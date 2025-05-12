import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

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

    // FOLLOW sets son solo para referencia de la gramática original, no se usan para poblar tablas.
    // private static final List<String> FOLLOW_A = Arrays.asList("$", "||", ")");
    // ... (otros FOLLOW sets)


    private static void loadParsingTable(String filePath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 4) {
                    int state = Integer.parseInt(parts[0]);
                    String type = parts[1]; // "T" for terminal, "N" for non-terminal
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
            throw e; // Re-throw to indicate critical failure
        } catch (IOException e) {
            System.err.println("Error de E/S al leer " + filePath + ": " + e.getMessage());
            throw e; // Re-throw to indicate critical failure
        }
    }

    static {
        // Initialize Grammar Productions
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
            // Asegúrate de que este archivo esté en el directorio desde donde ejecutas el programa,
            // o proporciona la ruta completa.
            loadParsingTable("matriz_parsing.txt");
        } catch (IOException e) {
            System.err.println("Error fatal al cargar la tabla de parsing desde el archivo: " + e.getMessage());
            // Es crucial que la tabla se cargue, así que si falla, es mejor detenerse.
            throw new RuntimeException("No se pudo cargar la tabla de parsing.", e);
        }
    }

      public List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\|\\||&&|==|!=|>=|<=|\\b(?:id|TRUE|FALSE)\\b|[()!+\\-*/%<>]");
        Matcher matcher = pattern.matcher(input.replaceAll("\\s+", ""));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        tokens.add("$");
        return tokens;
    }

    public void parse(String inputExpression) {
        List<String> tokens = tokenize(inputExpression);
        if (tokens.size() == 1 && tokens.get(0).equals("$")) {
            System.out.println("Entrada vacía.");
            return;
        }

        Stack<Object> internalStack = new Stack<>();
        internalStack.push(0);

        int inputPtr = 0;
        System.out.printf("%-30s | %-30s | %-15s\n", "PILA", "ENTRADA", "SALIDA");
        System.out.println(String.join("", Collections.nCopies(80, "-")));

        while (true) {
            int currentState = -1;
            for (int i = internalStack.size() - 1; i >= 0; i--) {
                if (internalStack.get(i) instanceof Integer) {
                    currentState = (Integer) internalStack.get(i);
                    break;
                }
            }
            if(currentState == -1){ 
                System.out.println("Error: Pila de estados interna inválida.");
                return;
            }

            String currentToken = tokens.get(inputPtr);
            String stackStr = internalStack.stream()
                                          .filter(item -> item instanceof Integer) 
                                          .map(Object::toString)
                                          .collect(Collectors.joining(" "));
            String inputStr = tokens.subList(inputPtr, tokens.size()).stream().collect(Collectors.joining(" "));
            
            Map<String, String> stateActions = actionTable.get(currentState);
            if (stateActions == null) { // Puede ser null si un estado solo tiene GOTO y no acciones para terminales
                 System.out.printf("%-30s | %-30s | Error: Estado %d no encontrado en la tabla de acciones (o no tiene acciones para terminales).\n", stackStr, inputStr, currentState);
                return;
            }
            String action = stateActions.get(currentToken);

            if (action == null) {
                System.out.printf("%-30s | %-30s | Error: No hay acción para el estado %d y el token '%s'.\n", stackStr, inputStr, currentState, currentToken);
                System.out.print("Tokens esperados (ACTION) desde el estado " + currentState + ": ");
                List<String> expectedTokens = new ArrayList<>(stateActions.keySet());
                System.out.println(String.join(", ", expectedTokens));
                return;
            }
            
            System.out.printf("%-30s | %-30s | %-15s\n", stackStr, inputStr, action);

            if (action.startsWith("s")) { // Shift
                int nextState = Integer.parseInt(action.substring(1));
                internalStack.push(currentToken); 
                internalStack.push(nextState);
                inputPtr++;
            } else if (action.startsWith("r")) { // Reduce
                int ruleNumber = Integer.parseInt(action.substring(1));
                ProductionRule rule = grammarProductions.get(ruleNumber);
                if (rule == null) {
                    System.out.println("Error: Regla de producción desconocida " + ruleNumber);
                    return;
                }

                for (int i = 0; i < rule.rhsLength * 2; i++) { 
                    if(!internalStack.isEmpty()) internalStack.pop();
                    else {
                         System.out.println("Error: Pila interna vacía inesperadamente durante la reducción.");
                         return;
                    }
                }
                
                int stateBeforeReduce = -1;
                 for(int i = internalStack.size() -1; i >=0; i--){
                    if(internalStack.get(i) instanceof Integer){
                        stateBeforeReduce = (Integer) internalStack.get(i);
                        break;
                    }
                }
                if(stateBeforeReduce == -1){
                     System.out.println("Error: Pila de estados interna inválida después de pop en reducción.");
                    return;
                }

                Map<String, Integer> stateGotos = gotoTable.get(stateBeforeReduce);
                if (stateGotos == null || !stateGotos.containsKey(rule.lhs)) {
                     System.out.printf("Error: No hay GOTO para el estado %d y el no terminal '%s'.\n", stateBeforeReduce, rule.lhs);
                     System.out.print("No-terminales esperados (GOTO) desde el estado " + stateBeforeReduce + ": ");
                     if (stateGotos != null) {
                        System.out.println(String.join(", ", stateGotos.keySet()));
                     } else {
                        System.out.println("Ninguno definido.");
                     }
                     return;
                }
                int nextState = stateGotos.get(rule.lhs);
                internalStack.push(rule.lhs); 
                internalStack.push(nextState); 
            } else if (action.equals("acc")) { // Accept
                System.out.println("Entrada aceptada.");
                break;
            } else {
                System.out.println("Error: Acción desconocida en la tabla: " + action);
                return;
            }
        }
    }

    public static void main(String[] args) {
        LRParser parser = new LRParser();
        Scanner scanner = new Scanner(System.in);

        System.out.println("Introduce la operación (ej: ( id + id ) * id ):");
        String input = scanner.nextLine();
        
        if (input.trim().isEmpty()){
            System.out.println("Entrada vacía, usando ejemplo: ( id + id * id ) / id < id || id + id * id == id");
            input = "( id + id * id ) / id < id || id + id * id == id";
        }

        parser.parse(input);
        scanner.close();
    }
}