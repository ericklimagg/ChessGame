package view;

import controller.AIController;
import controller.Game;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.MatteBorder;
import model.board.Position;
import model.pieces.Pawn;
import model.pieces.Piece;

public class ChessGUI extends JFrame {
    private static final long serialVersionUID = 1L;

    // --- NOVO ESTILO: Cores Mais Elegantes ---
    private static final Color COR_CASA_CLARA = new Color(135,206,235); // Branco
    private static final Color COR_CASA_ESCURA = new Color(240,248,255);  // Azul
    
    private static final Color COR_DESTAQUE_SELECIONADO = new Color(28,28,28); // Cinza Escuro
    private static final Color COR_DESTAQUE_LEGAL = new Color(79, 79, 79);     // Verde Suave
    private static final Color COR_DESTAQUE_ULTIMO = new Color(220, 200, 100, 130);    // Ouro Envelhecido
    private static final Color COR_DESTAQUE_XEQUE = new Color(245, 39, 39);       // Vermelho Discreto

    private static final Border BORDA_SELECIONADO = new MatteBorder(3, 3, 3, 3, COR_DESTAQUE_SELECIONADO);
    private static final Border BORDA_LEGAL = new MatteBorder(3, 3, 3, 3, COR_DESTAQUE_LEGAL);

    private final Game game;
    private final AIController aiController;

    private final JPanel boardPanel;
    private final JButton[][] squares = new JButton[8][8];
    private final JLabel statusLabel;
    private final JTextArea historyArea;
    private final JPanel capturedWhitePanel;
    private final JPanel capturedBlackPanel;
    private JSpinner depthSpinner;

    // --- Variáveis de Configuração do Jogo ---
    private boolean playerIsWhite;
    private boolean isPvpMode;
    private boolean isBoardFlipped;

    private Position selectedPos = null;
    private List<Position> legalMovesForSelected = new ArrayList<>();
    private Position lastFrom = null, lastTo = null;
    private boolean aiThinking = false;

    public ChessGUI() {
        super("Jogo de Xadrez");
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {}

        this.game = new Game();
        this.aiController = new AIController();
        
        showGameSetupDialog();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setJMenuBar(buildMenuBar());

        boardPanel = new JPanel(new GridLayout(8, 8));
        boardPanel.setPreferredSize(new Dimension(600, 600));

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                squares[r][c] = new JButton();
                squares[r][c].setOpaque(true);
                squares[r][c].setBorderPainted(true);
                final int finalR = r;
                final int finalC = c;
                squares[r][c].addActionListener(e -> onSquareClick(new Position(finalR, finalC)));
                boardPanel.add(squares[r][c]);
            }
        }

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Dimension capturedPanelSize = new Dimension(600, 40);
        capturedWhitePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        capturedWhitePanel.setPreferredSize(capturedPanelSize);
        capturedBlackPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        capturedBlackPanel.setPreferredSize(capturedPanelSize);
        
        mainPanel.add(capturedWhitePanel, BorderLayout.NORTH);
        mainPanel.add(boardPanel, BorderLayout.CENTER);
        mainPanel.add(capturedBlackPanel, BorderLayout.SOUTH);

        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Georgia", Font.PLAIN, 16));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        historyArea = new JTextArea(15, 25);
        historyArea.setEditable(false);
        historyArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        JScrollPane historyScroll = new JScrollPane(historyArea);
        
        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
        rightPanel.add(new JLabel("Histórico:"), BorderLayout.NORTH);
        rightPanel.add(historyScroll, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        add(rightPanel, BorderLayout.EAST);

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);
        
        refreshAll();
        maybeTriggerAI();
    }
    
    private void showGameSetupDialog() {
        JRadioButton whiteRadio = new JRadioButton("Brancas", true);
        JRadioButton blackRadio = new JRadioButton("Pretas");
        ButtonGroup colorGroup = new ButtonGroup();
        colorGroup.add(whiteRadio);
        colorGroup.add(blackRadio);
        JPanel colorPanel = new JPanel();
        colorPanel.setBorder(BorderFactory.createTitledBorder("Escolha sua cor:"));
        colorPanel.add(whiteRadio);
        colorPanel.add(blackRadio);

        JRadioButton vsAiRadio = new JRadioButton("Contra IA", true);
        JRadioButton vsHumanRadio = new JRadioButton("Humano vs. Humano");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(vsAiRadio);
        modeGroup.add(vsHumanRadio);
        JPanel modePanel = new JPanel();
        modePanel.setBorder(BorderFactory.createTitledBorder("Modo de jogo:"));
        modePanel.add(vsAiRadio);
        modePanel.add(vsHumanRadio);

        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.add(colorPanel);
        panel.add(modePanel);

        int result = JOptionPane.showConfirmDialog(null, panel, "Configurar Novo Jogo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            this.playerIsWhite = whiteRadio.isSelected();
            this.isPvpMode = vsHumanRadio.isSelected();
        } else {
            System.exit(0);
        }

        this.isBoardFlipped = !this.playerIsWhite;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu gameMenu = new JMenu("Jogo");

        JMenuItem newGameItem = new JMenuItem("Novo Jogo");
        newGameItem.addActionListener(e -> doNewGame());
        
        depthSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 5, 1));
        
        gameMenu.add(newGameItem);
        gameMenu.addSeparator();
        gameMenu.add(new JLabel("Profundidade IA: "));
        gameMenu.add(depthSpinner);
        
        mb.add(gameMenu);
        return mb;
    }

    private void doNewGame() {
        showGameSetupDialog();
        
        game.newGame();
        selectedPos = null;
        legalMovesForSelected.clear();
        lastFrom = lastTo = null;
        aiThinking = false;
        refreshAll();
        maybeTriggerAI();
    }

    private void onSquareClick(Position guiPos) {
        if (game.isGameOver() || aiThinking) return;

        Position modelPos = isBoardFlipped ? new Position(7 - guiPos.getRow(), 7 - guiPos.getColumn()) : guiPos;

        if (!isPvpMode) {
            if (game.whiteToMove() != playerIsWhite) return;
        }

        Piece clickedPiece = game.board().get(modelPos);

        if (selectedPos == null) {
            if (clickedPiece != null && clickedPiece.isWhite() == game.whiteToMove()) {
                selectedPos = modelPos;
                legalMovesForSelected = game.legalMovesFrom(selectedPos);
            }
        } else {
            if (legalMovesForSelected.contains(modelPos)) {
                Character promo = null;
                if (game.board().get(selectedPos) instanceof Pawn && game.isPromotion(selectedPos, modelPos)) {
                    promo = askPromotion();
                }

                boolean wasCapture = game.board().get(modelPos) != null;
                boolean isValidMove = game.move(selectedPos, modelPos, promo);
                
                if (isValidMove) {
                    lastFrom = selectedPos;
                    lastTo = modelPos;
                    playSoundForMove(wasCapture, game.inCheck(game.whiteToMove()));
                }

                selectedPos = null;
                legalMovesForSelected.clear();
                
                refreshAll();
                maybeAnnounceEnd();
                maybeTriggerAI();
                
            } else if (clickedPiece != null && clickedPiece.isWhite() == game.whiteToMove()) {
                selectedPos = modelPos;
                legalMovesForSelected = game.legalMovesFrom(selectedPos);
            } else {
                selectedPos = null;
                legalMovesForSelected.clear();
            }
        }
        refreshBoard();
    }

    private Character askPromotion() {
        String[] opts = {"Rainha", "Torre", "Bispo", "Cavalo"};
        int choice = JOptionPane.showOptionDialog(this, "Promover peão para:", "Promoção",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
        return switch (choice) { case 1 -> 'R'; case 2 -> 'B'; case 3 -> 'N'; default -> 'Q'; };
    }

    private void maybeTriggerAI() {
        if (game.isGameOver() || isPvpMode) return;
        
        boolean isAiTurn = game.whiteToMove() != playerIsWhite;

        if (isAiTurn) {
            aiThinking = true;
            String aiColor = playerIsWhite ? "Pretas" : "Brancas";
            statusLabel.setText("Vez das " + aiColor + " — PC pensando...");
            
            Timer timer = new Timer(500, (e) -> {
                int depth = (Integer) depthSpinner.getValue();
                aiController.findBestMove(game, depth, (bestMove) -> {
                    if (bestMove != null) {
                        boolean wasCapture = game.board().get(bestMove.to) != null;
                        game.move(bestMove.from, bestMove.to, 'Q');
                        lastFrom = bestMove.from;
                        lastTo = bestMove.to;
                        playSoundForMove(wasCapture, game.inCheck(game.whiteToMove()));
                    }
                    aiThinking = false;
                    refreshAll();
                    maybeAnnounceEnd();
                });
            });
            timer.setRepeats(false);
            timer.start();
        }
    }
    
    private void playSoundForMove(boolean isCapture, boolean isCheck) {
        if (isCheck) SoundUtil.playSound("check.wav");
        else if (isCapture) SoundUtil.playSound("captura.wav");
        else SoundUtil.playSound("move.wav");
    }

    private void refreshAll() {
        refreshBoard();
        refreshHistory();
        refreshStatus();
        refreshCapturedPieces();
    }
    
    private void refreshBoard() {
        Position kingInCheck = game.inCheck(game.whiteToMove()) ? game.findKing(game.whiteToMove()) : null;
        int iconSize = Math.max(24, boardPanel.getWidth() / 10);

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                JButton b = squares[r][c];
                b.setBackground((r + c) % 2 == 0 ? COR_CASA_CLARA : COR_CASA_ESCURA);
                b.setBorder(null);

                Position modelPos = isBoardFlipped ? new Position(7 - r, 7 - c) : new Position(r, c);
                
                if (modelPos.equals(kingInCheck)) {
                    b.setBackground(COR_DESTAQUE_XEQUE);
                } else if (modelPos.equals(lastFrom) || modelPos.equals(lastTo)) {
                    b.setBackground(blend(b.getBackground(), COR_DESTAQUE_ULTIMO));
                }

                if (modelPos.equals(selectedPos)) {
                    b.setBorder(BORDA_SELECIONADO);
                } else if (legalMovesForSelected.contains(modelPos)) {
                    b.setBorder(BORDA_LEGAL);
                }
                
                Piece p = game.board().get(modelPos);
                b.setIcon(p != null ? ImageUtil.getPieceIcon(p.isWhite(), p.getSymbol(), iconSize) : null);
            }
        }
    }
    
    private void refreshStatus() {
        String side = game.whiteToMove() ? "Brancas" : "Pretas";
        String checkStatus = game.inCheck(game.whiteToMove()) ? " — Xeque!" : "";
        if (aiThinking) {
            String aiColor = playerIsWhite ? "Pretas" : "Brancas";
            statusLabel.setText("Vez das " + aiColor + " — PC pensando...");
        } else {
            statusLabel.setText("Vez das " + side + checkStatus);
        }
    }
    
    private void refreshHistory() {
        StringBuilder sb = new StringBuilder();
        List<String> hist = game.history();
        for (int i = 0; i < hist.size(); i++) {
            if (i % 2 == 0) sb.append((i / 2) + 1).append(". ");
            sb.append(hist.get(i)).append("  ");
            if (i % 2 == 1) sb.append("\n");
        }
        historyArea.setText(sb.toString());
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
    }
    
    private void refreshCapturedPieces() {
        capturedWhitePanel.removeAll();
        capturedBlackPanel.removeAll();
        
        List<Piece> allWhite = game.board().getPieces(true);
        List<Piece> allBlack = game.board().getPieces(false);

        int capturedBlackValue = calculateTotalMaterial() - getMaterialValue(allBlack);
        if (capturedBlackValue > 0) {
            capturedWhitePanel.add(new JLabel("Capturadas pelas Brancas: "));
            addCapturedIcons(capturedWhitePanel, false, allBlack); // Icons for white side (pieces captured FROM black)
        }

        int capturedWhiteValue = calculateTotalMaterial() - getMaterialValue(allWhite);
        if (capturedWhiteValue > 0) {
            capturedBlackPanel.add(new JLabel("Capturadas pelas Pretas: "));
            addCapturedIcons(capturedBlackPanel, true, allWhite); // Icons for black side (pieces captured FROM white)
        }

        capturedWhitePanel.revalidate();
        capturedWhitePanel.repaint();
        capturedBlackPanel.revalidate();
        capturedBlackPanel.repaint();
    }
    
    // Helper para adicionar os ícones de peças capturadas
    private void addCapturedIcons(JPanel panel, boolean capturedAreWhite, List<Piece> remainingPieces) {
        // Obter os símbolos das peças que *ainda restam* no tabuleiro
        List<String> remainingSymbols = new ArrayList<>();
        for (Piece p : remainingPieces) {
            remainingSymbols.add(p.getSymbol());
        }

        String[] allPieceSymbols = {"P", "N", "B", "R", "Q"}; // Peões, Cavalos, Bispos, Torres, Rainhas
        int[] initialCounts = {8, 2, 2, 2, 1}; // Contagem inicial de cada peça

        for (int i = 0; i < allPieceSymbols.length; i++) {
            String symbol = allPieceSymbols[i];
            int initialCount = initialCounts[i];
            
            // Contar quantos desse tipo restam
            long countRemaining = remainingSymbols.stream().filter(s -> s.equals(symbol)).count();
            
            // O número de peças capturadas é o inicial menos o que restou
            int countCaptured = initialCount - (int)countRemaining;
            
            for (int j = 0; j < countCaptured; j++) {
                ImageIcon icon = ImageUtil.getPieceIcon(capturedAreWhite, symbol.charAt(0), 20); // Usar a cor correta da peça capturada
                if (icon != null) panel.add(new JLabel(icon));
            }
        }
    }
    
    private int getMaterialValue(List<Piece> pieces) {
        int value = 0;
        for (Piece p : pieces) {
            value += switch (p.getSymbol()) {
                case "P" -> 1; case "N", "B" -> 3;
                case "R" -> 5; case "Q" -> 9;
                default -> 0; // Reis não contribuem para o valor material
            };
        }
        return value;
    }

    private int calculateTotalMaterial() {
        return 1*8 + 3*2 + 3*2 + 5*2 + 9; // Valor inicial total de material (sem reis)
    }

    private void maybeAnnounceEnd() {
        if (!game.isGameOver()) return;
        SoundUtil.playSound("fim.wav");
        SwingUtilities.invokeLater(() -> 
            JOptionPane.showMessageDialog(this, game.getGameEndMessage(), "Fim de Jogo", JOptionPane.INFORMATION_MESSAGE)
        );
    }

    private static Color blend(Color c1, Color c2) {
        float r = (c1.getRed() * 0.5f) + (c2.getRed() * 0.5f);
        float g = (c1.getGreen() * 0.5f) + (c2.getGreen() * 0.5f);
        float b = (c1.getBlue() * 0.5f) + (c2.getBlue() * 0.5f);
        return new Color((int) r, (int) g, (int) b);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChessGUI::new);
    }
}