package AnalizadorLexicoJ2G;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalizadorLexicoCore {

    public TablaSimbolos tablaSimbolos;

    public AnalizadorLexicoCore(TablaSimbolos tablaSimbolos) {
        this.tablaSimbolos = tablaSimbolos;
    }

    public List<String> fase1_limpiarYTokenizar(String codigo) {
        String codigoSinComentarios = codigo.replaceAll("/\\*[\\s\\S]*?\\*/", "").replaceAll("//.*", "");
        
        List<String> prConocidas = new ArrayList<>(this.tablaSimbolos.getPalabrasReservadasYSimbolosConocidos());
        prConocidas.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));

        StringBuilder regexBuilder = new StringBuilder();
        regexBuilder.append("(\"(?:\\\\.|[^\"\\\\])*\")"); 
        
        for (String token : prConocidas) {
            regexBuilder.append("|(").append(Pattern.quote(token)).append(")");
        }
        
        regexBuilder.append("|([a-zA-Z_][a-zA-Z0-9_]*)"); 
        regexBuilder.append("|([0-9]+)"); 
        
        Pattern tokenPattern = Pattern.compile(regexBuilder.toString());
        Matcher matcher = tokenPattern.matcher(codigoSinComentarios);
        
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) { 
                if (matcher.group(i) != null) {
                    tokens.add(matcher.group(i).trim());
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
            String tokenToEmit = currentOriginalToken;
            
            if (currentOriginalToken.equals("Input") && i + 6 < tokensOriginales.size() && 
                tokensOriginales.get(i+1).equals("(") && tokensOriginales.get(i+2).equals(")") && 
                tokensOriginales.get(i+3).equals(".") && 
                (tokensOriginales.get(i+4).equals("Int") || tokensOriginales.get(i+4).equals("Str") || tokensOriginales.get(i+4).equals("Bool")) &&
                tokensOriginales.get(i+5).equals("(") && tokensOriginales.get(i+6).equals(")")) {
                
                String inputCall = tokensOriginales.get(i) + tokensOriginales.get(i+1) + tokensOriginales.get(i+2) + tokensOriginales.get(i+3) + tokensOriginales.get(i+4) + tokensOriginales.get(i+5) + tokensOriginales.get(i+6);
                String id;
                String inputType = "desconocido";
                
                if (tokensOriginales.get(i+4).equals("Int")) {
                    inputType = "int";
                } else if (tokensOriginales.get(i+4).equals("Str")) {
                    inputType = "string";
                } else if (tokensOriginales.get(i+4).equals("Bool")) {
                    inputType = "bool";
                }
                
                if (this.tablaSimbolos.contieneLiteral(inputCall)) {
                    id = this.tablaSimbolos.obtenerIdParaLiteral(inputCall);
                } else {
                    id = this.tablaSimbolos.generarProximoId();
                    this.tablaSimbolos.agregarLiteralConId(inputCall, id);
                    this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(inputCall, id, inputType, inputCall));
                }
                tokenToEmit = id;
                i += 6; 
            } else if (currentOriginalToken.matches("\"(?:\\\\.|[^\"\\\\])*\"")) {
                String id;
                String stringContent = currentOriginalToken.substring(1, currentOriginalToken.length() - 1).replace("\\\"", "\""); 
                if (this.tablaSimbolos.contieneLiteral(currentOriginalToken)) { 
                    id = this.tablaSimbolos.obtenerIdParaLiteral(currentOriginalToken);
                } else {
                    id = this.tablaSimbolos.generarProximoId();
                    this.tablaSimbolos.agregarLiteralConId(currentOriginalToken, id); 
                    this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(currentOriginalToken, id, "string", stringContent));
                }
                tokenToEmit = id;
            } else if (currentOriginalToken.matches("[0-9]+")) {
                String id;
                if (this.tablaSimbolos.contieneLiteral(currentOriginalToken)) {
                    id = this.tablaSimbolos.obtenerIdParaLiteral(currentOriginalToken);
                } else {
                    id = this.tablaSimbolos.generarProximoId();
                    this.tablaSimbolos.agregarLiteralConId(currentOriginalToken, id);
                    this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(currentOriginalToken, id, "int", currentOriginalToken));
                }
                tokenToEmit = id;
            } else {
                Map<String, SymbolTableEntry> baseTabsim = this.tablaSimbolos.getTabsimBase();
                if (baseTabsim.containsKey(currentOriginalToken)) {
                    tokenToEmit = baseTabsim.get(currentOriginalToken).id;
                    if (tokenToEmit.equals("INT") || tokenToEmit.equals("STR") || tokenToEmit.equals("BOOL")) {
                        lastDeclaredType = tokenToEmit.toLowerCase();
                    }
                } else {
                    if (currentOriginalToken.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        String id;
                        if (this.tablaSimbolos.contieneVariable(currentOriginalToken)) {
                            id = this.tablaSimbolos.obtenerIdParaVariable(currentOriginalToken);
                        } else {
                            id = this.tablaSimbolos.generarProximoId();
                            this.tablaSimbolos.agregarVariableConId(currentOriginalToken, id);
                            
                            String tipoVar = (lastDeclaredType != null) ? lastDeclaredType : "desconocido";
                            
                            String valorParaTabla = currentOriginalToken;
                            if ((i + 1 < tokensOriginales.size()) && tokensOriginales.get(i + 1).equals(":=") && (i + 2 < tokensOriginales.size())) {
                                valorParaTabla = tokensOriginales.get(i + 2);
                            }
                            this.tablaSimbolos.agregarNuevaVariable(new SymbolTableEntry(currentOriginalToken, id, tipoVar, valorParaTabla));
                            lastDeclaredType = null;
                        }
                        tokenToEmit = id;
                    }
                }
            }
            transformedTokens.add(tokenToEmit);

            if (currentOriginalToken.equals(";")) {
                lastDeclaredType = null;
            }
        }
        
        this.tablaSimbolos.actualizarValoresDeVariablesPostAnalisis();
        return transformedTokens;
    }
}