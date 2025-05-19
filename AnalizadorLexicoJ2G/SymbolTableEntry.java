/**
 * Clase que representa una entrada en la tabla de símbolos.
 * Almacena información sobre variables en el programa.
 * 
 * Atributos:
 * @param variable El nombre o identificador de la variable
 * @param id El identificador único asignado a la variable
 * @param tipo El tipo de dato de la variable
 * @param valor El valor almacenado en la variable
 * 
 * Métodos:
 * - Constructor: Crea una nueva entrada con los datos de la variable
 * - toString: Convierte los datos de la entrada a texto formateado
 */

package AnalizadorLexicoJ2G;

class SymbolTableEntry {
    String variable;
    String id;
    String tipo;
    String valor;

    public SymbolTableEntry(String variable, String id, String tipo, String valor) {
        this.variable = variable;
        this.id = id;
        this.tipo = tipo;
        this.valor = valor;
    }

    @Override
    public String toString() {
        return String.format("%-40s | %-10s | %-10s | %-40s", variable, id, tipo, valor);
    }
}
