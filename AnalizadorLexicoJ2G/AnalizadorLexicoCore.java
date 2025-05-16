package AnalizadorLexicoJ2G;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map; // Necesario para SymbolTableEntry
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalizadorLexicoCore {

    private TablaSimbolos tablaSimbolos;

    public AnalizadorLexicoCore(TablaSimbolos tablaSimbolos) {
        this.tablaSimbolos = tablaSimbolos;
    }

    public List<String> fase1_limpiarYTokenizar(String codigo) {
        String codigoSinComentarios = codigo.replaceAll("/\\*[\\s\\S]*?\\*/", "").replaceAll("//.*", "");
        
        List<String> prConocidas = new ArrayList<>(this.tablaSimbolos.getPalabrasReservadasYSimbolosConocidos());
        // Ordenar para que los tokens más largos se intenten primero (ej. := antes de =)
        prConocidas.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));

        StringBuilder regexBuilder = new StringBuilder();
        // 1. Literales de cadena
        regexBuilder.append("(\"(?:\\\\.|[^\"\\\\])*\")"); 

        // 2. Palabras reservadas, símbolos de tabsim.txt
        for (String token : prConocidas) {
            regexBuilder.append("|(").append(Pattern.quote(token)).append(")");
        }
        
        // 3. Identificadores generales (variables de usuario)
        regexBuilder.append("|([a-zA-Z_][a-zA-Z0-9_]*)"); 
        // 4. Números
        regexBuilder.append("|([0-9]+)"); 
        // 5. Cualquier otro símbolo de un solo carácter (para operadores no listados explícitamente)
        // Esto es opcional y puede hacer que el tokenizador sea más permisivo o más ruidoso.
        // Por ahora, lo omitimos para ser más estrictos con los tokens definidos.
        // regexBuilder.append("|(.)"); 

        Pattern tokenPattern = Pattern.compile(regexBuilder.toString());
        Matcher matcher = tokenPattern.matcher(codigoSinComentarios);
        
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) { 
                if (matcher.group(i) != null) {
                    tokens.add(matcher.group(i).trim()); // trim() para eliminar espacios si la regex los captura accidentalmente
                    break; 
                }
            }
        }
        return tokens;
    }

    public List<String> fase2_transformarTokens(List<String> tokensOriginales) {
        List<String> transformedTokens = new ArrayList<>();
        String lastDeclaredType = null; 

        for (int i = 0; i < tokensOriginales.size(); i++) {
            String currentOriginalToken = tokensOriginales.get(i);
            String tokenToEmit = currentOriginalToken; // Por defecto, emitir el token original

            // 1. Actualizar lastDeclaredType para la inferencia de tipos de NUEVAS variables
            // Esto debe basarse en el TOKEN ORIGINAL antes de cualquier transformación a ID.
            if (currentOriginalToken.equals("INT") || currentOriginalToken.equals("STR") || currentOriginalToken.equals("BOOL")) {
                lastDeclaredType = currentOriginalToken.toLowerCase();
            } else if (currentOriginalToken.equals(";")) {
                lastDeclaredType = null; 
            }

            // 2. Intentar la transformación del token
            if (currentOriginalToken.matches("\"(?:\\\\.|[^\"\\\\])*\"")) { // Literal de cadena
                String id;
                // El contenido del string para la tabla de símbolos no debe incluir las comillas externas
                String stringContent = currentOriginalToken.substring(1, currentOriginalToken.length() - 1).replace("\\\"", "\""); 
                if (this.tablaSimbolos.contieneLiteral(currentOriginalToken)) { 
                    id = this.tablaSimbolos.obtenerIdParaLiteral(currentOriginalToken);
                } else {
                    id = this.tablaSimbolos.generarProximoId();
                    this.tablaSimbolos.agregarLiteralConId(currentOriginalToken, id); 
                    // Guardar el literal original como 'variable' y su contenido como 'valor'
                    this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(currentOriginalToken, id, "string", stringContent));
                }
                tokenToEmit = id;
            } else if (currentOriginalToken.matches("[0-9]+")) { // Literal numérico
                String id;
                if (this.tablaSimbolos.contieneLiteral(currentOriginalToken)) {
                    id = this.tablaSimbolos.obtenerIdParaLiteral(currentOriginalToken);
                } else {
                    id = this.tablaSimbolos.generarProximoId();
                    this.tablaSimbolos.agregarLiteralConId(currentOriginalToken, id);
                    // Guardar el número original como 'variable' y también como 'valor'
                    this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(currentOriginalToken, id, "int", currentOriginalToken));
                }
                tokenToEmit = id;
            } else {
                // No es un literal. Podría ser una variable, palabra reservada u operador de tabsim.txt,
                // o una nueva variable de usuario.
                Map<String, SymbolTableEntry> baseTabsim = this.tablaSimbolos.getTabsimBase();
                if (baseTabsim.containsKey(currentOriginalToken)) {
                    // El token existe en el tabsim.txt base (puede ser variable predefinida, PR, operador)
                    tokenToEmit = baseTabsim.get(currentOriginalToken).id;
                } else {
                    // El token NO está en tabsim.txt base.
                    // Verificar si es un identificador válido (potencial nueva variable de usuario).
                    if (currentOriginalToken.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        String id;
                        if (this.tablaSimbolos.contieneVariable(currentOriginalToken)) { // Ya vista esta nueva variable
                            id = this.tablaSimbolos.obtenerIdParaVariable(currentOriginalToken);
                        } else { // Nueva variable de usuario, no definida en tabsim.txt
                            id = this.tablaSimbolos.generarProximoId();
                            this.tablaSimbolos.agregarVariableConId(currentOriginalToken, id);
                            String tipoVar = (lastDeclaredType != null) ? lastDeclaredType : "desconocido";
                            
                            String valorParaTabla = currentOriginalToken; // Por defecto, el nombre del token
                            // Si hay una asignación inmediata, capturar el token del RHS para 'valor'
                            // La actualización posterior convertirá este token RHS a su ID si es posible.
                            if ((i + 1 < tokensOriginales.size()) && tokensOriginales.get(i + 1).equals(":=") && (i + 2 < tokensOriginales.size())) {
                                valorParaTabla = tokensOriginales.get(i + 2);
                            }
                            this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(currentOriginalToken, id, tipoVar, valorParaTabla));
                        }
                        tokenToEmit = id;
                    }
                    // Si no está en tabsim.txt y no es un nuevo identificador válido,
                    // se emitirá el token original (tokenToEmit no cambió).
                    // Esto podría ser un operador como '+' que no estaba en tabsim.txt.
                }
            }
            transformedTokens.add(tokenToEmit);
        }
        
        // Actualizar el campo 'valor' en nuevasVariablesDetectadas para que referencie IDs si es posible
        this.tablaSimbolos.actualizarValoresDeVariablesPostAnalisis();
        return transformedTokens;
    }
}
