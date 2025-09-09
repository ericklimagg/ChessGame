package controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import model.GameState;
import model.board.Board;
import model.board.Position;
import model.pieces.*;

/**
 * Controla a lógica principal, as regras e o estado do jogo de xadrez.
 * Esta classe é o núcleo do "Controller" no padrão MVC, gerenciando
 * a interação entre o jogador e as regras do jogo.
 */
public final class Game {

    // --- Variáveis de Estado do Jogo ---
    private Board board;                 // Representa o tabuleiro e a posição das peças.
    private boolean whiteToMove;         // Define de quem é a vez de jogar.
    private boolean gameOver;            // Sinaliza o fim da partida.
    private String gameEndMessage;       // Armazena a mensagem de final de jogo (ex: "Xeque-mate!").
    private int halfmoveClock;           // Contador para a regra de empate por 50 movimentos.
    private Map<String, Integer> positionHistory; // Rastreia posições para a regra de empate por repetição tripla.
    private Position enPassantTarget;    // Armazena a casa vulnerável à captura "en passant".
    private final List<String> history;  // Mantém um registro de todos os movimentos em notação de texto.
    
    // Pilha para armazenar o estado do jogo a cada movimento, permitindo a funcionalidade de "desfazer".
    private final Stack<GameState> gameStateHistory;

    /**
     * Construtor padrão da classe Game. Inicializa os componentes e começa um novo jogo.
     */
    public Game() {
        this.positionHistory = new HashMap<>();
        this.history = new ArrayList<>();
        this.gameStateHistory = new Stack<>();
        newGame(); 
    }

    /**
     * Construtor de cópia, essencial para a simulação de movimentos pela IA.
     * Permite que a IA explore futuras jogadas em uma cópia do jogo sem afetar o estado real.
     */
    public Game(Game other) {
        this.board = other.board.copy();
        this.whiteToMove = other.whiteToMove;
        this.gameOver = other.gameOver;
        this.gameEndMessage = other.gameEndMessage;
        this.halfmoveClock = other.halfmoveClock;
        this.positionHistory = new HashMap<>(other.positionHistory);
        this.enPassantTarget = other.enPassantTarget;
        this.history = new ArrayList<>(); // Históricos não são copiados para simulações.
        this.gameStateHistory = new Stack<>();
    }

    // --- Getters Públicos para Acesso ao Estado do Jogo ---
    public Board board() { return board; }
    public boolean whiteToMove() { return whiteToMove; }
    public boolean isGameOver() { return gameOver; }
    public String getGameEndMessage() { return gameEndMessage; }
    public List<String> history() { return Collections.unmodifiableList(history); }
    public int getHalfmoveClock() { return halfmoveClock; }
    public Map<String, Integer> getPositionHistory() { return positionHistory; }
    public Position getEnPassantTarget() { return enPassantTarget; }

    /**
     * Configura o tabuleiro e as variáveis de estado para o início de uma nova partida.
     */
    public void newGame() {
        this.board = new Board();
        setupPieces();
        this.whiteToMove = true;
        this.gameOver = false;
        this.gameEndMessage = "";
        this.enPassantTarget = null;
        this.history.clear();
        this.halfmoveClock = 0;
        this.positionHistory.clear();
        this.gameStateHistory.clear();
        updatePositionHistory();
        saveState(); // Salva o estado inicial para permitir desfazer desde o primeiro lance.
    }

    /**
     * Desfaz o último movimento, restaurando o estado anterior do jogo.
     */
    public void undoMove() {
        if (gameStateHistory.size() > 1) { // Garante que há um estado anterior para restaurar.
            gameStateHistory.pop(); // Remove o estado atual.
            GameState previousState = gameStateHistory.peek(); // Acessa o estado anterior.
            restoreFromState(previousState); // Restaura o jogo para esse estado.
            if (!history.isEmpty()) {
                history.remove(history.size() - 1); // Remove o registro de texto do último lance.
            }
        }
    }

    // Salva o estado atual do jogo na pilha de histórico.
    private void saveState() {
        gameStateHistory.push(new GameState(this));
    }

    // Restaura as variáveis de estado do jogo a partir de um objeto GameState.
    private void restoreFromState(GameState state) {
        this.board = state.getBoard().copy();
        this.whiteToMove = state.isWhiteToMove();
        this.gameOver = state.isGameOver();
        this.gameEndMessage = state.getGameEndMessage();
        this.halfmoveClock = state.getHalfmoveClock();
        this.positionHistory = new HashMap<>(state.getPositionHistory());
        this.enPassantTarget = state.getEnPassantTarget();
    }

    /**
     * Calcula e retorna todos os movimentos legais para a peça na posição especificada.
     * Este método considera todas as regras, incluindo xeque.
     *
     * @param from A posição da peça.
     * @return Uma lista de posições de destino legais.
     */
    public List<Position> legalMovesFrom(Position from) {
        return legalMovesFromWithSpecials(from);
    }

    /**
     * Verifica se um movimento de peão resulta em uma promoção.
     * @return True se o peão alcançou a última fileira.
     */
    public boolean isPromotion(Position from, Position to) {
        Piece p = board.get(from);
        if (!(p instanceof Pawn)) return false;
        return p.isWhite() ? to.getRow() == 0 : to.getRow() == 7;
    }

    /**
     * Processa um movimento do jogador.
     * Valida a legalidade do movimento, o executa e atualiza o estado do jogo.
     *
     * @param from Posição de origem.
     * @param to Posição de destino.
     * @param promotion Caractere para a promoção do peão ('Q', 'R', 'B', 'N'), ou null.
     * @return True se o movimento foi executado com sucesso, False caso contrário.
     */
    public boolean move(Position from, Position to, Character promotion) {
        if (gameOver) return false;

        Piece piece = board.get(from);
        if (piece == null || piece.isWhite() != whiteToMove) return false;

        List<Position> legal = legalMovesFromWithSpecials(from);
        if (!legal.contains(to)) return false;
        
        boolean isPawnMove = piece instanceof Pawn;
        boolean isCapture = board.get(to) != null || (isPawnMove && to.equals(enPassantTarget));

        // Executa o movimento no tabuleiro.
        performMove(from, to, promotion, piece);
        
        // Passa a vez para o outro jogador.
        whiteToMove = !whiteToMove;
        
        // Reseta os contadores de empate se um peão se moveu ou uma captura ocorreu.
        if (isPawnMove || isCapture) {
            halfmoveClock = 0;
            positionHistory.clear();
        } else {
            halfmoveClock++;
        }

        updatePositionHistory();
        saveState(); // Salva o novo estado após o movimento.
        checkGameEnd(); // Verifica se o jogo terminou.
        
        // Adiciona o movimento ao histórico em texto.
        String moveStr = piece.getSymbol() + from.toString() + (isCapture ? "x" : "-") + to.toString();
        if (isCheckmate(whiteToMove)) moveStr += "#";
        else if (inCheck(whiteToMove)) moveStr += "+";
        history.add(moveStr);
        
        return true;
    }
    
    /**
     * Contém a lógica detalhada para executar diferentes tipos de movimentos (normal, roque, en passant, promoção).
     */
    private void performMove(Position from, Position to, Character promotion, Piece piece) {
        // Lógica do Roque.
        if (piece instanceof King && Math.abs(to.getColumn() - from.getColumn()) == 2) {
            board.move(from, to);
            int row = from.getRow();
            if (to.getColumn() == 6) { // Roque do lado do rei
                board.move(new Position(row, 7), new Position(row, 5)); 
            } else { // Roque do lado da rainha
                board.move(new Position(row, 0), new Position(row, 3)); 
            }
        }
        // Lógica do En Passant.
        else if (piece instanceof Pawn && to.equals(enPassantTarget) && board.get(to) == null) {
            board.move(from, to);
            int capturedPawnRow = to.getRow() + (piece.isWhite() ? 1 : -1);
            board.remove(new Position(capturedPawnRow, to.getColumn()));
        }
        // Lógica da Promoção.
        else if (piece instanceof Pawn && isPromotion(from, to)) {
            char promoChar = (promotion != null) ? Character.toUpperCase(promotion) : 'Q';
            Piece newPiece = switch (promoChar) {
                case 'R' -> new Rook(board, piece.isWhite());
                case 'B' -> new Bishop(board, piece.isWhite());
                case 'N' -> new Knight(board, piece.isWhite());
                default -> new Queen(board, piece.isWhite());
            };
            board.remove(from);
            board.placePiece(newPiece, to);
        }
        // Movimento normal.
        else {
            board.move(from, to);
        }

        // Define o alvo para 'en passant' se um peão avançou duas casas.
        if (piece instanceof Pawn && Math.abs(from.getRow() - to.getRow()) == 2) {
            enPassantTarget = new Position((from.getRow() + to.getRow()) / 2, from.getColumn());
        } else {
            enPassantTarget = null;
        }

        // Marca que o rei ou a torre se moveram (importante para a lógica do roque).
        if (piece instanceof King || piece instanceof Rook) {
            piece.setMoved(true);
        }
    }

    /**
     * Verifica se um determinado lado está em xeque.
     * @param whiteSide True para as Brancas, False para as Pretas.
     * @return True se o rei desse lado estiver sob ataque.
     */
    public boolean inCheck(boolean whiteSide) {
        Position kingPos = findKing(whiteSide);
        return kingPos != null && isSquareAttacked(kingPos, !whiteSide);
    }
    
    /**
     * Verifica se um determinado lado está em xeque-mate.
     * @return True se o lado estiver em xeque e não tiver nenhum movimento legal.
     */
    public boolean isCheckmate(boolean whiteSide) {
        return inCheck(whiteSide) && !hasAnyLegalMove(whiteSide);
    }

    /**
     * Verifica todas as condições de término de jogo (xeque-mate, afogamento, empates).
     */
    private void checkGameEnd() {
        if (!hasAnyLegalMove(whiteToMove)) {
            gameOver = true;
            if (inCheck(whiteToMove)) {
                gameEndMessage = "Xeque-mate! " + (whiteToMove ? "Pretas" : "Brancas") + " vencem.";
            } else {
                gameEndMessage = "Empate por afogamento (Stalemate).";
            }
            return;
        }
        // Regra dos 50 movimentos
        if (halfmoveClock >= 100) {
            gameOver = true;
            gameEndMessage = "Empate pela regra dos 50 movimentos.";
            return;
        }
        // Regra de repetição tripla
        if (positionHistory.getOrDefault(board.getFenPosition(), 0) >= 3) {
            gameOver = true;
            gameEndMessage = "Empate por repetição tripla.";
            return;
        }
        // Regra de material insuficiente
        if (isInsufficientMaterial()) {
            gameOver = true;
            gameEndMessage = "Empate por material insuficiente.";
        }
    }
    
    /**
     * Verifica se o material no tabuleiro é insuficiente para forçar um xeque-mate.
     * (Ex: Rei vs Rei; Rei vs Rei e Bispo; Rei vs Rei e Cavalo).
     */
    private boolean isInsufficientMaterial() {
        List<Piece> whitePieces = board.getPieces(true);
        List<Piece> blackPieces = board.getPieces(false);
        if (whitePieces.size() <= 2 && blackPieces.size() <= 2) {
            if (whitePieces.size() == 1 && blackPieces.size() == 1) return true; // Rei vs Rei
            if (whitePieces.size() == 2 && blackPieces.size() == 1) { // Rei e peça menor vs Rei
                Piece p = whitePieces.stream().filter(pc -> !(pc instanceof King)).findFirst().orElse(null);
                return p instanceof Bishop || p instanceof Knight;
            }
            if (whitePieces.size() == 1 && blackPieces.size() == 2) { // Rei vs Rei e peça menor
                 Piece p = blackPieces.stream().filter(pc -> !(pc instanceof King)).findFirst().orElse(null);
                return p instanceof Bishop || p instanceof Knight;
            }
        }
        return false;
    }

    /**
     * Coleta os movimentos "pseudo-legais" de uma peça e depois os filtra, removendo
     * aqueles que deixariam o próprio rei em xeque. Também adiciona movimentos especiais como roque e en passant.
     */
    private List<Position> legalMovesFromWithSpecials(Position from) {
        Piece p = board.get(from);
        if (p == null || p.isWhite() != whiteToMove) return Collections.emptyList();

        // Começa com os movimentos básicos da peça.
        List<Position> moves = new ArrayList<>(p.getPossibleMoves());

        // Adiciona a captura en passant, se aplicável.
        if (p instanceof Pawn && enPassantTarget != null) {
            List<Position> attacks = p.getAttacks();
            if (attacks.contains(enPassantTarget)) {
                moves.add(enPassantTarget);
            }
        }

        // Adiciona os movimentos de roque, se aplicável.
        if (p instanceof King && !p.hasMoved() && !inCheck(p.isWhite())) {
            if (canCastle(p.isWhite(), true)) moves.add(new Position(from.getRow(), 6));
            if (canCastle(p.isWhite(), false)) moves.add(new Position(from.getRow(), 2));
        }

        // Filtro final: remove qualquer movimento que deixe o rei em xeque.
        moves.removeIf(to -> leavesKingInCheck(from, to));
        return moves;
    }
    
    /**
     * Verifica se o roque é possível para um determinado lado.
     * Checa se o rei e a torre não se moveram, se o caminho está livre e se não passa por xeque.
     */
    private boolean canCastle(boolean isWhite, boolean kingSide) {
        int row = isWhite ? 7 : 0;
        int rookCol = kingSide ? 7 : 0;
        Piece rook = board.get(new Position(row, rookCol));

        if (!(rook instanceof Rook) || rook.hasMoved()) return false;
        
        // Verifica se as casas entre o rei e a torre estão vazias.
        int[] path = kingSide ? new int[]{5, 6} : new int[]{1, 2, 3};
        for (int col : path) {
            if (board.get(new Position(row, col)) != null) return false;
        }

        // Verifica se as casas pelas quais o rei passa não estão sob ataque.
        int[] checkPath = kingSide ? new int[]{4, 5, 6} : new int[]{2, 3, 4};
        for (int col : checkPath) {
            if (isSquareAttacked(new Position(row, col), !isWhite)) return false;
        }

        return true;
    }

    /**
     * Simula um movimento em uma cópia do tabuleiro para verificar se ele resultaria
     * em o próprio rei ficar em xeque.
     */
    private boolean leavesKingInCheck(Position from, Position to) {
        Game tempGame = new Game(this);
        Piece p = tempGame.board.get(from);
        tempGame.performMove(from, to, 'Q', p); // 'Q' para promoção padrão na simulação.
        return tempGame.inCheck(p.isWhite());
    }

    /**
     * Verifica se uma determinada casa está sendo atacada pelo oponente.
     */
    private boolean isSquareAttacked(Position sq, boolean byWhite) {
        for (Piece p : board.getPieces(byWhite)) {
            if (p.getAttacks().contains(sq)) {
                return true;
            }
        }
        return false;
    }
    
    // Encontra a posição do rei de uma determinada cor.
    public Position findKing(boolean whiteSide) {
        return board.getPieces(whiteSide).stream()
                .filter(p -> p instanceof King)
                .map(Piece::getPosition)
                .findFirst().orElse(null);
    }
    
    // Verifica se um lado tem pelo menos um movimento legal.
    private boolean hasAnyLegalMove(boolean forWhiteSide) {
        for (Piece p : board.getPieces(forWhiteSide)) {
            if (!legalMovesFromWithSpecials(p.getPosition()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // Atualiza o histórico de posições para a regra de repetição tripla.
    private void updatePositionHistory() {
        String fen = board.getFenPosition();
        positionHistory.put(fen, positionHistory.getOrDefault(fen, 0) + 1);
    }

    /**
     * Posiciona todas as peças em suas casas iniciais no tabuleiro.
     */
    private void setupPieces() {
        board.clear();
        // Peças Brancas
        board.placePiece(new Rook(board, true), new Position(7, 0));
        board.placePiece(new Knight(board, true), new Position(7, 1));
        board.placePiece(new Bishop(board, true), new Position(7, 2));
        board.placePiece(new Queen(board, true), new Position(7, 3));
        board.placePiece(new King(board, true), new Position(7, 4));
        board.placePiece(new Bishop(board, true), new Position(7, 5));
        board.placePiece(new Knight(board, true), new Position(7, 6));
        board.placePiece(new Rook(board, true), new Position(7, 7));
        for (int c = 0; c < 8; c++) board.placePiece(new Pawn(board, true), new Position(6, c));

        // Peças Pretas
        board.placePiece(new Rook(board, false), new Position(0, 0));
        board.placePiece(new Knight(board, false), new Position(0, 1));
        board.placePiece(new Bishop(board, false), new Position(0, 2));
        board.placePiece(new Queen(board, false), new Position(0, 3));
        board.placePiece(new King(board, false), new Position(0, 4));
        board.placePiece(new Bishop(board, false), new Position(0, 5));
        board.placePiece(new Knight(board, false), new Position(0, 6));
        board.placePiece(new Rook(board, false), new Position(0, 7));
        for (int c = 0; c < 8; c++) board.placePiece(new Pawn(board, false), new Position(1, c));
    }
}