import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class J2GRegexValidator {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            System.out.println("Seleccione una opción para validar una expresión regular:");
            System.out.println("1. Validar Identificador"); 
            System.out.println("2. Validar Declaración de Entero (INT)"); 
            System.out.println("3. Validar Declaración de Cadena (STR)"); 
            System.out.println("4. Validar Declaración de Booleano (BOOL)");
            System.out.println("5. Validar Condicionales"); 
            System.out.println("6. Validar Estructura if"); 
            System.out.println("7. Validar Estructura if-else"); 
            System.out.println("8. Validar Estructura sw (switch)");
            System.out.println("9. Validar Estructura for");
            System.out.println("10. Validar Estructura while"); 
            System.out.println("11. Validar Estructura do-while"); 
            System.out.println("22. Validar Función Print"); 
            System.out.println("13. Validar Función Input con conversión");
            System.out.println("14. Validar Estructura Main");
            System.out.println("15. Salir"); 

            int choice = 0;
            boolean validChoice = false;

            while (!validChoice) {
                try {
                    System.out.print("Ingrese el número de opción: ");
                    choice = scanner.nextInt();
                    scanner.nextLine(); // Consume newline

                    if (choice >= 1 && choice <= 15) {
                        validChoice = true;
                    } else {
                        System.out.println("Por favor, ingrese un número entero entre 1 y 15.");
                    }
                } catch (InputMismatchException e) {
                    System.out.println("Entrada inválida. Por favor, ingrese un número entero.");
                    scanner.next(); // Consume the invalid input
                }
            }

            if (choice == 15) {
                break;
            }

            while (true) {
                System.out.print("Ingrese la cadena a validar: ");
                String input = scanner.nextLine();

                boolean isValid = false;

                switch (choice) {
                    case 1:  // Identificador ¡LISTA!
                        isValid = validate(input, "^\\s*[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])?$"); //lista
                        break;
                    case 2: // Declaración de entero ¡LISTA!
                        isValid = validate(input, "^\\s*INT\\s\\s*+[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])?(\\s*:\\s*=\\s*-?\\s*?[0-9]+)?\\s*;$"); //lista
                        break;
                    case 3: // Declaración de cadena ¡LISTA!
                        isValid = validate(input, "^\\s*STR\\s\\s*+[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])?(\\s*:\\s*=\\s*\"[^\"]*\")?\\s*;$"); //lista
                        break;
                    case 4: // Declaración de booleano ¡LISTA!
                        isValid = validate(input, "^\\s*BOOL\\s\\s*+[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])?(\\s*:\\s*=\\s*(TRUE|FALSE|true|false))?\\s*;$"); //lista
                        break;
                    case 5: // Condicionales ¡LISTA!
                        isValid = validate(input, "^\\s*([a-zA-Z_][a-zA-Z0-9_]*\\s*:=\\s*)?(([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*)(\\s*(\\&\\&|\\|\\|)\\s*(([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*))*$"); // lista
                        break;
                    case 6: // Estructura if ¡LISTA!
                        isValid = validate(input, "^\\s*if\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*\\s*:=\\s*)?(([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*(\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*(\\s*(\\&\\&|\\|\\|)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*)*)?)?\\s*\\)\\s*\\{\\s*(?:[^{}]|\\{[^{}]*\\})*\\s*\\}\\s*$"); // lista
                        break;
                    case 7: // Estructura if-else ¡LISTA!
                        isValid = validate(input, "^\\s*if\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*\\s*:=\\s*)?(([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*(\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*(\\s*(\\&\\&|\\|\\|)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*)*)?)?\\s*\\)\\s*\\{\\s*(?:[^{}]|\\{[^{}]*\\})*\\s*\\}(\\s*else\\s*\\{\\s*(?:[^{}]|\\{[^{}]*\\})*\\s*\\})?\\s*$"); // lista
                        break;
                    case 8: // Estructura sw (switch) ¡LISTA!
                        isValid = validate(input, "^\\s*sw\\s*\\(\\s*[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])?\\s*\\)\\s*\\{\\s*(caso\\s+(\"[^\"]*\"|-?\\d+)\\s*:\\s*[^{}]*?\\s*detener\\s*;\\s*)+(por_defecto\\s*:\\s*[^{}]*?\\s*detener\\s*;)?\\s*\\}$");
                        break;
                    case 9: // Estructura for ¡LISTA!
                        isValid = validate(input, "^\\s*for\\s*\\(\\s*(INT\\s+([a-z]([a-zA-Z0-9_]*(?:[a-zA-Z0-9]))?)\\s*:\\s*=\\s*-?[0-9]+)\\s*;\\s*([^;(){}]*(?:\\([^;(){}]*\\))*)\\s*;\\s*(([a-z]([a-zA-Z0-9_]*(?:[a-zA-Z0-9]))?)\\s*(\\+=|-=)\\s*[0-9]+)\\s*\\)\\s*\\{(?:[^{}]|\\{[^{}]*\\})*\\}$");
                        break;
                    case 10: // Estructura while ¡LISTA!
                        isValid = validate(input, "^\\s*while\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*\\s*:=\\s*)?(([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*(\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*(\\s*(\\&\\&|\\|\\|)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*)*)?)?\\s*\\)\\s*\\{\\s*(?:[^{}]|\\{[^{}]*\\})*\\s*\\}$");  // lista
                        break;
                    case 11: // Estructura do-while ¡LISTA!
                        isValid = validate(input, "^\\s*do\\s*\\{\\s*(?:[^{}]|\\{[^{}]*\\})*\\s*\\}\\s*while\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*\\s*:=\\s*)?(([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*(\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*(\\s*(\\&\\&|\\|\\|)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*\\s*(==|!=|<=|>=|<|>)\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\))(\\s*[\\+\\-\\*/%]\\s*([a-zA-Z_][a-zA-Z0-9_]*|\\d+|\\(.*?\\)))*)*)?)?\\s*\\)\\s*;$"); // lista
                        break;
                    case 12: // Estructura Print ¡LISTA!
                        isValid = validate(input, "^\\s*Print\\s*\\(\\s*\\\".*?\\\"\\s*\\)\\s*;$"); // lista
                        break;
                    case 13: // Estructura Input ¡LISTA!
                        isValid = validate(input,"^\\s*Input\\s*\\(\\s*(\\\".*?\\\")?\\s*\\)\\s*\\.\\s*(Int|Str|Bool)\\s*\\(\\s*\\)\\s*;$"); // lista
                        break;
                    case 14: // Estrutura Main ¡LISTA!
                        isValid = validate(input,"^\\s*FUNC\\s*J2G\\s*Main\\s*\\(\\s*\\)\\s*\\{.*\\}$"); // lista
                        break;
                    default:
                        System.out.println("Opción no válida. Por favor, intente de nuevo.");
                        continue; 
                }

                System.out.println("Resultado de la validación: " + (isValid ? "Válido" : "Inválido"));
                System.out.println();
                System.out.print("Desea validar otra cadena con la misma expresión regular? (s/n): ");
                String continueChoice = scanner.nextLine().trim().toLowerCase();
                if (!continueChoice.equals("s")) {
                    break;
                }
            }
        }

        scanner.close();
    }

    private static boolean validate(String input, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return matcher.matches();
    }
}