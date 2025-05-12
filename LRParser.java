import java.util.*; // Importa colecciones como List, Map, Stack, Scanner.
import java.util.regex.Matcher; // Para coincidencias de expresiones regulares.
import java.util.regex.Pattern; // Para compilar expresiones regulares.
import java.util.stream.Collectors; // Para operaciones de stream, como joining.
import java.io.BufferedReader; // Para leer texto de un stream de entrada de caracteres de manera eficiente.
import java.io.FileReader; // Para leer archivos de caracteres.
import java.io.IOException; // Para manejar excepciones de E/S (Entrada/Salida).

/**
 * Define la estructura de una regla de producción de la gramática.
 * Contiene el lado izquierdo (no terminal), la cantidad de símbolos en el lado derecho,
 * y la representación original de la regla como cadena para visualización.
 */
class ProductionRule {
    String lhs; // Lado izquierdo de la producción (un no-terminal).
    int rhsLength; // Número de símbolos en el lado derecho de la producción.
    String originalRuleString; // La regla original como texto, ej: "A -> A || B".

    /**
     * Constructor para una regla de producción.
     * @param lhs El no-terminal del lado izquierdo.
     * @param rhsLength La cantidad de símbolos en el lado derecho.
     * @param originalRuleString La representación en cadena de la regla.
     */
    public ProductionRule(String lhs, int rhsLength, String originalRuleString) {
        this.lhs = lhs;
        this.rhsLength = rhsLength;
        this.originalRuleString = originalRuleString;
    }
}

/**
 * Implementa un parser LR (Left-to-Right, Rightmost derivation).
 * Utiliza una tabla de acciones (ACTION) y una tabla GOTO para analizar
 * una secuencia de tokens basada en una gramática definida.
 */
public class LRParser {

    // Tabla ACTION: Mapea (estado, token_terminal) -> acción (desplazar, reducir, aceptar, error).
    // El formato es: Map<EstadoActual, Map<SimboloTerminal, AccionString>>
    private static final Map<Integer, Map<String, String>> actionTable = new HashMap<>();

    // Tabla GOTO: Mapea (estado, no_terminal) -> estado_siguiente.
    // El formato es: Map<EstadoActual, Map<SimboloNoTerminal, EstadoSiguiente>>
    private static final Map<Integer, Map<String, Integer>> gotoTable = new HashMap<>();

    // Almacena las reglas de producción de la gramática, mapeadas por su número.
    // El formato es: Map<NumeroDeRegla, ObjetoProductionRule>
    private static final Map<Integer, ProductionRule> grammarProductions = new HashMap<>();

    /**
     * Carga la tabla de parsing (ACTION y GOTO) desde un archivo de texto especificado.
     * El archivo debe tener líneas con el formato: estado tipo_simbolo simbolo valor
     * donde 'tipo_simbolo' es 'T' para terminal (ACTION) o 'N' para no-terminal (GOTO).
     * @param filePath La ruta al archivo que contiene la tabla de parsing.
     * @throws IOException Si ocurre un error al leer el archivo.
     */
    private static void loadParsingTable(String filePath) throws IOException {
        // Usamos try-with-resources para asegurar que el BufferedReader se cierre automáticamente.
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line; // Variable para almacenar cada línea leída del archivo.
            // Lee el archivo línea por línea.
            while ((line = reader.readLine()) != null) {
                // Elimina espacios en blanco al inicio y final, luego divide la línea por espacios.
                // "\\s+" significa uno o más caracteres de espacio en blanco (espacio, tab, etc.).
                String[] parts = line.trim().split("\\s+");

                // Cada línea válida debe tener 4 partes: estado, tipo, símbolo, valor.
                if (parts.length == 4) {
                    int state = Integer.parseInt(parts[0]); // El estado origen.
                    String type = parts[1]; // Tipo de entrada: 'T' para ACTION, 'N' para GOTO.
                    String symbol = parts[2]; // El símbolo (terminal o no-terminal).
                    String value = parts[3]; // La acción (ej: "s5", "r3") o el estado destino para GOTO.

                    if ("T".equalsIgnoreCase(type)) { // Si es una entrada para la tabla ACTION.
                        // actionTable.computeIfAbsent(state, k -> new HashMap<>())
                        // Obtiene el mapa interno para 'state'. Si no existe, crea uno nuevo.
                        // Luego, .put(symbol, value) añade la entrada (símbolo_terminal, acción).
                        actionTable.computeIfAbsent(state, k -> new HashMap<>()).put(symbol, value);
                    } else if ("N".equalsIgnoreCase(type)) { // Si es una entrada para la tabla GOTO.
                        // Similar a ACTION, pero el valor es un Integer (estado destino).
                        gotoTable.computeIfAbsent(state, k -> new HashMap<>()).put(symbol, Integer.parseInt(value));
                    } else {
                        // Si el tipo no es 'T' ni 'N', se imprime una advertencia.
                        System.err.println("Advertencia: Tipo de símbolo desconocido en " + filePath + ": " + line);
                    }
                } else if (!line.trim().isEmpty()){ // Si la línea no está vacía pero no tiene 4 partes.
                    System.err.println("Advertencia: Línea malformada en " + filePath + ": " + line);
                }
            }
        } catch (NumberFormatException e) { // Si ocurre un error al convertir un número (ej: estado).
            System.err.println("Error de formato numérico al leer " + filePath + ": " + e.getMessage());
            throw e; // Relanza la excepción para indicar un fallo crítico.
        } catch (IOException e) { // Si ocurre un error general de E/S.
            System.err.println("Error de E/S al leer " + filePath + ": " + e.getMessage());
            throw e; // Relanza la excepción.
        }
    }

    // Bloque estático: Se ejecuta una vez cuando la clase es cargada por la JVM.
    // Se utiliza para inicializar miembros estáticos, como las producciones y cargar las tablas.
    static {
        // Inicialización de las reglas de producción de la gramática.
        // Cada regla se asocia con un número, que corresponde a las acciones de reducción (ej: "rX").
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
        grammarProductions.put(21, new ProductionRule("F", 2, "F -> ! F")); // Lado derecho tiene 2 símbolos: "!" y "F"
        grammarProductions.put(22, new ProductionRule("G", 3, "G -> ( A )"));
        grammarProductions.put(23, new ProductionRule("G", 1, "G -> id"));
        grammarProductions.put(24, new ProductionRule("G", 1, "G -> TRUE"));
        grammarProductions.put(25, new ProductionRule("G", 1, "G -> FALSE"));

        try {
            // Carga de la matriz "matriz_parsing.txt".
            loadParsingTable("matriz_parsing.txt");
        } catch (IOException e) {
            System.err.println("Error fatal al cargar la tabla de parsing desde el archivo: " + e.getMessage());
            throw new RuntimeException("No se pudo cargar la tabla de parsing.", e);
        }
    }

    /**
     * Tokeniza la cadena de entrada en una lista de tokens.
     * @param input recibe la cadena de expresión a tokenizar.
     * @return devuelve una lista de tokens (cadenas).
     */
    public List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        // Expresión regular para capturar operadores (los más largos primero para evitar coincidencias parciales),
        // identificadores (id, TRUE, FALSE como palabras completas), y paréntesis/operadores de un solo carácter.
        // "\\|\\|" es "||", "&&" es "&&", etc.
        // "\\b(?:id|TRUE|FALSE)\\b" busca "id", "TRUE", o "FALSE" como palabras completas (\\b es límite de palabra).
        // "[()!+\\-*/%<>]" captura operadores de un solo carácter y paréntesis.
        Pattern pattern = Pattern.compile("\\|\\||&&|==|!=|>=|<=|\\b(?:id|TRUE|FALSE)\\b|[()!+\\-*/%<>]");
        // Elimina todos los espacios en blanco de la entrada antes de tokenizar.
        Matcher matcher = pattern.matcher(input.replaceAll("\\s+", ""));
        // Encuentra todas las coincidencias de la expresión regular.
        while (matcher.find()) {
            tokens.add(matcher.group()); // Añade el token encontrado a la lista.
        }
        tokens.add("$"); // Añade el marcador de fin de entrada.
        return tokens;
    }

    /**
     * Realiza el análisis sintáctico de la expresión de entrada.
     * Sigue el algoritmo de parsing LR utilizando la pila interna,
     * la tabla ACTION y la tabla GOTO.
     * @param inputExpression La expresión a analizar.
     */
    public void parse(String inputExpression) {
        // Convierte la expresión de entrada en una lista de tokens.
        List<String> tokens = tokenize(inputExpression);
        // Si la entrada está vacía (solo contiene "$"), no hay nada que analizar.
        if (tokens.size() == 1 && tokens.get(0).equals("$")) {
            System.out.println("Entrada vacía.");
            return;
        }

        // Pila interna del parser. Almacena alternadamente estados (Integer) y símbolos (String).
        // Ej: 0 "id" 5 "+" 10 ...
        Stack<Object> internalStack = new Stack<>();
        internalStack.push(0); // Empuja el estado inicial (0) a la pila.

        int inputPtr = 0; // Puntero al token actual en la lista de tokens de entrada.

        // Imprime la cabecera para la traza del análisis.
        System.out.printf("%-30s | %-30s | %-15s\n", "PILA", "ENTRADA", "SALIDA");
        System.out.println(String.join("", Collections.nCopies(80, "-"))); // Línea separadora.

        // Bucle principal del parser. Continúa hasta una acción de 'aceptar' o un error.
        while (true) {
            int currentState = -1; // El estado actual del parser, tomado de la cima de la pila.
            // Busca el último Integer en la pila, que representa el estado actual.
            // Se recorre la pila desde la cima hacia abajo.
            for (int i = internalStack.size() - 1; i >= 0; i--) {
                if (internalStack.get(i) instanceof Integer) {
                    currentState = (Integer) internalStack.get(i);
                    break;
                }
            }
            // Si no se encuentra un estado en la pila (no debería ocurrir con una inicialización correcta).
            if(currentState == -1){ 
                System.out.println("Error: Pila de estados interna inválida.");
                return;
            }

            String currentToken = tokens.get(inputPtr); // El token actual de la entrada.

            // Construye representaciones en cadena de la pila y la entrada restante para la traza.
            String stackStr = internalStack.stream()
                                          .filter(item -> item instanceof Integer) // Muestra solo los estados en la pila.
                                          .map(Object::toString)
                                          .collect(Collectors.joining(" "));
            String inputStr = tokens.subList(inputPtr, tokens.size()).stream().collect(Collectors.joining(" "));
            
            // Consulta la tabla ACTION para el estado actual y el token de entrada actual.
            Map<String, String> stateActions = actionTable.get(currentState);
            // Si no hay acciones definidas para el estado actual (puede ser un estado solo con GOTOs).
            if (stateActions == null) {
                 System.out.printf("%-30s | %-30s | Error: Estado %d no encontrado en la tabla de acciones (o no tiene acciones para terminales).\n", stackStr, inputStr, currentState);
                return;
            }
            String action = stateActions.get(currentToken); // Obtiene la acción específica.

            // Si no hay acción definida para el par (estado, token_actual).
            if (action == null) {
                System.out.printf("%-30s | %-30s | Error: No hay acción para el estado %d y el token '%s'.\n", stackStr, inputStr, currentState, currentToken);
                // Muestra los tokens que sí tendrían una acción desde este estado.
                System.out.print("Tokens esperados (ACTION) desde el estado " + currentState + ": ");
                List<String> expectedTokens = new ArrayList<>(stateActions.keySet());
                System.out.println(String.join(", ", expectedTokens));
                return;
            }
            
            // Imprime la traza del paso actual del parser.
            System.out.printf("%-30s | %-30s | %-15s\n", stackStr, inputStr, action);

            // Procesa la acción obtenida.
            if (action.startsWith("s")) { // Acción de Desplazamiento (Shift). Ej: "s5"
                int nextState = Integer.parseInt(action.substring(1)); // Obtiene el número del estado a desplazar.
                internalStack.push(currentToken); // Empuja el token actual a la pila.
                internalStack.push(nextState);    // Empuja el nuevo estado a la pila.
                inputPtr++; // Avanza el puntero de entrada al siguiente token.
            } else if (action.startsWith("r")) { // Acción de Reducción. Ej: "r3"
                int ruleNumber = Integer.parseInt(action.substring(1)); // Obtiene el número de la regla a reducir.
                ProductionRule rule = grammarProductions.get(ruleNumber); // Obtiene la regla de producción.
                if (rule == null) {
                    System.out.println("Error: Regla de producción desconocida " + ruleNumber);
                    return;
                }

                // Saca de la pila 2*N elementos, donde N es el número de símbolos en el lado derecho de la regla.
                // Por cada símbolo en RHS, se saca el símbolo y su estado asociado.
                for (int i = 0; i < rule.rhsLength * 2; i++) { 
                    if(!internalStack.isEmpty()) internalStack.pop();
                    else { // Si la pila se vacía inesperadamente.
                         System.out.println("Error: Pila interna vacía inesperadamente durante la reducción.");
                         return;
                    }
                }
                
                // Después de sacar los elementos, el estado en la cima de la pila es el estado "descubierto".
                int stateBeforeReduce = -1;
                 for(int i = internalStack.size() -1; i >=0; i--){
                    if(internalStack.get(i) instanceof Integer){
                        stateBeforeReduce = (Integer) internalStack.get(i);
                        break;
                    }
                }
                if(stateBeforeReduce == -1){ // Si no se encuentra un estado válido.
                     System.out.println("Error: Pila de estados interna inválida después de pop en reducción.");
                    return;
                }

                // Consulta la tabla GOTO con el estado descubierto y el no-terminal del lado izquierdo de la regla.
                Map<String, Integer> stateGotos = gotoTable.get(stateBeforeReduce);
                if (stateGotos == null || !stateGotos.containsKey(rule.lhs)) {
                     System.out.printf("Error: No hay GOTO para el estado %d y el no terminal '%s'.\n", stateBeforeReduce, rule.lhs);
                     // Muestra los no-terminales esperados para GOTO desde ese estado.
                     System.out.print("No-terminales esperados (GOTO) desde el estado " + stateBeforeReduce + ": ");
                     if (stateGotos != null) {
                        System.out.println(String.join(", ", stateGotos.keySet()));
                     } else {
                        System.out.println("Ninguno definido.");
                     }
                     return;
                }
                int nextState = stateGotos.get(rule.lhs); // Obtiene el estado al que se transita.
                internalStack.push(rule.lhs); // Empuja el no-terminal (LHS de la regla) a la pila.
                internalStack.push(nextState);  // Empuja el nuevo estado (resultado del GOTO) a la pila.
            } else if (action.equals("acc")) { // Acción de Aceptar.
                System.out.println("Entrada aceptada.");
                break; // Termina el bucle de parsing.
            } else { // Acción desconocida (no debería ocurrir si la tabla está bien formada).
                System.out.println("Error: Acción desconocida en la tabla: " + action);
                return;
            }
        }
    }

    /**
     * Método principal para ejecutar el parser.
     * Pide al usuario una expresión, la tokeniza y la analiza.
     * @param args Argumentos de la línea de comandos (no se usan).
     */
    public static void main(String[] args) {
        LRParser parser = new LRParser(); // Crea una instancia del parser.
        Scanner scanner = new Scanner(System.in); // Para leer la entrada del usuario.

        System.out.println("Introduce la operación (ej: ( id + id ) * id ):");
        String input = scanner.nextLine(); // Lee la línea de entrada.
        
        // Si la entrada está vacía, usa una expresión de ejemplo.
        if (input.trim().isEmpty()){
            System.out.println("Entrada vacía, usando ejemplo: ( id + id * id ) / id < id || id + id * id == id");
            input = "( id + id * id ) / id < id || id + id * id == id";
        }

        parser.parse(input); // Llama al método de parsing.
        scanner.close(); // Cierra el scanner.
    }
}