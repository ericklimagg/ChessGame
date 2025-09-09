package controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.SwingWorker;
import model.board.Board;
import model.board.Position;
import model.pieces.*;

/**
 * Controla a lógica da Inteligência Artificial (IA).
 * Utiliza um algoritmo Minimax com poda Alfa-Beta e a técnica de aprofundamento iterativo
 * para calcular o melhor movimento dentro de um limite de tempo.
 * A avaliação da IA é aprimorada com Piece-Square Tables (PST) e bônus de mobilidade.
 */
public class AIController {

    // Estrutura interna para representar um movimento (de 'from' para 'to').
    public static class AIMove {
        public final Position from, to;
        public AIMove(Position f, Position t) { this.from = f; this.to = t; }
    }

    // Estrutura interna que associa um movimento a sua pontuação para facilitar a ordenação.
    private record MoveScore(AIMove move, int score) {}

    // Flag volátil para sinalizar a interrupção da busca por tempo.
    // Garante consistência entre a thread do timer e a de busca.
    private volatile boolean timeUp;

    // --- Piece-Square Tables (PST) ---
    // As tabelas a seguir dão um bônus ou penalidade para a posição de cada peça.
    // Elas incentivam a IA a dominar o centro, posicionar bem as peças e proteger o rei.
    // As tabelas são definidas para as peças brancas; para as pretas, as linhas são invertidas.
    private static final int[] PAWN_TABLE = {
        0,  0,  0,  0,  0,  0,  0,  0, 50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,  5,  5, 10, 25, 25, 10,  5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,  5, -5,-10,  0,  0,-10, -5,  5,
         5, 10, 10,-20,-20, 10, 10,  5,  0,  0,  0,  0,  0,  0,  0,  0
    };
    private static final int[] KNIGHT_TABLE = {
        -50,-40,-30,-30,-30,-30,-40,-50,-40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,-30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,-30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,-50,-40,-30,-30,-30,-30,-40,-50,
    };
    private static final int[] BISHOP_TABLE = {
        -20,-10,-10,-10,-10,-10,-10,-20,-10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,-10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,-10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,-20,-10,-10,-10,-10,-10,-10,-20,
    };
    // Tabela de Rei para meio de jogo, priorizando a segurança e o roque.
    private static final int[] KING_TABLE_MIDDLEGAME = {
        -30,-40,-40,-50,-50,-40,-40,-30,-30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,-30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,-10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20, 20, 30, 10,  0,  0, 10, 30, 20
    };
    // Tabela de Rei para final de jogo, incentivando o rei a se tornar uma peça ativa e centralizada.
    private static final int[] KING_TABLE_ENDGAME = {
        -50,-40,-30,-20,-20,-30,-40,-50, -30,-20,-10,  0,  0,-10,-20,-30,
        -30,-10, 20, 30, 30, 20,-10,-30, -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30, -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-20,-10,  0,  0,-10,-20,-30, -50,-40,-30,-20,-20,-30,-40,-50,
    };

    /**
     * Inicia a busca pelo melhor movimento em uma thread separada para não bloquear a interface gráfica.
     * Utiliza a abordagem de aprofundamento iterativo.
     *
     * @param game O estado atual do jogo.
     * @param timeLimitMillis O tempo máximo de busca em milissegundos.
     * @param difficultyIndex O nível de dificuldade (0-fácil a 3-expert).
     * @param onDone Callback a ser executado com o movimento encontrado.
     */
    public void findBestMove(Game game, long timeLimitMillis, int difficultyIndex, java.util.function.Consumer<AIMove> onDone) {
        new SwingWorker<AIMove, Void>() {
            @Override
            protected AIMove doInBackground() {
                timeUp = false;
                // Thread separada que atua como um timer para a busca.
                Thread timer = new Thread(() -> {
                    try {
                        Thread.sleep(timeLimitMillis);
                        timeUp = true;
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                timer.start();

                List<MoveScore> bestMovesList = new ArrayList<>();
                List<AIMove> allMoves = collectAllLegalMovesForSide(game, game.whiteToMove(), true);
                if (allMoves.isEmpty()) return null;
                
                // Aprofundamento Iterativo: busca em profundidade 1, depois 2, 3, etc., até o tempo esgotar.
                // Isso garante que sempre tenhamos um resultado, mesmo que o tempo seja curto.
                for (int depth = 1; depth < 100; depth++) {
                    List<MoveScore> currentScoredMoves = searchAtDepth(game, depth, allMoves, game.whiteToMove());
                    if (timeUp) break; // Interrompe se o tempo acabou.
                    bestMovesList = currentScoredMoves; // Salva o resultado da última busca completa.
                }
                
                timer.interrupt(); // Para o timer, pois a busca foi concluída ou interrompida.
                return selectMoveBasedOnDifficulty(bestMovesList, difficultyIndex);
            }

            @Override
            protected void done() {
                try { onDone.accept(get()); }
                catch (Exception e) { e.printStackTrace(); onDone.accept(null); }
            }
        }.execute();
    }
    
    /**
     * Seleciona um movimento da lista de melhores lances com base no nível de dificuldade.
     * Níveis mais baixos introduzem uma chance de erro, tornando a IA mais humana.
     *
     * @param scoredMoves Lista de movimentos ordenados pela pontuação.
     * @param difficultyIndex O nível de dificuldade.
     * @return O movimento escolhido.
     */
    private AIMove selectMoveBasedOnDifficulty(List<MoveScore> scoredMoves, int difficultyIndex) {
        if (scoredMoves.isEmpty()) return null;

        double r = Math.random(); // Fator de aleatoriedade para a seleção.

        return switch (difficultyIndex) {
            // Fácil: 60% melhor lance, 30% 2º melhor, 10% 3º melhor.
            case 0 -> {
                if (scoredMoves.size() <= 2) yield scoredMoves.get(0).move();
                if (r < 0.60) yield scoredMoves.get(0).move();
                if (r < 0.90) yield scoredMoves.get(1).move();
                yield scoredMoves.get(2).move();
            }
            // Médio: 85% melhor lance, 15% 2º melhor.
            case 1 -> {
                if (scoredMoves.size() == 1) yield scoredMoves.get(0).move();
                if (r < 0.85) yield scoredMoves.get(0).move();
                yield scoredMoves.get(1).move();
            }
            // Difícil: 95% melhor lance, 5% 2º melhor.
            case 2 -> {
                if (scoredMoves.size() == 1) yield scoredMoves.get(0).move();
                if (r < 0.95) yield scoredMoves.get(0).move();
                yield scoredMoves.get(1).move();
            }
            // Expert: Sempre joga o melhor lance.
            default -> scoredMoves.get(0).move();
        };
    }

    /**
     * Itera sobre todos os movimentos possíveis em uma dada profundidade, os pontua usando
     * o minimax e os retorna ordenados.
     * @return Lista de movimentos com suas pontuações, ordenada.
     */
    private List<MoveScore> searchAtDepth(Game game, int depth, List<AIMove> allMoves, boolean isMaximizingPlayer) {
        List<MoveScore> scoredMoves = new ArrayList<>();

        for (AIMove move : allMoves) {
            Game tempGame = new Game(game);
            tempGame.move(move.from, move.to, 'Q');
            int score = minimax(tempGame, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, !isMaximizingPlayer);
            if (timeUp) return scoredMoves; // Retorna imediatamente se o tempo acabar.
            scoredMoves.add(new MoveScore(move, score));
        }

        // Ordena os movimentos. Ordem descendente para o maximizador (Brancas) e ascendente para o minimizador (Pretas).
        if (isMaximizingPlayer) {
            scoredMoves.sort(Comparator.comparingInt(MoveScore::score).reversed());
        } else {
            scoredMoves.sort(Comparator.comparingInt(MoveScore::score));
        }
        return scoredMoves;
    }

    /**
     * Implementação recursiva do algoritmo Minimax com poda Alfa-Beta.
     *
     * @param depth Profundidade restante da busca.
     * @param alpha Melhor valor para o maximizador encontrado até agora.
     * @param beta Melhor valor para o minimizador encontrado até agora.
     * @param isMaximizingPlayer True se o jogador atual for o maximizador.
     * @return A avaliação da posição.
     */
    private int minimax(Game game, int depth, int alpha, int beta, boolean isMaximizingPlayer) {
        if (timeUp) return 0;
        if (depth == 0 || game.isGameOver()) {
            return evaluateBoard(game, game.whiteToMove());
        }

        List<AIMove> allMoves = collectAllLegalMovesForSide(game, game.whiteToMove(), false);
        if (allMoves.isEmpty()) {
            return evaluateBoard(game, game.whiteToMove());
        }

        if (isMaximizingPlayer) {
            int maxEval = Integer.MIN_VALUE;
            for (AIMove move : allMoves) {
                Game tempGame = new Game(game);
                tempGame.move(move.from, move.to, 'Q');
                int eval = minimax(tempGame, depth - 1, alpha, beta, false);
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha) break; // Poda Alfa-Beta: corta ramos da árvore de busca que não influenciarão no resultado.
            }
            return maxEval;
        } else {
            int minEval = Integer.MAX_VALUE;
            for (AIMove move : allMoves) {
                Game tempGame = new Game(game);
                tempGame.move(move.from, move.to, 'Q');
                int eval = minimax(tempGame, depth - 1, alpha, beta, true);
                minEval = Math.min(minEval, eval);
                beta = Math.min(beta, eval);
                if (beta <= alpha) break; // Poda Alfa-Beta.
            }
            return minEval;
        }
    }

    /**
     * Avalia a posição do tabuleiro e retorna uma pontuação.
     * Pontuação positiva favorece as Brancas, negativa favorece as Pretas.
     */
    private int evaluateBoard(Game game, boolean isWhiteToMove) {
        int totalScore = 0;
        Board board = game.board();
        
        // Determina se o jogo está em sua fase final (endgame) para usar a PST correta para o rei.
        boolean isEndGame = (board.getPieces(true).size() + board.getPieces(false).size()) <= 12;

        for (Piece p : board.getPieces(true)) totalScore += getPieceValue(p, isEndGame, game);
        for (Piece p : board.getPieces(false)) totalScore -= getPieceValue(p, isEndGame, game);
        
        // Bônus de "tempo": um pequeno incentivo para o lado que tem a vez de jogar.
        totalScore += isWhiteToMove ? 10 : -10;
        
        return totalScore;
    }
    
    /**
     * Calcula o valor de uma única peça.
     * A avaliação é a soma de: valor material + bônus da PST + bônus de mobilidade.
     */
    private int getPieceValue(Piece p, boolean isEndGame, Game game) {
        int value = 0;
        String symbol = p.getSymbol();
        int row = p.getPosition().getRow();
        int col = p.getPosition().getColumn();
        int tableIndex = p.isWhite() ? row * 8 + col : (7 - row) * 8 + col;

        switch(symbol) {
            case "P": value = 100 + PAWN_TABLE[tableIndex]; break;
            case "N": value = 320 + KNIGHT_TABLE[tableIndex]; break;
            case "B": value = 330 + BISHOP_TABLE[tableIndex]; break;
            case "R": value = 500; break;
            case "Q": value = 900; break;
            case "K":
                value = 20000 + (isEndGame ? KING_TABLE_ENDGAME[tableIndex] : KING_TABLE_MIDDLEGAME[tableIndex]);
                break;
        }
        
        // Bônus de mobilidade: adiciona um pequeno valor para cada movimento legal que a peça tem.
        value += game.legalMovesFrom(p.getPosition()).size();
        
        return value;
    }

    /**
     * Coleta todos os movimentos legais para um determinado lado.
     */
    private List<AIMove> collectAllLegalMovesForSide(Game game, boolean isWhite, boolean shuffle) {
        List<AIMove> moves = new ArrayList<>();
        for (Piece p : game.board().getPieces(isWhite)) {
            for (Position to : game.legalMovesFrom(p.getPosition())) {
                moves.add(new AIMove(p.getPosition(), to));
            }
        }
        // Embaralhar os movimentos no nível raiz ajuda a IA a variar suas aberturas.
        if (shuffle) Collections.shuffle(moves);
        return moves;
    }
}