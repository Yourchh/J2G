package AnalizadorSintacticoJ2G;

import AnalizadorLexicoJ2G.TablaSimbolos;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
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

    // --- INICIO DE NUEVOS ATRIBUTOS PARA GENERACIÓN ASM ---
    private int tempCounter = 1; // Contador para temporales
    private StringBuilder asmDatos;
    private StringBuilder asmCodigo;
    private Set<String> temporalesDeclarados;
    private String finalTemporalResult;
    private Set<String> idsEncontrados; // --- NUEVO: Para rastrear variables usadas ---
    // --- FIN DE NUEVOS ATRIBUTOS ---


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
        // No inicializar contadores aquí, sino en parse()
    }

    // --- INICIO DE NUEVOS MÉTODOS GETTER PARA ASM ---
    public String getAsmDatos() {
        return (asmDatos != null) ? asmDatos.toString() : "";
    }

    public String getAsmCodigo() {
        return (asmCodigo != null) ? asmCodigo.toString() : "";
    }

    public String getFinalTemporalResult() {
        return finalTemporalResult;
    }

    public Set<String> getIdsEncontrados() { // --- NUEVO GETTER ---
        return this.idsEncontrados;
    }
    // --- FIN DE NUEVOS MÉTODOS GETTER PARA ASM ---


    private List<String> createNormalizedList(List<String> originalTokens) {
        List<String> normalized = new ArrayList<>();
        Pattern idPattern = Pattern.compile("^id[0-9]+$");
        for (String token : originalTokens) {
            if (idPattern.matcher(token).matches()) {
                normalized.add("id");
            } else if (token.equalsIgnoreCase("true")) {
                normalized.add("TRUE");
            } else if (token.equalsIgnoreCase("false")) {
                normalized.add("FALSE");
            }
             else {
                normalized.add(token);
            }
        }
        return normalized;
    }

    public boolean parse(List<String> inputTokens) {
        // --- MODIFICACIÓN: Reiniciar estado para cada análisis ---
        this.tempCounter = 1;
        this.asmDatos = new StringBuilder();
        this.asmCodigo = new StringBuilder();
        this.temporalesDeclarados = new HashSet<>();
        this.finalTemporalResult = null;
        this.idsEncontrados = new HashSet<>(); // --- NUEVO: Inicializar el Set ---
        // --- FIN DE MODIFICACIÓN ---

        List<String> originalTokens = inputTokens;
        List<String> normalizedTokens = createNormalizedList(originalTokens);

        if (normalizedTokens.isEmpty() || (normalizedTokens.size() == 1 && normalizedTokens.get(0).equals("$"))) {
            return true;
        }

        Stack<Object> internalStack = new Stack<>();
        Stack<String> semanticStack = new Stack<>();
        Stack<String> temporalStack = new Stack<>();
        internalStack.push(0);
        semanticStack.push("_");
        temporalStack.push("_");

        int inputPtr = 0;

        outputStream.printf("%-30s | %-30s | %-60s | %-15s | %-15s\n", "P. SEMANTICA", "PILA", "ENTRADA", "SALIDA", "TEMPORAL");
        outputStream.println(String.join("", Collections.nCopies(163, "-")));

        while (true) {
            int currentState = (Integer) internalStack.peek();
            String currentNormalizedToken = normalizedTokens.get(inputPtr);

            String semanticStackStr = semanticStack.stream().collect(Collectors.joining(" "));
            String temporalStackStr = temporalStack.stream().collect(Collectors.joining(" "));
            String stackStr = internalStack.stream()
                                          .filter(item -> item instanceof Integer)
                                          .map(Object::toString)
                                          .collect(Collectors.joining(" "));

            String inputStr = originalTokens.subList(inputPtr, originalTokens.size()).stream().collect(Collectors.joining(" "));

            String action = actionTable.getOrDefault(currentState, new HashMap<>()).get(currentNormalizedToken);

            if (action == null) {
                outputStream.printf("%-30s | %-30s | %-60s | Error: No hay acción para el estado %d y el token '%s'. | %-15s\n",
                                    semanticStackStr, stackStr, inputStr, currentState, currentNormalizedToken, temporalStackStr);
                return false;
            }

            outputStream.printf("%-30s | %-30s | %-60s | %-15s | %-15s\n",
                                semanticStackStr, stackStr, inputStr, action, temporalStackStr);

            if (action.startsWith("s")) {
                int nextState = Integer.parseInt(action.substring(1));
                internalStack.push(currentNormalizedToken);
                internalStack.push(nextState);

                String tokenForSemantic = originalTokens.get(inputPtr);
                String tipo = tablaSimbolos.getTipoDeIdSimplificado(tokenForSemantic);

                semanticStack.push(tipo);

                // --- NUEVO: Rastrear el ID si es un id de variable o literal ---
                if (tokenForSemantic.matches("^id[0-9]+$")) {
                    this.idsEncontrados.add(tokenForSemantic);
                }

                if (tipo.equals("i") || tipo.equals("s") || tipo.equals("b") || tokenForSemantic.matches("^id[0-9]+$") || tokenForSemantic.equalsIgnoreCase("TRUE") || tokenForSemantic.equalsIgnoreCase("FALSE") ) {
                    temporalStack.push(tokenForSemantic);
                } else {
                    temporalStack.push("_");
                }

                inputPtr++;
            } else if (action.startsWith("r")) {
                int ruleNumber = Integer.parseInt(action.substring(1));
                ProductionRule rule = grammarProductions.get(ruleNumber);
                if (rule == null) {
                    outputStream.println("Error: Regla de producción desconocida " + ruleNumber);
                    return false;
                }

                String t1 = "", t2 = "", t3 = ""; // Tipos
                String v1 = "", v2 = "", v3 = ""; // Valores/Temporales
                for (int i = 0; i < rule.rhsLength; i++) {
                    internalStack.pop(); // State
                    internalStack.pop(); // Symbol
                    if (!semanticStack.isEmpty()) {
                         if (i==0) t1 = semanticStack.pop();
                         if (i==1) t2 = semanticStack.pop();
                         if (i==2) t3 = semanticStack.pop();
                    }
                    if (!temporalStack.isEmpty()) {
                         if (i==0) v1 = temporalStack.pop();
                         if (i==1) v2 = temporalStack.pop();
                         if (i==2) v3 = temporalStack.pop();
                    }
                }

                int stateBeforeReduce = (Integer) internalStack.peek();
                internalStack.push(rule.lhs);
                int nextState = gotoTable.get(stateBeforeReduce).get(rule.lhs);
                internalStack.push(nextState);

                String resultingType = performSemanticAction(ruleNumber, t1, t2, t3);

                if (resultingType.equals("ERROR")) {
                    outputStream.println("Error Semántico: Incompatibilidad de tipos en la regla " + rule.originalRuleString);
                    return false;
                }
                semanticStack.push(resultingType);

                // --- INICIO DE MODIFICACIÓN: Generar ASM ---
                String resultingTemporal = getTemporalResult(ruleNumber, v1, v2, v3, this.tempCounter);
                String opSymbol = getOperatorSymbol(ruleNumber);
                boolean newTemporalCreated = false;

                if (rule.rhsLength == 3) { // Operación Binaria (ej: D -> D + E)
                    generateAsmCode(ruleNumber, resultingTemporal, v3, v1, opSymbol);
                    newTemporalCreated = true;
                } else if (rule.rhsLength == 2 && ruleNumber == 21) { // Operación Unaria (ej: F -> ! F)
                    generateAsmCode(ruleNumber, resultingTemporal, v1, null, opSymbol); // operando está en v1
                    newTemporalCreated = true;
                }

                // Si se usó un temporal nuevo (tN), incrementar el contador
                if (newTemporalCreated && resultingTemporal.equals("t" + this.tempCounter)) {
                    this.tempCounter++;
                }
                // --- FIN DE MODIFICACIÓN ---

                temporalStack.push(resultingTemporal); // Apilar el ID/temporal resultante


            } else if (action.equals("acc")) {
                // --- MODIFICACIÓN: Guardar el resultado final ---
                if (!temporalStack.isEmpty()) {
                    this.finalTemporalResult = temporalStack.peek();
                }
                // --- FIN DE MODIFICACIÓN ---
                outputStream.println("Entrada aceptada.");
                return true;
            } else {
                outputStream.println("Error: Acción desconocida en la tabla: " + action);
                return false;
            }
        }
    }

     private String performSemanticAction(int ruleNumber, String t1, String t2, String t3) {
         // Este método solo maneja la validación de TIPOS
         switch (ruleNumber) {
            case 3: case 5: case 6: case 13: case 16: case 20:
            case 23: return t1;
            case 24: case 25: return "b";

            case 22: return t2;

            case 2: case 4:
                if (t1.equals("b") && t3.equals("b")) return "b";
                return "ERROR";

            case 7: case 8:
                if (t1.equals(t3) && !t1.equals("_") && !t1.equals("ERROR")) return "b";
                return "ERROR";

            case 9: case 10: case 11: case 12:
                if (t1.equals("i") && t3.equals("i")) return "b";
                return "ERROR";

            case 14: case 15: case 17: case 18: case 19:
                if (t1.equals("i") && t3.equals("i")) return "i";
                return "ERROR";

            case 21:
                if (t1.equals("b")) return "b";
                return "ERROR";

            default: return "_"; // Para regla 1 (S' -> A) y otras
        }
    }

    private String getTemporalResult(int ruleNumber, String v1, String v2, String v3, int tempCounter) {
         // Este método maneja qué ID/Temporal se apila
         switch (ruleNumber) {
            // Reglas de paso directo (X -> Y, G -> id, G -> TRUE, etc.)
            case 3: case 5: case 6: case 13: case 16: case 20:
            case 23: case 24: case 25:
                return v1; // Pasa el valor del hijo (v1 es el de la derecha)

            // Regla de paréntesis (G -> ( A ))
            case 22:
                return v2; // Pasa el valor de A (v2 es el del medio)

            // Reglas de operaciones binarias (A -> A || B, D -> D + E, etc.)
            // y operaciones unarias (F -> ! F)
            // Estas crean un nuevo temporal
            case 2: case 4:
            case 7: case 8:
            case 9: case 10: case 11: case 12:
            case 14: case 15: case 17: case 18: case 19:
            case 21:
                return "t" + tempCounter; // Retorna el nuevo temporal tN

            // Regla inicial (S' -> A)
            case 1:
                return v1; // Pasa el valor de A

            default:
                return "_";
        }
    }

    // --- INICIO DE NUEVOS MÉTODOS DE GENERACIÓN ASM ---

    /**
     * Genera el código ensamblador para una regla de reducción dada.
     * @param ruleNumber El número de la regla (ej: 14 para D -> D + E)
     * @param result El temporal resultante (ej: "t1")
     * @param opL El operando izquierdo (ej: "id3")
     * @param opR El operando derecho (ej: "id5")
     * @param originalOpSymbol El símbolo del operador (ej: "+")
     */
    private void generateAsmCode(int ruleNumber, String result, String opL, String opR, String originalOpSymbol) {
        // 1. Declarar el temporal en el segmento de datos si es la primera vez que se usa
        if (result.startsWith("t") && !temporalesDeclarados.contains(result)) {
            asmDatos.append("\t" + result + " dw ?\n");
            temporalesDeclarados.add(result);
        }

        // 2. Generar el código de operación
        String comment;
        if (opR != null) { // Operación binaria
            comment = "\t; " + result + " = " + opL + " " + originalOpSymbol + " " + opR + "\n";
        } else { // Operación unaria
            comment = "\t; " + result + " = " + originalOpSymbol + " " + opL + "\n";
        }
        asmCodigo.append(comment);

        switch (ruleNumber) {
            case 14: // D -> D + E
                asmCodigo.append("\tmov ax, [" + opL + "]\n");
                asmCodigo.append("\tadd ax, [" + opR + "]\n");
                asmCodigo.append("\tmov [" + result + "], ax\n\n");
                break;
            case 15: // D -> D - E
                asmCodigo.append("\tmov ax, [" + opL + "]\n");
                asmCodigo.append("\tsub ax, [" + opR + "]\n");
                asmCodigo.append("\tmov [" + result + "], ax\n\n");
                break;
            case 17: // E -> E * F
                // --- MODIFICACIÓN: Cambiado imul por mul ---
                asmCodigo.append("\tmov ax, [" + opL + "]\n");
                asmCodigo.append("\tmul [" + opR + "]\n"); // <-- CAMBIO AQUÍ
                asmCodigo.append("\tmov [" + result + "], ax\n\n");
                break;
            case 18: // E -> E / F
                asmCodigo.append("\tmov ax, [" + opL + "]\n");
                asmCodigo.append("\tcwd ; Extender signo de AX a DX\n");
                asmCodigo.append("\tidiv [" + opR + "]\n");
                asmCodigo.append("\tmov [" + result + "], ax\n\n");
                break;
            case 19: // E -> E % F
                 asmCodigo.append("\tmov ax, [" + opL + "]\n");
                 asmCodigo.append("\tcwd ; Extender signo de AX a DX\n");
                 asmCodigo.append("\tidiv [" + opR + "]\n");
                 asmCodigo.append("\tmov [" + result + "], dx ; El residuo queda en dx\n\n");
                 break;

            // Comparaciones (C -> C op D)
            case 7: // ==
            case 8: // !=
            case 9: // >
            case 10: // <
            case 11: // >=
            case 12: // <=
                String jumpInstruction = getJumpInstruction(ruleNumber);
                String trueLabel = result + "_true";
                String endLabel = result + "_end";

                asmCodigo.append("\tmov ax, [" + opL + "]\n");
                asmCodigo.append("\tcmp ax, [" + opR + "]\n");
                asmCodigo.append("\t" + jumpInstruction + " " + trueLabel + "\n");
                asmCodigo.append("\tmov [" + result + "], 0\n"); // false
                asmCodigo.append("\tjmp " + endLabel + "\n");
                asmCodigo.append(trueLabel + ":\n");
                asmCodigo.append("\tmov [" + result + "], 1\n"); // true
                asmCodigo.append(endLabel + ":\n\n");
                break;

            // Lógica (A -> A || B, B -> B && C)
            case 2: // || (OR)
                asmCodigo.append("\tmov ax, [" + opL + "]\n");
                asmCodigo.append("\tor ax, [" + opR + "]\n");
                asmCodigo.append("\tmov [" + result + "], ax\n\n");
                break;
            case 4: // && (AND)
                asmCodigo.append("\tmov ax, [" + opL + "]\n");
                asmCodigo.append("\tand ax, [" + opR + "]\n");
                asmCodigo.append("\tmov [" + result + "], ax\n\n");
                break;

            // Unaria ! (F -> ! F)
            case 21: // opL es el operando (ej: t3), opR es null
                String falseLabel = result + "_false";
                String endNotLabel = result + "_end";

                asmCodigo.append("\tcmp [" + opL + "], 0\n"); // if (opL != 0)
                asmCodigo.append("\tjne " + falseLabel + "\n"); // Es TRUE (1), saltar para hacerlo 0
                asmCodigo.append("\tmov [" + result + "], 1\n");   // Es FALSE (0), hacerlo 1
                asmCodigo.append("\tjmp " + endNotLabel + "\n");
                asmCodigo.append(falseLabel + ":\n");
                asmCodigo.append("\tmov [" + result + "], 0\n");   // Era TRUE (1), hacerlo 0
                asmCodigo.append(endNotLabel + ":\n\n");
                break;
        }
    }

    /**
     * Obtiene la instrucción de salto de ensamblador para una regla de comparación.
     */
    private String getJumpInstruction(int ruleNumber) {
        switch (ruleNumber) {
            case 7: return "je";  // ==
            case 8: return "jne"; // !=
            case 9: return "jg";  // >
            case 10: return "jl"; // <
            case 11: return "jge"; // >=
            case 12: return "jle"; // <=
            default: return "jmp";
        }
    }

    /**
     * Obtiene el símbolo de operador para una regla.
     */
    private String getOperatorSymbol(int ruleNumber) {
        switch (ruleNumber) {
            case 2: return "||";
            case 4: return "&&";
            case 7: return "==";
            case 8: return "!=";
            case 9: return ">";
            case 10: return "<";
            case 11: return ">=";
            case 12: return "<=";
            case 14: return "+";
            case 15: return "-";
            case 17: return "*";
            case 18: return "/";
            case 19: return "%";
            case 21: return "!";
            default: return "?";
        }
    }
    // --- FIN DE NUEVOS MÉTODOS DE GENERACIÓN ASM ---

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