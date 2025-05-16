package AnalizadorLexicoJ2G;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        prConocidas.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));

        StringBuilder regexBuilder = new StringBuilder();
        regexBuilder.append("(\"(?:\\\\.|[^\"\\\\])*\")"); // String literals

        for (String token : prConocidas) {
            regexBuilder.append("|(").append(Pattern.quote(token)).append(")");
        }
        
        regexBuilder.append("|([a-zA-Z_][a-zA-Z0-9_]*)"); // Identifiers
        regexBuilder.append("|([0-9]+)"); // Numbers

        Pattern tokenPattern = Pattern.compile(regexBuilder.toString());
        Matcher matcher = tokenPattern.matcher(codigoSinComentarios);
        
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) { 
                if (matcher.group(i) != null) {
                    tokens.add(matcher.group(i));
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
            String token = tokensOriginales.get(i);
            String tokenToEmit = token;

            if (token.equals("INT") || token.equals("STR") || token.equals("BOOL")) {
                lastDeclaredType = token.toLowerCase();
            } else if (token.equals(";")) {
                lastDeclaredType = null; 
            }

            if (token.matches("\"(?:\\\\.|[^\"\\\\])*\"")) { // String literal
                String id;
                String stringContent = token.substring(1, token.length() - 1).replace("\\\"", "\""); 
                if (this.tablaSimbolos.contieneLiteral(token)) { 
                    id = this.tablaSimbolos.obtenerIdParaLiteral(token);
                } else {
                    id = this.tablaSimbolos.generarProximoId();
                    this.tablaSimbolos.agregarLiteralConId(token, id); 
                    this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(token, id, "string", stringContent));
                }
                tokenToEmit = id;
            }
            else if (this.tablaSimbolos.getPalabrasReservadasYSimbolosConocidos().contains(token)) {
                // Keyword or known symbol, do not transform
            } else if (token.matches("[0-9]+")) { // Number literal
                String id;
                if (this.tablaSimbolos.contieneLiteral(token)) {
                    id = this.tablaSimbolos.obtenerIdParaLiteral(token);
                } else {
                    id = this.tablaSimbolos.generarProximoId();
                    this.tablaSimbolos.agregarLiteralConId(token, id);
                    this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(token, id, "int", token));
                }
                tokenToEmit = id;
            } else if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*")) { // Identifier
                String id;
                if (this.tablaSimbolos.contieneVariable(token)) {
                    id = this.tablaSimbolos.obtenerIdParaVariable(token);
                } else {
                    id = this.tablaSimbolos.generarProximoId();
                    this.tablaSimbolos.agregarVariableConId(token, id);
                    String tipoVar = (lastDeclaredType != null) ? lastDeclaredType : "desconocido";
                    String valorParaTabla = token; 
                    if ( (i + 2 < tokensOriginales.size()) && tokensOriginales.get(i+1).equals(":=") ) {
                        String valorAsignadoToken = tokensOriginales.get(i+2);
                        if (this.tablaSimbolos.contieneVariable(valorAsignadoToken)) { 
                            valorParaTabla = this.tablaSimbolos.obtenerIdParaVariable(valorAsignadoToken);
                        } else if (this.tablaSimbolos.contieneLiteral(valorAsignadoToken)) { 
                            valorParaTabla = this.tablaSimbolos.obtenerIdParaLiteral(valorAsignadoToken);
                        } else {
                             valorParaTabla = valorAsignadoToken; 
                        }
                    }
                    this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(token, id, tipoVar, valorParaTabla));
                }
                tokenToEmit = id;
            }
            transformedTokens.add(tokenToEmit);
        }
        
        // La actualización de valores de variables ahora se hace en TablaSimbolos
        this.tablaSimbolos.actualizarValoresDeVariablesPostAnalisis();
        return transformedTokens;
    }
}
