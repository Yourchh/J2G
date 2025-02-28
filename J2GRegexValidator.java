import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class J2GRegexValidator {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            System.out.println("Seleccione una opción para validar una expresión regular:");
            System.out.println("1. Validar Identificador"); // listo
            System.out.println("2. Validar Entero (INT)"); // listo
            System.out.println("3. Validar Declaración de Entero (INT)"); // listo
            System.out.println("4. Validar Decimal (FLT)"); // listo
            System.out.println("5. Validar Declaración de Decimal (FLT)"); // listo
            System.out.println("6. Validar Cadena (STR)"); // listo
            System.out.println("7. Validar Declaración de Cadena (STR)"); // listo
            System.out.println("8. Validar Booleano (BOOL)"); // listo
            System.out.println("9. Validar Declaración de Booleano (BOOL)"); // listo
            System.out.println("10. Validar Operador Aritmético"); // listo
            System.out.println("11. Validar Operador de Asignación"); // listo
            System.out.println("12. Validar Operador Relacional"); // listo
            System.out.println("13. Validar Operador Lógico"); // listo
            System.out.println("14. Validar Estructura if"); // listo
            System.out.println("15. Validar Estructura if-else"); // listo
            System.out.println("16. Validar Estructura sw (switch)");
            System.out.println("17. Validar Estructura for"); // listo
            System.out.println("18. Validar Estructura while"); // listo
            System.out.println("19. Validar Estructura do-while"); // listo
            System.out.println("20. Validar Condición Lógica"); // listo
            System.out.println("21. Validar Función Print"); // listo1
            
            System.out.println("22. Validar Función Input"); // listo
            System.out.println("23. Validar Función Input con conversión"); // listo
            System.out.println("24. Salir"); // listo

            int choice = 0;
            boolean validChoice = false;

            while (!validChoice) {
                try {
                    System.out.print("Ingrese el número de opción: ");
                    choice = scanner.nextInt();
                    scanner.nextLine(); // Consume newline

                    if (choice >= 1 && choice <= 24) {
                        validChoice = true;
                    } else {
                        System.out.println("Por favor, ingrese un número entero entre 1 y 24.");
                    }
                } catch (InputMismatchException e) {
                    System.out.println("Entrada inválida. Por favor, ingrese un número entero.");
                    scanner.next(); // Consume the invalid input
                }
            }

            if (choice == 24) {
                break;
            }

            while (true) {
                System.out.print("Ingrese la cadena a validar: ");
                String input = scanner.nextLine();

                boolean isValid = false;

                switch (choice) {
                    case 1:
                        isValid = validate(input, "^[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])n?$"); // Identificador
                        break;
                    case 2:
                        isValid = validate(input, "^-?[0-9]+$"); // Entero
                        break;
                    case 3:
                        isValid = validate(input, "^INT\\s+[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])?(\\s*:=\\s*-?[0-9]+)?;$"); // Declaración
                                                                                                                      // de
                                                                                                                      // entero
                        break;
                    case 4:
                        isValid = validate(input, "^-?[0-9]+(\\.[0-9]+)?$"); // Decimal
                        break;
                    case 5:
                        isValid = validate(input,
                                "^FLT\\s+[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])?(\\s*:=\\s*-?[0-9]+(\\.[0-9]+)?)?;$"); // Declaración
                                                                                                                // Decimal
                        break;
                    case 6:
                        isValid = validate(input, "^\"[^\"]*\"$"); // Cadena
                        break;
                    case 7:
                        isValid = validate(input, "^STR\\s+[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])?(\\s*:=\\s*\"[^\"]*\")?;$"); // Declaración
                                                                                                                        // de
                                                                                                                        // Cadena
                        break;
                    case 8:
                        isValid = validate(input, "^(TRUE|FALSE|true|false)$"); // Booleano
                        break;
                    case 9:
                        isValid = validate(input,
                                "^BOOL\\s+[a-z]([a-zA-Z0-9_]*[a-zA-Z0-9])?(\\s*:=\\s*(TRUE|FALSE|true|false))?;$"); // Declaración
                                                                                                                    // de
                                                                                                                    // Booleano
                        break;
                    case 10:
                        isValid = validate(input, "[+\\-*/%]"); // Operador Aritmético
                        break;
                    case 11:
                        isValid = validate(input, ":=|[+\\-*/]="); // Operador de Asignación
                        break;
                    case 12:
                        isValid = validate(input, "==|!=|>=|<=|[><]"); // Operador Relacional
                        break;
                    case 13:
                        isValid = validate(input, "&&|\\|\\||!"); // Operador Lógico
                        break;
                    case 14:
                        isValid = validate(input,
                                "^if\\s*\\(\\s*(?:(?:!?\\s*\\(*\\s*[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)(?:\\s*[\\+\\-\\*\\/%<>=!&|]*\\s*\\(*\\s*(?:[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)\\s*\\)*)*(?:\\s*(?:&&|\\|\\|)\\s*(?:!?\\s*\\(*\\s*[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)(?:\\s*[\\+\\-\\*\\/%<>=!&|]*\\s*\\(*\\s*(?:[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)\\s*\\)*)*)*)\\s*\\)\\s*\\{\\s*(?:[^{}]|\\{[^{}]*\\})*\\s*\\}$"); // Estructura
                        // if
                        break;
                    case 15:
                        isValid = validate(input,
                                "^if\\s*\\(\\s*(?:(?:!?\\s*\\(*\\s*[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)(?:\\s*[\\+\\-\\*\\/%<>=!&|]*\\s*\\(*\\s*(?:[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)\\s*\\)*)*(?:\\s*(?:&&|\\|\\|)\\s*(?:!?\\s*\\(*\\s*[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)(?:\\s*[\\+\\-\\*\\/%<>=!&|]*\\s*\\(*\\s*(?:[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)\\s*\\)*)*)*)\\s*\\)\\s*\\{\\s*(?:[^{}]|\\{[^{}]*\\})*\\s*\\}\\s*else\\s*\\{(?:[^{}]|\\{[^{}]*\\})*\\}$"); // Estructura
                        // if-else
                        break;
                    case 16:
                        isValid = validate(input,
                                "^sw\\s*\\(\\s*([a-z][a-zA-Z0-9]*)\\s*\\)\\s*\\{\\s*(?:caso\\s+\\d+\\s*:\\s*[^{}]*?detener;\\s*)+(?:por_defecto\\s*:\\s*[^{}]*?detener;)?\\s*\\}$"); // Estructura
                        // sw
                        // (switch)
                        break;
                    case 17:
                        isValid = validate(input,
                                "^for\\s*\\(\\s*(INT\\s+([a-z]([a-zA-Z0-9_]*(?:[a-zA-Z0-9]))?)\\s*:=\\s*-?[0-9]+)\\s*;\\s*([^;(){}]*(?:\\([^;(){}]*\\))*)\\s*;\\s*(([a-z]([a-zA-Z0-9_]*(?:[a-zA-Z0-9]))?)\\s*(\\+=|-=)\\s*[0-9]+)\\s*\\)\\s*\\{(?:[^{}]|\\{[^{}]*\\})*\\}$"); // Estructura
                        // for
                        break;
                    case 18:
                        isValid = validate(input,
                                "^while\\s*\\(\\s*(?:(?:!?\\s*\\(*\\s*[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)(?:\\s*[\\+\\-\\*\\/%<>=!&|]*\\s*\\(*\\s*(?:[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)\\s*\\)*)*(?:\\s*(?:&&|\\|\\|)\\s*(?:!?\\s*\\(*\\s*[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)(?:\\s*[\\+\\-\\*\\/%<>=!&|]*\\s*\\(*\\s*(?:[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)\\s*\\)*)*)*)\\s*\\)\\s*\\{\\s*(?:[^{}]|\\{[^{}]*\\})*\\s*\\}$"); // Estructura
                        // while
                        break;
                    case 19:
                        isValid = validate(input,
                                "^do\\s*\\{(?:[^{}]|\\{[^{}]*\\})*\\}\\s*while\\s*\\(\\s*(?:(?:!?\\s*\\(*\\s*[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)(?:\\s*[\\+\\-\\*\\/%<>=!&|]*\\s*\\(*\\s*(?:[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)\\s*\\)*)*(?:\\s*(?:&&|\\|\\|)\\s*(?:!?\\s*\\(*\\s*[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)(?:\\s*[\\+\\-\\*\\/%<>=!&|]*\\s*\\(*\\s*(?:[a-zA-Z_][a-zA-Z0-9_]*|-?\\d+(\\.\\d+)?)\\s*\\)*)*)*)\\s*\\)\\s*\\;$"); // Estructura
                        // do-while
                        break;
                    case 20:
                        isValid = validate(input,
                                "^(?:(?:(?:[a-zA-Z][a-zA-Z0-9_]*|[0-9]+(?:\\.[0-9]+)?|\"[^\"]*\"|TRUE|FALSE)\\s*(?:==|!=|>=|<=|>|<)\\s*(?:[a-zA-Z][a-zA-Z0-9_]*|[0-9]+(?:\\.[0-9]+)?|\"[^\"]*\"|TRUE|FALSE))|\\((?:[^()]*)\\)|\\!(?:[a-zA-Z][a-zA-Z0-9_]*|TRUE|FALSE)|(?:TRUE|FALSE))(?:\\s*(?:&&|\\|\\|)\\s*(?:[^()]*|\\((?:[^()]*)\\)))*$"); // Condición
                        // Lógica
                        break;
                    case 21:
                        isValid = validate(input, "Print\\(([\\w\\s,.:\"]*)\\)\\s*;"); // Función Print
                        break;
                    case 22:
                        isValid = validate(input, "Input\\(\\s*\\\"?\\s*.*?\\s*\\\"?\\s*\\)\\s*;"); // Función Input
                        break;
                    case 23:
                        isValid = validate(input,
                                "Input\\(\\s*\\\"?\\s*.*?\\s*\\\"?\\s*\\).(Int|Flt|Str|Bool)\\(\\)\\s*;"); // Función
                                                                                                           // Input
                                                                                                           // con
                                                                                                           // conversión
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