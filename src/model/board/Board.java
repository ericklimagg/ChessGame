package model.board;

import java.util.ArrayList;
import java.util.List;
import model.pieces.Piece;

public class Board {

    private final Piece[][] grid = new Piece[8][8];

    public Piece get(Position p) {
        return (p.isValid()) ? grid[p.getRow()][p.getColumn()] : null;
    }

    public void placePiece(Piece piece, Position p) {
        if (!p.isValid()) return;
        grid[p.getRow()][p.getColumn()] = piece;
        if (piece != null) {
            piece.setPosition(p);
        }
    }
    
    public Piece remove(Position p) {
        if (!p.isValid()) return null;
        Piece piece = get(p);
        grid[p.getRow()][p.getColumn()] = null;
        return piece;
    }
    
    public void move(Position from, Position to) {
        Piece piece = remove(from);
        placePiece(piece, to);
    }

    public void clear() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                grid[r][c] = null;
            }
        }
    }

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
     * Cópia profunda do tabuleiro. Essencial para a IA e validação de lances.
     */
    public Board copy() {
        Board b = new Board();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = grid[r][c];
                if (p != null) {
                    b.placePiece(p.copyFor(b), new Position(r, c));
                }
            }
        }
        return b;
    }
    
    /**
     * Gera uma representação FEN simplificada da posição das peças.
     * Usado para a regra de repetição tripla.
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
                    sb.append(p.isWhite() ? sym.toUpperCase() : sym.toLowerCase());
                }
            }
            if (empty > 0) sb.append(empty);
            if (r < 7) sb.append('/');
        }
        return sb.toString();
    }
}