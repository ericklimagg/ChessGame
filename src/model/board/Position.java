package model.board;

import java.util.Objects;

/**
 * Representa uma coordenada (linha e coluna) no tabuleiro de xadrez.
 * Esta é uma classe imutável, o que significa que seus valores de linha e coluna
 * não podem ser alterados após a criação do objeto. Isso a torna segura para uso
 * em várias partes do sistema sem risco de modificação acidental.
 */
public final class Position {

    // A linha no tabuleiro, com 0 representando a fileira '8' (topo, onde as peças pretas começam)
    // e 7 representando a fileira '1' (fundo, onde as peças brancas começam).
    private final int row;

    // A coluna no tabuleiro, com 0 representando a coluna 'a' e 7 a coluna 'h'.
    private final int column;

    /**
     * Constrói um objeto Position com a linha e coluna especificadas.
     *
     * @param row    A linha (0-7).
     * @param column A coluna (0-7).
     */
    public Position(int row, int column) {
        this.row = row;
        this.column = column;
    }

    /**
     * @return O índice da linha (0-7).
     */
    public int getRow() { return row; }

    /**
     * @return O índice da coluna (0-7).
     */
    public int getColumn() { return column; }

    /**
     * Valida se a posição está dentro dos limites do tabuleiro de xadrez (8x8).
     *
     * @return true se a linha e a coluna estiverem entre 0 e 7 (inclusive), false caso contrário.
     */
    public boolean isValid() {
        return row >= 0 && row < 8 && column >= 0 && column < 8;
    }

    /**
     * Compara este objeto Position com outro para verificar se representam a mesma casa.
     * É essencial sobrescrever este método para que coleções (como Listas e Mapas)
     * possam comparar posições corretamente.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position)) return false;
        Position that = (Position) o;
        return row == that.row && column == that.column;
    }

    /**
     * Gera um código hash para o objeto Position.
     * É importante sobrescrever este método junto com `equals()` para garantir o
     * funcionamento correto de estruturas de dados baseadas em hash, como HashMap.
     */
    @Override
    public int hashCode() {
        return Objects.hash(row, column);
    }

    /**
     * Converte a posição (linha, coluna) para a notação algébrica padrão do xadrez.
     * Por exemplo, (row=7, column=0) se torna "a1".
     *
     * @return A representação da posição em formato de string (ex: "e4", "h8").
     */
    @Override
    public String toString() {
        char file = (char) ('a' + column); // Converte a coluna (0-7) para o caractere ('a'-'h').
        int rank = 8 - row;                // Converte a linha (0-7) para a fileira (8-1).
        return "" + file + rank;
    }
}