import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class ProductionRule {
    String lhs; // Left-hand side non-terminal
    int rhsLength; // Number of symbols on the right-hand side
    String originalRuleString; // For display purposes, e.g., "A -> A || B"

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

    // Terminals for FOLLOW sets
    private static final List<String> FOLLOW_A = Arrays.asList("$", "||", ")");
    private static final List<String> FOLLOW_B = Arrays.asList("$", "||", "&&", ")");
    private static final List<String> FOLLOW_C = Arrays.asList("$", "||", "&&", "==", "!=", ">", "<", ">=", "<=", ")");
    private static final List<String> FOLLOW_D = Arrays.asList("$", "||", "&&", "==", "!=", ">", "<", ">=", "<=", "+", "-", ")");
    private static final List<String> FOLLOW_E_F_G = Arrays.asList("$", "||", "&&", "==", "!=", ">", "<", ">=", "<=", "+", "-", "*", "/", "%", ")");


    static {
        // Initialize Grammar Productions (Rule numbers match rK actions)
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

        // ---- ACTION Table Initialization ----
        Map<String, String> actions;

        // State 0
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(0, actions);

        // State 1
        actions = new HashMap<>();
        actions.put("||", "s13"); actions.put("$", "acc");
        actionTable.put(1, actions);

        // State 2
        actions = new HashMap<>();
        actions.put("&&", "s14");
        for (String t : FOLLOW_A) actions.put(t, "r3");
        actionTable.put(2, actions);

        // State 3
        actions = new HashMap<>();
        actions.put("==", "s15"); actions.put("!=", "s16"); actions.put(">", "s17");
        actions.put("<", "s18"); actions.put(">=", "s19"); actions.put("<=", "s20");
        for (String t : FOLLOW_B) actions.put(t, "r5");
        actionTable.put(3, actions);

        // State 4
        actions = new HashMap<>();
        actions.put("+", "s21"); actions.put("-", "s22");
        for (String t : FOLLOW_C) actions.put(t, "r6");
        actionTable.put(4, actions);

        // State 5
        actions = new HashMap<>();
        actions.put("*", "s23"); actions.put("/", "s24"); actions.put("%", "s25");
        for (String t : FOLLOW_D) actions.put(t, "r13");
        actionTable.put(5, actions);

        // State 6
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r16"); // FOLLOW(E)
        actionTable.put(6, actions);

        // State 7
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r20"); // FOLLOW(F)
        actionTable.put(7, actions);

        // State 8
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(8, actions);

        // State 9
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(9, actions);

        // State 10
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r23"); // FOLLOW(G)
        actionTable.put(10, actions);

        // State 11
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r24"); // FOLLOW(G)
        actionTable.put(11, actions);

        // State 12
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r25"); // FOLLOW(G)
        actionTable.put(12, actions);

        // State 13
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(13, actions);

        // State 14
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(14, actions);

        // State 15
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(15, actions);
        
        // State 16
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(16, actions);

        // State 17
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(17, actions);

        // State 18
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(18, actions);

        // State 19
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(19, actions);

        // State 20
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(20, actions);

        // State 21
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(21, actions);

        // State 22
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(22, actions);

        // State 23
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(23, actions);

        // State 24
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(24, actions);

        // State 25
        actions = new HashMap<>();
        actions.put("!", "s8"); actions.put("(", "s9");
        actions.put("id", "s10"); actions.put("TRUE", "s11"); actions.put("FALSE", "s12");
        actionTable.put(25, actions);

        // State 26
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r21"); // FOLLOW(F)
        actionTable.put(26, actions);

        // State 27
        actions = new HashMap<>();
        actions.put("||", "s13"); actions.put(")", "s42");
        actionTable.put(27, actions);

        // State 28
        actions = new HashMap<>();
        actions.put("&&", "s14");
        for (String t : FOLLOW_A) actions.put(t, "r2");
        actionTable.put(28, actions);

        // State 29 is I3 - no explicit entry, parser logic handles shared states if GOTO points to I3

        // State 30
        actions = new HashMap<>();
        actions.put("==", "s15"); actions.put("!=", "s16"); actions.put(">", "s17");
        actions.put("<", "s18"); actions.put(">=", "s19"); actions.put("<=", "s20");
        for (String t : FOLLOW_B) actions.put(t, "r4");
        actionTable.put(30, actions);

        // State 31
        actions = new HashMap<>();
        actions.put("+", "s21"); actions.put("-", "s22");
        for (String t : FOLLOW_C) actions.put(t, "r7");
        actionTable.put(31, actions);
        
        // State 32
        actions = new HashMap<>();
        actions.put("+", "s21"); actions.put("-", "s22");
        for (String t : FOLLOW_C) actions.put(t, "r8");
        actionTable.put(32, actions);

        // State 33
        actions = new HashMap<>();
        actions.put("+", "s21"); actions.put("-", "s22");
        for (String t : FOLLOW_C) actions.put(t, "r9");
        actionTable.put(33, actions);

        // State 34
        actions = new HashMap<>();
        actions.put("+", "s21"); actions.put("-", "s22");
        for (String t : FOLLOW_C) actions.put(t, "r10");
        actionTable.put(34, actions);

        // State 35
        actions = new HashMap<>();
        actions.put("+", "s21"); actions.put("-", "s22");
        for (String t : FOLLOW_C) actions.put(t, "r11");
        actionTable.put(35, actions);

        // State 36
        actions = new HashMap<>();
        actions.put("+", "s21"); actions.put("-", "s22");
        for (String t : FOLLOW_C) actions.put(t, "r12");
        actionTable.put(36, actions);

        // State 37
        actions = new HashMap<>();
        actions.put("*", "s23"); actions.put("/", "s24"); actions.put("%", "s25");
        for (String t : FOLLOW_D) actions.put(t, "r14");
        actionTable.put(37, actions);

        // State 38
        actions = new HashMap<>();
        actions.put("*", "s23"); actions.put("/", "s24"); actions.put("%", "s25");
        for (String t : FOLLOW_D) actions.put(t, "r15");
        actionTable.put(38, actions);

        // State 39
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r17"); // FOLLOW(E)
        actionTable.put(39, actions);

        // State 40
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r18"); // FOLLOW(E)
        actionTable.put(40, actions);

        // State 41
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r19"); // FOLLOW(E)
        actionTable.put(41, actions);

        // State 42
        actions = new HashMap<>();
        for (String t : FOLLOW_E_F_G) actions.put(t, "r22"); // FOLLOW(G)
        actionTable.put(42, actions);


        // ---- GOTO Table Initialization ----
        Map<String, Integer> gotos;

        // State 0
        gotos = new HashMap<>(); gotos.put("A",1); gotos.put("B",2); gotos.put("C",3);
        gotos.put("D",4); gotos.put("E",5); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(0, gotos);

        // State 8
        gotos = new HashMap<>(); gotos.put("F",26); gotos.put("G",7);
        gotoTable.put(8, gotos);
        
        // State 9
        gotos = new HashMap<>(); gotos.put("A",27); gotos.put("B",2); gotos.put("C",3);
        gotos.put("D",4); gotos.put("E",5); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(9, gotos);

        // State 13
        gotos = new HashMap<>(); gotos.put("B",28); gotos.put("C",3); gotos.put("D",4);
        gotos.put("E",5); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(13, gotos);
        
        // State 14
        gotos = new HashMap<>(); gotos.put("C",30); gotos.put("D",4); gotos.put("E",5);
        gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(14, gotos);

        // State 15
        gotos = new HashMap<>(); gotos.put("D",31); gotos.put("E",5); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(15, gotos);
        
        // State 16
        gotos = new HashMap<>(); gotos.put("D",32); gotos.put("E",5); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(16, gotos);

        // State 17
        gotos = new HashMap<>(); gotos.put("D",33); gotos.put("E",5); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(17, gotos);

        // State 18
        gotos = new HashMap<>(); gotos.put("D",34); gotos.put("E",5); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(18, gotos);

        // State 19
        gotos = new HashMap<>(); gotos.put("D",35); gotos.put("E",5); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(19, gotos);

        // State 20
        gotos = new HashMap<>(); gotos.put("D",36); gotos.put("E",5); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(20, gotos);

        // State 21
        gotos = new HashMap<>(); gotos.put("E",37); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(21, gotos);

        // State 22
        gotos = new HashMap<>(); gotos.put("E",38); gotos.put("F",6); gotos.put("G",7);
        gotoTable.put(22, gotos);

        // State 23
        gotos = new HashMap<>(); gotos.put("F",39); gotos.put("G",7);
        gotoTable.put(23, gotos);

        // State 24
        gotos = new HashMap<>(); gotos.put("F",40); gotos.put("G",7);
        gotoTable.put(24, gotos);

        // State 25
        gotos = new HashMap<>(); gotos.put("F",41); gotos.put("G",7);
        gotoTable.put(25, gotos);
    }

      public List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        // Regex to capture operators (longer first), identifiers, and parentheses
        // CORRECTED: Added < and > to the single character operator group
        Pattern pattern = Pattern.compile("\\|\\||&&|==|!=|>=|<=|\\b(?:id|TRUE|FALSE)\\b|[()!+\\-*/%<>]");
        Matcher matcher = pattern.matcher(input.replaceAll("\\s+", "")); // Remove all whitespace first
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        tokens.add("$"); // End of input marker
        return tokens;
    }

        public void parse(String inputExpression) {
        List<String> tokens = tokenize(inputExpression);
        if (tokens.size() == 1 && tokens.get(0).equals("$")) {
            System.out.println("Entrada vacía.");
            return;
        }

        Stack<Object> internalStack = new Stack<>(); // This stack will hold states and symbols for logic
        internalStack.push(0); // Initial state

        int inputPtr = 0;
        System.out.printf("%-30s | %-30s | %-15s\n", "PILA", "ENTRADA", "SALIDA");
        System.out.println(String.join("", Collections.nCopies(80, "-")));


        while (true) {
            int currentState = -1;
            // Get current state from the top of the internal stack (if it's an Integer)
            // The actual state for decision making is the last Integer on the internalStack
            for (int i = internalStack.size() - 1; i >= 0; i--) {
                if (internalStack.get(i) instanceof Integer) {
                    currentState = (Integer) internalStack.get(i);
                    break;
                }
            }
            if(currentState == -1){ // Should not happen with proper initialization
                System.out.println("Error: Pila de estados interna inválida.");
                return;
            }

            String currentToken = tokens.get(inputPtr);

            // ---- MODIFICACIÓN AQUÍ para la visualización de la PILA ----
            String stackStr = internalStack.stream()
                                          .filter(item -> item instanceof Integer) // Solo mostrar estados
                                          .map(Object::toString)
                                          .collect(Collectors.joining(" "));
            // ---- FIN DE LA MODIFICACIÓN ----

            String inputStr = tokens.subList(inputPtr, tokens.size()).stream().collect(Collectors.joining(" "));
            
            Map<String, String> stateActions = actionTable.get(currentState);
            if (stateActions == null) {
                 System.out.printf("%-30s | %-30s | Error: Estado %d no encontrado en la tabla de acciones.\n", stackStr, inputStr, currentState);
                return;
            }
            String action = stateActions.get(currentToken);

            if (action == null) {
                System.out.printf("%-30s | %-30s | Error: No hay acción para el estado %d y el token '%s'.\n", stackStr, inputStr, currentState, currentToken);
                System.out.print("Tokens esperados desde el estado " + currentState + ": ");
                List<String> expectedTokens = new ArrayList<>(stateActions.keySet());
                System.out.println(String.join(", ", expectedTokens));
                return;
            }
            
            System.out.printf("%-30s | %-30s | %-15s\n", stackStr, inputStr, action);

            if (action.startsWith("s")) { // Shift
                int nextState = Integer.parseInt(action.substring(1));
                internalStack.push(currentToken); // Aún necesitamos el símbolo en la pila interna para las reducciones
                internalStack.push(nextState);
                inputPtr++;
            } else if (action.startsWith("r")) { // Reduce
                int ruleNumber = Integer.parseInt(action.substring(1));
                ProductionRule rule = grammarProductions.get(ruleNumber);
                if (rule == null) {
                    System.out.println("Error: Regla de producción desconocida " + ruleNumber);
                    return;
                }

                // Pop 2 items for each RHS symbol (symbol & state) from the internal stack
                for (int i = 0; i < rule.rhsLength * 2; i++) { 
                    if(!internalStack.isEmpty()) internalStack.pop();
                    else {
                         System.out.println("Error: Pila interna vacía inesperadamente durante la reducción.");
                         return;
                    }
                }
                
                int stateBeforeReduce = -1;
                // Get the state from the top of the internal stack after popping
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
                     return;
                }
                int nextState = stateGotos.get(rule.lhs);
                internalStack.push(rule.lhs); // Push the non-terminal
                internalStack.push(nextState);  // Push the new state
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
            // input = "id + id * id"; // Simpler example
        }

        parser.parse(input);
        scanner.close();
    }
}