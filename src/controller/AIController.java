package controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingWorker;
import model.board.Board;
import model.board.Position;
import model.pieces.*;

/**
 * Controla a lógica da Inteligência Artificial (IA) usando o algoritmo Minimax com poda Alfa-Beta.
 * Esta versão inclui avaliação posicional para uma jogabilidade mais estratégica.
 */
public class AIController {

    public static class Move {
        public final Position from, to;
        public Move(Position f, Position t) { this.from = f; this.to = t; }
    }

    // Tabelas de Posição de Peças (Piece-Square Tables)
    // Dão um bónus/penalidade para a posição de cada peça.
    // As tabelas são para as peças brancas; para as pretas, as linhas são invertidas.
    private static final int[] PAWN_TABLE = {
        0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
         5,  5, 10, 25, 25, 10,  5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,
         5, -5,-10,  0,  0,-10, -5,  5,
         5, 10, 10,-20,-20, 10, 10,  5,
         0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final int[] KNIGHT_TABLE = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50,
    };

    private static final int[] BISHOP_TABLE = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20,
    };
    
    private static final int[] KING_TABLE = {
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20
    };


    public void findBestMove(Game game, int depth, java.util.function.Consumer<Move> onDone) {
        new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() throws Exception {
                // A IA joga com as Pretas (minimizador)
                return minimaxRoot(game, depth, false);
            }

            @Override
            protected void done() {
                try {
                    onDone.accept(get());
                } catch (Exception e) {
                    e.printStackTrace();
                    onDone.accept(null);
                }
            }
        }.execute();
    }

    private Move minimaxRoot(Game game, int depth, boolean isMaximizingPlayer) {
        List<Move> allMoves = collectAllLegalMovesForSide(game, game.whiteToMove());
        Collections.shuffle(allMoves); // Para variar as jogadas de abertura

        int bestScore = isMaximizingPlayer ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        Move bestMove = null;

        for (Move move : allMoves) {
            Game tempGame = new Game(game);
            tempGame.move(move.from, move.to, 'Q');

            int score = minimax(tempGame, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isMaximizingPlayer);

            if (isMaximizingPlayer) {
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            } else {
                if (score < bestScore) {
                    bestScore = score;
                    bestMove = move;
                }
            }
        }
        return bestMove;
    }

    private int minimax(Game game, int depth, int alpha, int beta, boolean isMaximizingPlayer) {
        if (depth == 0 || game.isGameOver()) {
            return evaluateBoard(game.board());
        }

        List<Move> allMoves = collectAllLegalMovesForSide(game, game.whiteToMove());

        if (isMaximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (Move move : allMoves) {
                Game tempGame = new Game(game);
                tempGame.move(move.from, move.to, 'Q');
                int eval = minimax(tempGame, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break; // Poda
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (Move move : allMoves) {
                Game tempGame = new Game(game);
                tempGame.move(move.from, move.to, 'Q');
                int eval = minimax(tempGame, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break; // Poda
            }
            return minEval;
        }
    }

    private int evaluateBoard(Board board) {
        int totalScore = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = board.get(new Position(r, c));
                if (p != null) {
                    totalScore += getPieceValueWithPosition(p);
                }
            }
        }
        return totalScore;
    }
    
    private int getPieceValueWithPosition(Piece p) {
        int value = 0;
        int row = p.getPosition().getRow();
        int col = p.getPosition().getColumn();
        int tableIndex = p.isWhite() ? row * 8 + col : (7 - row) * 8 + col;

        switch(p.getSymbol()) {
            case "P": value = 100 + PAWN_TABLE[tableIndex]; break;
            case "N": value = 320 + KNIGHT_TABLE[tableIndex]; break;
            case "B": value = 330 + BISHOP_TABLE[tableIndex]; break;
            case "R": value = 500; break; // Torres não usam tabela posicional nesta versão simples
            case "Q": value = 900; break;
            case "K": value = 20000 + KING_TABLE[tableIndex]; break;
        }
        
        return p.isWhite() ? value : -value;
    }

    private List<Move> collectAllLegalMovesForSide(Game game, boolean isWhite) {
        List<Move> moves = new ArrayList<>();
        for (Piece p : game.board().getPieces(isWhite)) {
            for (Position to : game.legalMovesFrom(p.getPosition())) {
                moves.add(new Move(p.getPosition(), to));
            }
        }
        return moves;
    }
}