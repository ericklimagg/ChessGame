package model;

import java.util.HashMap;
import java.util.Map;
import controller.Game;
import model.board.Board;
import model.board.Position;

/**
 * Representa um "snapshot" (um registro imutável) de um estado específico do jogo.
 * Esta classe é fundamental para o sistema de histórico, permitindo funcionalidades
 * como "desfazer movimento". Por ser imutável, garante que um estado salvo
 * não possa ser alterado acidentalmente, preservando a integridade do histórico do jogo.
 */
public final class GameState {

    // Campos finais (final) para garantir a imutabilidade do estado.
    private final Board board;
    private final boolean whiteToMove;
    private final boolean gameOver;
    private final String gameEndMessage;
    private final int halfmoveClock;
    private final Map<String, Integer> positionHistory;
    private final Position enPassantTarget;

    /**
     * Constrói um novo GameState a partir de uma instância ativa do jogo.
     * Realiza uma cópia defensiva dos dados mutáveis (como o tabuleiro e o histórico de posições)
     * para garantir que o GameState seja um snapshot completamente independente e seguro.
     *
     * @param game O objeto Game do qual o estado será capturado.
     */
    public GameState(Game game) {
        // A cópia profunda (deep copy) do tabuleiro é essencial para isolar o estado.
        this.board = game.board().copy();
        this.whiteToMove = game.whiteToMove();
        this.gameOver = game.isGameOver();
        this.gameEndMessage = game.getGameEndMessage();
        this.halfmoveClock = game.getHalfmoveClock();
        // O mapa de histórico de posições também é copiado.
        this.positionHistory = new HashMap<>(game.getPositionHistory());
        // A classe Position já é imutável, então uma referência direta é segura.
        this.enPassantTarget = game.getEnPassantTarget();
    }

    // --- Getters ---
    // Métodos de acesso para permitir que a classe Game restaure seu estado a partir deste snapshot.

    /**
     * @return Uma cópia do tabuleiro neste estado do jogo.
     */
    public Board getBoard() {
        return board;
    }

    /**
     * @return De quem era a vez de jogar neste estado.
     */
    public boolean isWhiteToMove() {
        return whiteToMove;
    }

    /**
     * @return Se o jogo havia terminado neste estado.
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * @return A mensagem de fim de jogo, se houver.
     */
    public String getGameEndMessage() {
        return gameEndMessage;
    }

    /**
     * @return O valor do contador de 50 movimentos neste estado.
     */
    public int getHalfmoveClock() {
        return halfmoveClock;
    }

    /**
     * @return O mapa do histórico de posições FEN para a regra de repetição tripla.
     */
    public Map<String, Integer> getPositionHistory() {
        return positionHistory;
    }

    /**
     * @return A posição de alvo para captura en passant, se houver.
     */
    public Position getEnPassantTarget() {
        return enPassantTarget;
    }
}