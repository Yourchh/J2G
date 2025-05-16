package AnalizadorLexicoJ2G;

// No necesita imports si está en el mismo paquete que las otras clases
// o si las otras clases lo importan.

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
        return String.format("%-30s | %-10s | %-10s | %-30s", variable, id, tipo, valor);
    }
}
