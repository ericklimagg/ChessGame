package model.board;

import java.util.ArrayList;
import java.util.List;
import model.pieces.Piece;

/**
 * Representa o tabuleiro de xadrez 8x8.
 * Esta classe gerencia a localização de todas as peças e fornece
 * métodos para manipular o tabuleiro, como colocar, mover e remover peças.
 * É uma classe central para a representação do estado do jogo (Model).
 */
public class Board {

    // A estrutura de dados principal: uma matriz 2D 8x8 para armazenar as peças.
    private final Piece[][] grid = new Piece[8][8];

    /**
     * Obtém a peça em uma determinada posição do tabuleiro.
     *
     * @param p A posição (linha, coluna) a ser verificada.
     * @return O objeto Piece na posição, ou null se a casa estiver vazia ou a posição for inválida.
     */
    public Piece get(Position p) {
        return (p.isValid()) ? grid[p.getRow()][p.getColumn()] : null;
    }

    /**
     * Coloca uma peça em uma posição específica no tabuleiro.
     * Também atualiza a referência de posição interna da própria peça.
     *
     * @param piece A peça a ser colocada.
     * @param p A posição de destino.
     */
    public void placePiece(Piece piece, Position p) {
        if (!p.isValid()) return;
        grid[p.getRow()][p.getColumn()] = piece;
        if (piece != null) {
            piece.setPosition(p);
        }
    }
    
    /**
     * Remove uma peça de uma posição, deixando a casa vazia.
     *
     * @param p A posição da qual a peça será removida.
     * @return A peça que foi removida, ou null se a casa já estava vazia.
     */
    public Piece remove(Position p) {
        if (!p.isValid()) return null;
        Piece piece = get(p);
        grid[p.getRow()][p.getColumn()] = null;
        return piece;
    }
    
    /**
     * Executa um movimento simples, movendo uma peça de uma posição para outra.
     *
     * @param from A posição de origem.
     * @param to A posição de destino.
     */
    public void move(Position from, Position to) {
        Piece piece = remove(from);
        placePiece(piece, to);
    }

    /**
     * Limpa o tabuleiro, removendo todas as peças.
     * Usado para iniciar um novo jogo.
     */
    public void clear() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                grid[r][c] = null;
            }
        }
    }

    /**
     * Coleta e retorna uma lista de todas as peças de uma cor específica no tabuleiro.
     *
     * @param white True para obter as peças brancas, False para as pretas.
     * @return Uma lista contendo as peças da cor especificada.
     */
    public List<Piece> getPieces(boolean white) {
        List<Piece> out = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece pc = grid[r][c];
                if (pc != null && pc.isWhite() == white) out.add(pc);
            }
        }
        return out;
    }

    /**
     * Cria uma cópia profunda (deep copy) do tabuleiro.
     * Este método é crucial para a IA, que precisa simular movimentos em um tabuleiro
     * temporário sem alterar o estado real do jogo. Também é usado na validação de lances.
     *
     * @return Um novo objeto Board com uma cópia exata do estado atual.
     */
    public Board copy() {
        Board b = new Board();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = grid[r][c];
                if (p != null) {
                    // Chama o método copyFor() de cada peça para criar uma nova instância da peça.
                    b.placePiece(p.copyFor(b), new Position(r, c));
                }
            }
        }
        return b;
    }
    
    /**
     * Gera uma representação da posição das peças no formato Forsyth-Edwards Notation (FEN).
     * Esta string é uma maneira padronizada de descrever uma posição de xadrez.
     * É usada aqui para a regra de empate por repetição tripla.
     *
     * @return Uma string FEN representando a disposição das peças.
     */
    public String getFenPosition() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int c = 0; c < 8; c++) {
                Piece p = grid[r][c];
                if (p == null) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    String sym = p.getSymbol();
                    // Letras maiúsculas para peças brancas, minúsculas para pretas.
                    sb.append(p.isWhite() ? sym.toUpperCase() : sym.toLowerCase());
                }
            }
            if (empty > 0) sb.append(empty);
            if (r < 7) sb.append('/'); // Adiciona a barra para separar as fileiras.
        }
        return sb.toString();
    }
}