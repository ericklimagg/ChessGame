package controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.board.Board;
import model.board.Position;
import model.pieces.*;

public class Game {

    private Board board;
    private boolean whiteToMove;
    private boolean gameOver;
    private String gameEndMessage;
    private int halfmoveClock;
    private final Map<String, Integer> positionHistory;
    private Position enPassantTarget;
    private final List<String> history;

    // Construtor principal CORRIGIDO para remover aviso
    public Game() {
        this.positionHistory = new HashMap<>();
        this.history = new ArrayList<>();
        newGame(); // Chama o método para configurar um novo jogo
    }

    // Construtor de cópia para a IA simular lances
    public Game(Game other) {
        this.board = other.board.copy();
        this.whiteToMove = other.whiteToMove;
        this.gameOver = other.gameOver;
        this.gameEndMessage = other.gameEndMessage;
        this.halfmoveClock = other.halfmoveClock;
        this.positionHistory = new HashMap<>(other.positionHistory);
        this.enPassantTarget = other.enPassantTarget;
        this.history = new ArrayList<>(); // O histórico não precisa ser copiado para simulação
    }

    // --------- Getters Públicos ----------
    public Board board() { return board; }
    public boolean whiteToMove() { return whiteToMove; }
    public boolean isGameOver() { return gameOver; }
    public String getGameEndMessage() { return gameEndMessage; }
    public List<String> history() { return Collections.unmodifiableList(history); }

    // --------- Iniciar Novo Jogo ----------
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
        updatePositionHistory();
    }

    // --------- Consultar Lances Legais ----------
    public List<Position> legalMovesFrom(Position from) {
        return legalMovesFromWithSpecials(from);
    }

    public boolean isPromotion(Position from, Position to) {
        Piece p = board.get(from);
        if (!(p instanceof Pawn)) return false;
        return p.isWhite() ? to.getRow() == 0 : to.getRow() == 7;
    }

    // --------- Executar um Lance ----------
    public boolean move(Position from, Position to, Character promotion) {
        if (gameOver) return false;

        Piece piece = board.get(from);
        if (piece == null || piece.isWhite() != whiteToMove) return false;

        List<Position> legal = legalMovesFromWithSpecials(from);
        if (!legal.contains(to)) return false;
        
        Piece capturedPiece = board.get(to);
        boolean isPawnMove = piece instanceof Pawn;
        boolean isCapture = capturedPiece != null || (isPawnMove && to.equals(enPassantTarget));

        performMove(from, to, promotion, piece);
        
        whiteToMove = !whiteToMove;
        
        if (isPawnMove || isCapture) {
            halfmoveClock = 0;
            positionHistory.clear();
        } else {
            halfmoveClock++;
        }
        updatePositionHistory();

        checkGameEnd();
        
        String moveStr = piece.getSymbol() + from.toString() + (isCapture ? "x" : "-") + to.toString();
        if (isCheckmate(whiteToMove)) moveStr += "#";
        else if (inCheck(whiteToMove)) moveStr += "+";
        history.add(moveStr);
        
        return true;
    }
    
    private void performMove(Position from, Position to, Character promotion, Piece piece) {
        if (piece instanceof King && Math.abs(to.getColumn() - from.getColumn()) == 2) {
            board.move(from, to);
            int row = from.getRow();
            if (to.getColumn() == 6) { board.move(new Position(row, 7), new Position(row, 5)); } 
            else { board.move(new Position(row, 0), new Position(row, 3)); }
        }
        else if (piece instanceof Pawn && to.equals(enPassantTarget) && board.get(to) == null) {
            board.move(from, to);
            int capturedPawnRow = to.getRow() + (piece.isWhite() ? 1 : -1);
            board.remove(new Position(capturedPawnRow, to.getColumn()));
        }
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
        else {
            board.move(from, to);
        }

        if (piece instanceof Pawn && Math.abs(from.getRow() - to.getRow()) == 2) {
            enPassantTarget = new Position((from.getRow() + to.getRow()) / 2, from.getColumn());
        } else {
            enPassantTarget = null;
        }

        if (piece instanceof King || piece instanceof Rook) {
            piece.setMoved(true);
        }
    }

    public boolean inCheck(boolean whiteSide) {
        Position kingPos = findKing(whiteSide);
        return kingPos != null && isSquareAttacked(kingPos, !whiteSide);
    }
    
    public boolean isCheckmate(boolean whiteSide) {
        return inCheck(whiteSide) && !hasAnyLegalMove(whiteSide);
    }

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
        if (halfmoveClock >= 100) {
            gameOver = true;
            gameEndMessage = "Empate pela regra dos 50 movimentos.";
            return;
        }
        if (positionHistory.getOrDefault(board.getFenPosition(), 0) >= 3) {
            gameOver = true;
            gameEndMessage = "Empate por repetição tripla.";
            return;
        }
        if (isInsufficientMaterial()) {
            gameOver = true;
            gameEndMessage = "Empate por material insuficiente.";
        }
    }
    
    private boolean isInsufficientMaterial() {
        List<Piece> whitePieces = board.getPieces(true);
        List<Piece> blackPieces = board.getPieces(false);
        if (whitePieces.size() <= 2 && blackPieces.size() <= 2) {
            if (whitePieces.size() == 1 && blackPieces.size() == 1) return true;
            if (whitePieces.size() == 2 && blackPieces.size() == 1) {
                Piece p = whitePieces.stream().filter(pc -> !(pc instanceof King)).findFirst().orElse(null);
                return p instanceof Bishop || p instanceof Knight;
            }
            if (whitePieces.size() == 1 && blackPieces.size() == 2) {
                 Piece p = blackPieces.stream().filter(pc -> !(pc instanceof King)).findFirst().orElse(null);
                return p instanceof Bishop || p instanceof Knight;
            }
        }
        return false;
    }

    private List<Position> legalMovesFromWithSpecials(Position from) {
        Piece p = board.get(from);
        if (p == null || p.isWhite() != whiteToMove) return Collections.emptyList();

        List<Position> moves = new ArrayList<>(p.getPossibleMoves());

        if (p instanceof Pawn && enPassantTarget != null) {
            List<Position> attacks = p.getAttacks();
            if (attacks.contains(enPassantTarget)) {
                moves.add(enPassantTarget);
            }
        }

        if (p instanceof King && !p.hasMoved() && !inCheck(p.isWhite())) {
            if (canCastle(p.isWhite(), true)) moves.add(new Position(from.getRow(), 6));
            if (canCastle(p.isWhite(), false)) moves.add(new Position(from.getRow(), 2));
        }

        moves.removeIf(to -> leavesKingInCheck(from, to));
        return moves;
    }
    
    private boolean canCastle(boolean isWhite, boolean kingSide) {
        int row = isWhite ? 7 : 0;
        int rookCol = kingSide ? 7 : 0;
        Position rookPos = new Position(row, rookCol);
        Piece rook = board.get(rookPos);

        if (!(rook instanceof Rook) || rook.hasMoved()) return false;
        
        int[] path = kingSide ? new int[]{5, 6} : new int[]{1, 2, 3};
        for (int col : path) {
            if (board.get(new Position(row, col)) != null) return false;
        }

        int[] checkPath = kingSide ? new int[]{4, 5, 6} : new int[]{2, 3, 4};
        for (int col : checkPath) {
            if (isSquareAttacked(new Position(row, col), !isWhite)) return false;
        }

        return true;
    }

    private boolean leavesKingInCheck(Position from, Position to) {
        Game tempGame = new Game(this);
        Piece p = tempGame.board.get(from);
        tempGame.performMove(from, to, 'Q', p);
        return tempGame.inCheck(p.isWhite());
    }

    private boolean isSquareAttacked(Position sq, boolean byWhite) {
        for (Piece p : board.getPieces(byWhite)) {
            if (p.getAttacks().contains(sq)) {
                return true;
            }
        }
        return false;
    }
    
    public Position findKing(boolean whiteSide) {
        return board.getPieces(whiteSide).stream()
                .filter(p -> p instanceof King)
                .map(Piece::getPosition)
                .findFirst().orElse(null);
    }
    
    private boolean hasAnyLegalMove(boolean forWhiteSide) {
        for (Piece p : board.getPieces(forWhiteSide)) {
            if (!legalMovesFromWithSpecials(p.getPosition()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void updatePositionHistory() {
        String fen = board.getFenPosition();
        positionHistory.put(fen, positionHistory.getOrDefault(fen, 0) + 1);
    }

    private void setupPieces() {
        board.clear();
        board.placePiece(new Rook(board, true), new Position(7, 0));
        board.placePiece(new Knight(board, true), new Position(7, 1));
        board.placePiece(new Bishop(board, true), new Position(7, 2));
        board.placePiece(new Queen(board, true), new Position(7, 3));
        board.placePiece(new King(board, true), new Position(7, 4));
        board.placePiece(new Bishop(board, true), new Position(7, 5));
        board.placePiece(new Knight(board, true), new Position(7, 6));
        board.placePiece(new Rook(board, true), new Position(7, 7));
        for (int c = 0; c < 8; c++) board.placePiece(new Pawn(board, true), new Position(6, c));

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