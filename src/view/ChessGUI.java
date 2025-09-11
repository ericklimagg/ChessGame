package view;

import controller.AIController;
import controller.Game;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import model.board.Position;
import model.pieces.Pawn;
import model.pieces.Piece;

/**
 * A classe principal da interface gráfica (View) para o jogo de xadrez.
 * Constrói e gerencia todos os componentes Swing, lida com a entrada do usuário (cliques, hover)
 * e atualiza a exibição com base nas mudanças de estado recebidas do Controller (Game).
 */
@SuppressWarnings("serial")
public class ChessGUI extends JFrame {

    // --- Constantes de Estilo da UI ---
    // Define um tema de cores consistente para todos os componentes da interface.
    private static final Color BG_COLOR = new Color(30, 31, 34);
    private static final Color PANEL_COLOR = new Color(40, 42, 46);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color BUTTON_COLOR = new Color(60, 62, 66);
    private static final Color BUTTON_HOVER_COLOR = new Color(80, 82, 86);
    private static final Color COMBO_BG_COLOR = new Color(60, 62, 66);

    private static final Color COR_CASA_CLARA = new Color(140, 140, 140); 
    private static final Color COR_CASA_ESCURA = new Color(80, 80, 80);   
    
    // Cores para feedback visual (seleção, movimentos legais, xeque, etc.).
    private static final Color COR_DESTAQUE_SELECIONADO = new Color(0, 122, 255); 
    private static final Color COR_DESTAQUE_LEGAL = new Color(0, 122, 255, 70); 
    private static final Color COR_DESTAQUE_HOVER = new Color(255, 255, 255, 50);
    private static final Color COR_DESTAQUE_ULTIMO = new Color(20, 180, 180, 100);
    private static final Color COR_DESTAQUE_XEQUE = new Color(255, 59, 48);

    private static final Border BORDA_SELECIONADO = new MatteBorder(3, 3, 3, 3, COR_DESTAQUE_SELECIONADO);

    // --- Referências ao Model e Controller ---
    private final Game game;
    private final AIController aiController;

    // --- Componentes da UI ---
    private final JPanel boardPanel;
    private final JButton[][] squares = new JButton[8][8];
    private JLabel statusLabel;
    private JTextArea historyArea;
    private final JPanel capturedWhitePanel;
    private final JPanel capturedBlackPanel;
    private JComboBox<String> difficultySelector;

    // --- Variáveis de Estado da UI ---
    private boolean playerIsWhite;      // Cor escolhida pelo jogador humano.
    private boolean isPvpMode;          // True para Humano vs Humano, False para vs IA.
    private boolean isBoardFlipped;     // True se o tabuleiro deve ser exibido na perspectiva das pretas.

    private Position selectedPos = null; // Posição da peça atualmente selecionada pelo jogador.
    private List<Position> legalMovesForSelected = new ArrayList<>(); // Movimentos legais para a peça selecionada.
    private Position lastFrom = null, lastTo = null; // Armazena o último movimento para destaque visual.
    private boolean aiThinking = false;   // Flag para bloquear a entrada do usuário enquanto a IA calcula.

    /**
     * Construtor da ChessGUI. Inicializa o jogo, configura a janela principal
     * e todos os componentes da interface.
     */
    public ChessGUI() {
        super("Jogo de Xadrez");
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {}

        this.game = new Game();
        this.aiController = new AIController();
        
        showGameSetupDialog(); // Exibe o diálogo de configuração inicial.

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_COLOR);

        // --- Configuração do Painel Principal do Jogo ---
        JPanel gamePanel = new JPanel(new BorderLayout(0, 10));
        gamePanel.setBackground(BG_COLOR);
        gamePanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        boardPanel = new JPanel(new GridLayout(8, 8));
        boardPanel.setPreferredSize(new Dimension(640, 640));
        boardPanel.setBorder(BorderFactory.createLineBorder(PANEL_COLOR, 2));

        // Inicializa os 64 botões que representam as casas do tabuleiro.
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position currentPos = new Position(r, c);
                squares[r][c] = new JButton();
                squares[r][c].setOpaque(true);
                squares[r][c].setBorder(null);
                squares[r][c].addActionListener(e -> onSquareClick(currentPos));
                
                // Adiciona listeners de mouse para o efeito de "hover".
                squares[r][c].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) { onSquareHover(currentPos, true); }
                    @Override
                    public void mouseExited(MouseEvent e) { onSquareHover(currentPos, false); }
                });
                boardPanel.add(squares[r][c]);
            }
        }

        // Painéis para exibir as peças capturadas.
        capturedWhitePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        capturedWhitePanel.setBackground(PANEL_COLOR);
        capturedBlackPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        capturedBlackPanel.setBackground(PANEL_COLOR);
        
        gamePanel.add(capturedWhitePanel, BorderLayout.NORTH);
        gamePanel.add(boardPanel, BorderLayout.CENTER);
        gamePanel.add(capturedBlackPanel, BorderLayout.SOUTH);

        JPanel sidePanel = createSidePanel();
        
        add(gamePanel, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        pack(); // Ajusta o tamanho da janela aos componentes.
        setResizable(false);
        setLocationRelativeTo(null); // Centraliza a janela na tela.
        setVisible(true);
        
        refreshAll(); // Atualiza a UI para o estado inicial do jogo.
        maybeTriggerAI(); // Verifica se a IA deve fazer o primeiro movimento.
    }

    /**
     * Cria e retorna o painel lateral que contém o histórico, status e opções do jogo.
     */
    private JPanel createSidePanel() {
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new GridBagLayout());
        sidePanel.setPreferredSize(new Dimension(280, 640));
        sidePanel.setBackground(BG_COLOR);
        sidePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;

        // Título
        JLabel titleLabel = new JLabel("XADREZ", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        titleLabel.setForeground(TEXT_COLOR);
        gbc.gridy = 0;
        gbc.weighty = 0.1;
        sidePanel.add(titleLabel, gbc);

        // Botões de Opção (Novo Jogo, Desfazer)
        JPanel optionsPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        optionsPanel.setOpaque(false);
        JButton newGameButton = createStyledButton("Novo Jogo");
        newGameButton.addActionListener(e -> doNewGame());
        JButton undoButton = createStyledButton("Desfazer");
        undoButton.addActionListener(e -> doUndo());
        optionsPanel.add(newGameButton);
        optionsPanel.add(undoButton);
        gbc.gridy = 1;
        sidePanel.add(optionsPanel, gbc);

        // Seletor de Dificuldade da IA
        JPanel aiPanel = new JPanel(new BorderLayout(10, 0));
        aiPanel.setOpaque(false);
        JLabel aiLabel = new JLabel("Dificuldade IA:");
        aiLabel.setForeground(TEXT_COLOR);
        aiPanel.add(aiLabel, BorderLayout.WEST);
        
        String[] difficulties = {"Burro", "Fácil", "Médio", "Difícil", "Expert"};
        difficultySelector = new JComboBox<>(difficulties);
        difficultySelector.setSelectedIndex(2); 
        difficultySelector.setBackground(COMBO_BG_COLOR);
        difficultySelector.setForeground(TEXT_COLOR);
        aiPanel.add(difficultySelector, BorderLayout.CENTER);
        gbc.gridy = 2;
        sidePanel.add(aiPanel, gbc);

        // Área de Histórico de Movimentos
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        historyArea.setBackground(PANEL_COLOR);
        historyArea.setForeground(TEXT_COLOR);
        historyArea.setBorder(new EmptyBorder(5, 5, 5, 5));
        JScrollPane historyScroll = new JScrollPane(historyArea);
        historyScroll.setBorder(BorderFactory.createLineBorder(BUTTON_COLOR));
        gbc.gridy = 3;
        gbc.weighty = 0.8;
        gbc.fill = GridBagConstraints.BOTH;
        sidePanel.add(historyScroll, gbc);

        // Rótulo de Status
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        statusLabel.setForeground(TEXT_COLOR);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(PANEL_COLOR);
        gbc.gridy = 4;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        sidePanel.add(statusLabel, gbc);
        
        return sidePanel;
    }

    /**
     * Cria um JButton com um estilo visual padronizado para a aplicação.
     */
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(BUTTON_COLOR);
        button.setForeground(TEXT_COLOR);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, 10, 10, 10));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent evt) { button.setBackground(BUTTON_HOVER_COLOR); }
            public void mouseExited(MouseEvent evt) { button.setBackground(BUTTON_COLOR); }
        });
        return button;
    }
    
    /**
     * Exibe um diálogo modal para o jogador configurar o modo de jogo (cor, vs IA ou vs Humano).
     */
    private void showGameSetupDialog() {
        // Personaliza a aparência do JOptionPane para combinar com o tema da UI.
        UIManager.put("Panel.background", BG_COLOR);
        UIManager.put("TitledBorder.titleColor", TEXT_COLOR);
        UIManager.put("RadioButton.background", BG_COLOR);
        UIManager.put("RadioButton.foreground", TEXT_COLOR);
        UIManager.put("Button.background", BUTTON_COLOR);
        UIManager.put("Button.foreground", TEXT_COLOR);

        JRadioButton whiteRadio = new JRadioButton("Brancas", true);
        JRadioButton blackRadio = new JRadioButton("Pretas");
        ButtonGroup colorGroup = new ButtonGroup();
        colorGroup.add(whiteRadio); colorGroup.add(blackRadio);
        JPanel colorPanel = new JPanel();
        colorPanel.setBorder(BorderFactory.createTitledBorder("Escolha sua cor:"));
        colorPanel.add(whiteRadio); colorPanel.add(blackRadio);

        JRadioButton vsAiRadio = new JRadioButton("Contra IA", true);
        JRadioButton vsHumanRadio = new JRadioButton("Humano vs. Humano");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(vsAiRadio); modeGroup.add(vsHumanRadio);
        JPanel modePanel = new JPanel();
        modePanel.setBorder(BorderFactory.createTitledBorder("Modo de jogo:"));
        modePanel.add(vsAiRadio); modePanel.add(vsHumanRadio);

        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 5));
        panel.add(colorPanel); panel.add(modePanel);

        int result = JOptionPane.showConfirmDialog(null, panel, "Configurar Novo Jogo",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            this.playerIsWhite = whiteRadio.isSelected();
            this.isPvpMode = vsHumanRadio.isSelected();
        } else {
            System.exit(0); // Fecha a aplicação se o diálogo for cancelado.
        }

        // Define se o tabuleiro deve ser invertido com base na cor escolhida pelo jogador.
        this.isBoardFlipped = !this.playerIsWhite;
        UIManager.put("Panel.background", null); // Reseta a cor do UIManager.
    }

    /**
     * Ação executada ao clicar no botão "Novo Jogo".
     */
    private void doNewGame() {
        showGameSetupDialog();
        game.newGame();
        // Reseta o estado da UI para um novo jogo.
        selectedPos = null;
        legalMovesForSelected.clear();
        lastFrom = lastTo = null;
        aiThinking = false;
        refreshAll();
        maybeTriggerAI();
    }

    /**
     * Ação executada ao clicar no botão "Desfazer".
     */
    private void doUndo() {
        if (game.isGameOver() || aiThinking) return;
        game.undoMove();
        // No modo contra IA, desfaz a jogada da IA também.
        if (!isPvpMode) {
            game.undoMove();
        }
        // Limpa os destaques e o estado de seleção.
        lastFrom = lastTo = null;
        selectedPos = null;
        legalMovesForSelected.clear();
        aiThinking = false;
        refreshAll();
    }

    /**
     * Lógica central que trata o clique do jogador em uma casa do tabuleiro.
     * Gerencia a seleção, desseleção e tentativa de movimento de peças.
     * @param guiPos A posição na grade da GUI que foi clicada.
     */
    private void onSquareClick(Position guiPos) {
        if (game.isGameOver() || aiThinking) return;

        // Converte a posição da GUI para a posição do modelo, caso o tabuleiro esteja invertido.
        Position modelPos = isBoardFlipped ? new Position(7 - guiPos.getRow(), 7 - guiPos.getColumn()) : guiPos;
        
        // Impede que o jogador jogue fora da sua vez.
        if (!isPvpMode && game.whiteToMove() != playerIsWhite) return;

        Piece clickedPiece = game.board().get(modelPos);

        if (selectedPos == null) {
            // Se nenhuma peça está selecionada, tenta selecionar a peça clicada.
            if (clickedPiece != null && clickedPiece.isWhite() == game.whiteToMove()) {
                selectedPos = modelPos;
                legalMovesForSelected = game.legalMovesFrom(selectedPos);
            }
        } else {
            // Se uma peça já está selecionada, verifica se o clique foi em um movimento legal.
            if (legalMovesForSelected.contains(modelPos)) {
                Character promo = null;
                // Se for um movimento de promoção, abre o diálogo de escolha.
                if (game.board().get(selectedPos) instanceof Pawn && game.isPromotion(selectedPos, modelPos)) {
                    promo = askPromotion();
                }
                boolean wasCapture = game.board().get(modelPos) != null;
                
                // Envia o movimento para o Controller (Game).
                if (game.move(selectedPos, modelPos, promo)) {
                    lastFrom = selectedPos; // Salva o último movimento para destaque.
                    lastTo = modelPos;
                    playSoundForMove(wasCapture, game.inCheck(game.whiteToMove()));
                }
                
                // Limpa o estado de seleção e atualiza a UI.
                selectedPos = null;
                legalMovesForSelected.clear();
                refreshAll();
                maybeAnnounceEnd();
                maybeTriggerAI();

            } else if (clickedPiece != null && clickedPiece.isWhite() == game.whiteToMove()) {
                // Se o clique foi em outra peça do mesmo jogador, muda a seleção.
                selectedPos = modelPos;
                legalMovesForSelected = game.legalMovesFrom(selectedPos);
            } else {
                // Se o clique foi em uma casa vazia ou peça inimiga (não legal), deseleciona.
                selectedPos = null;
                legalMovesForSelected.clear();
            }
        }
        refreshBoard(); // Atualiza o tabuleiro para refletir a mudança de seleção/movimento.
    }
    
    /**
     * Lida com o evento de passar o mouse sobre uma casa para mostrar os movimentos legais.
     * @param guiPos A posição da casa.
     * @param isEntering True se o mouse está entrando na casa, False se está saindo.
     */
    private void onSquareHover(Position guiPos, boolean isEntering) {
        if (game.isGameOver() || aiThinking || selectedPos != null) return;
        Position modelPos = isBoardFlipped ? new Position(7 - guiPos.getRow(), 7 - guiPos.getColumn()) : guiPos;
        Piece pieceOnSquare = game.board().get(modelPos);
        if (isEntering && pieceOnSquare != null && pieceOnSquare.isWhite() == game.whiteToMove()) {
            // Se o mouse entra em uma casa com uma peça do jogador da vez, destaca seus movimentos.
            List<Position> moves = game.legalMovesFrom(modelPos);
            for (Position move : moves) {
                Position targetGuiPos = isBoardFlipped ? new Position(7 - move.getRow(), 7 - move.getColumn()) : move;
                squares[targetGuiPos.getRow()][targetGuiPos.getColumn()].setBackground(blend(getSquareBaseColor(targetGuiPos), COR_DESTAQUE_HOVER));
            }
        } else {
            // Se o mouse sai, remove os destaques.
            refreshBoard();
        }
    }

    /**
     * Exibe um diálogo para o jogador escolher a peça para a qual um peão será promovido.
     */
    private Character askPromotion() {
        String[] opts = {"Rainha", "Torre", "Bispo", "Cavalo"};
        int choice = JOptionPane.showOptionDialog(this, "Promover peão para:", "Promoção",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0]);
        return switch (choice) { case 1 -> 'R'; case 2 -> 'B'; case 3 -> 'N'; default -> 'Q'; };
    }

    /**
     * Verifica se é a vez da IA jogar e, em caso afirmativo, inicia a busca pelo melhor movimento.
     */
    private void maybeTriggerAI() {
        if (game.isGameOver() || isPvpMode || aiThinking) return;
        boolean isAiTurn = game.whiteToMove() != playerIsWhite;
        if (isAiTurn) {
            aiThinking = true;
            statusLabel.setText("PC (" + (playerIsWhite ? "Pretas" : "Brancas") + ") está a pensar...");

            // Define o tempo de busca da IA com base na dificuldade selecionada.
            int difficultyIndex = difficultySelector.getSelectedIndex();
            long timeLimit = switch (difficultyIndex) {
                case 0 -> 100L;  // Burro: 0.1 segundo
                case 1 -> 1000L; // Fácil: 1 segundo
                case 2 -> 3000L; // Médio: 3 segundos
                case 3 -> 5000L; // Difícil: 5 segundos
                case 4 -> 7000L; // Expert: 7 segundos
                default -> 3000L;
            };

            // Chama o AIController para encontrar o melhor movimento de forma assíncrona.
            aiController.findBestMove(game, timeLimit, difficultyIndex, (bestMove) -> {
                if (bestMove != null) {
                    boolean wasCapture = game.board().get(bestMove.to) != null;
                    game.move(bestMove.from, bestMove.to, null);
                    lastFrom = bestMove.from;
                    lastTo = bestMove.to;
                    playSoundForMove(wasCapture, game.inCheck(game.whiteToMove()));
                }
                aiThinking = false;
                refreshAll();
                maybeAnnounceEnd();
            });
        }
    }
    
    /**
     * Toca o efeito sonoro apropriado para um movimento (normal, captura ou xeque).
     */
    private void playSoundForMove(boolean isCapture, boolean isCheck) {
        if (isCheck) SoundUtil.playSound("check.wav");
        else if (isCapture) SoundUtil.playSound("captura.wav");
        else SoundUtil.playSound("move.wav");
    }

    /**
     * Método central para atualizar toda a interface gráfica.
     * Chama todos os outros métodos de atualização.
     */
    private void refreshAll() {
        refreshBoard();
        refreshHistory();
        refreshStatus();
        refreshCapturedPieces();
    }
    
    /**
     * Redesenha o tabuleiro, atualizando os ícones das peças e os destaques visuais.
     */
    private void refreshBoard() {
        Position kingInCheck = null;
        if(game.inCheck(true)) kingInCheck = game.findKing(true);
        else if(game.inCheck(false)) kingInCheck = game.findKing(false);

        int iconSize = Math.max(24, boardPanel.getWidth() / 10);

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position guiPos = new Position(r, c);
                JButton b = squares[r][c];
                b.setBackground(getSquareBaseColor(guiPos));
                b.setBorder(null);

                Position modelPos = isBoardFlipped ? new Position(7 - r, 7 - c) : guiPos;
                
                // Aplica destaques para xeque, último movimento, seleção e movimentos legais.
                if (modelPos.equals(kingInCheck)) {
                    b.setBackground(COR_DESTAQUE_XEQUE);
                } else if (modelPos.equals(lastFrom) || modelPos.equals(lastTo)) {
                    b.setBackground(blend(b.getBackground(), COR_DESTAQUE_ULTIMO));
                }

                if (modelPos.equals(selectedPos)) {
                    b.setBorder(BORDA_SELECIONADO);
                } else if (legalMovesForSelected.contains(modelPos)) {
                    b.setBackground(blend(b.getBackground(), COR_DESTAQUE_LEGAL));
                }
                
                // Define o ícone da peça na casa.
                Piece p = game.board().get(modelPos);
                b.setIcon(p != null ? ImageUtil.getPieceIcon(p.isWhite(), p.getSymbol(), iconSize) : null);
            }
        }
    }
    
    // Retorna a cor base (clara ou escura) de uma casa.
    private Color getSquareBaseColor(Position guiPos) {
        return (guiPos.getRow() + guiPos.getColumn()) % 2 == 0 ? COR_CASA_CLARA : COR_CASA_ESCURA;
    }

    /**
     * Atualiza o texto do rótulo de status (vez do jogador, xeque, etc.).
     */
    private void refreshStatus() {
        if(game.isGameOver()) {
            statusLabel.setText(game.getGameEndMessage());
            return;
        }
        
        String side = game.whiteToMove() ? "Brancas" : "Pretas";
        String checkStatus = game.inCheck(game.whiteToMove()) ? " — Xeque!" : "";
        if (aiThinking) {
            String aiColor = playerIsWhite ? "Pretas" : "Brancas";
            statusLabel.setText("PC (" + aiColor + ") está a pensar...");
        } else {
            statusLabel.setText("Vez das " + side + checkStatus);
        }
    }
    
    /**
     * Atualiza a área de texto com o histórico de movimentos do jogo.
     */
    private void refreshHistory() {
        StringBuilder sb = new StringBuilder();
        List<String> hist = game.history();
        for (int i = 0; i < hist.size(); i++) {
            if (i % 2 == 0) sb.append((i / 2) + 1).append(". ");
            sb.append(hist.get(i)).append("  ");
            if (i % 2 == 1) sb.append("\n");
        }
        historyArea.setText(sb.toString());
        // Rola automaticamente para o final do histórico.
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
    }
    
    /**
     * Atualiza os painéis que exibem as peças capturadas por cada jogador.
     */
    private void refreshCapturedPieces() {
        capturedWhitePanel.removeAll();
        capturedBlackPanel.removeAll();
        
        JLabel whiteLabel = new JLabel("Capturadas (Brancas): ");
        whiteLabel.setForeground(TEXT_COLOR);
        capturedWhitePanel.add(whiteLabel);

        JLabel blackLabel = new JLabel("Capturadas (Pretas): ");
        blackLabel.setForeground(TEXT_COLOR);
        capturedBlackPanel.add(blackLabel);
        
        List<Piece> allWhite = game.board().getPieces(true);
        List<Piece> allBlack = game.board().getPieces(false);

        addCapturedIcons(capturedWhitePanel, false, allBlack);
        addCapturedIcons(capturedBlackPanel, true, allWhite);

        capturedWhitePanel.revalidate(); capturedWhitePanel.repaint();
        capturedBlackPanel.revalidate(); capturedBlackPanel.repaint();
    }
    
    /**
     * Lógica auxiliar para calcular e adicionar os ícones das peças capturadas a um painel.
     */
    private void addCapturedIcons(JPanel panel, boolean capturedAreWhite, List<Piece> remainingPieces) {
        List<String> remainingSymbols = new ArrayList<>();
        for (Piece p : remainingPieces) remainingSymbols.add(p.getSymbol());

        String[] allPieceSymbols = {"P", "N", "B", "R", "Q"};
        int[] initialCounts = {8, 2, 2, 2, 1}; // Contagem inicial de cada tipo de peça.

        for (int i = 0; i < allPieceSymbols.length; i++) {
            String symbol = allPieceSymbols[i];
            // O número de peças capturadas é o inicial menos o que restou.
            int countCaptured = initialCounts[i] - (int)remainingSymbols.stream().filter(s -> s.equals(symbol)).count();
            for (int j = 0; j < countCaptured; j++) {
                ImageIcon icon = ImageUtil.getPieceIcon(capturedAreWhite, symbol.charAt(0), 20);
                if (icon != null) panel.add(new JLabel(icon));
            }
        }
    }
    
    /**
     * Exibe uma mensagem de diálogo quando o jogo termina.
     */
    private void maybeAnnounceEnd() {
        if (!game.isGameOver()) return;
        SoundUtil.playSound("fim.wav");
        // Usa invokeLater para garantir que o diálogo seja exibido na thread de eventos do Swing.
        SwingUtilities.invokeLater(() -> 
            JOptionPane.showMessageDialog(this, game.getGameEndMessage(), "Fim de Jogo", JOptionPane.INFORMATION_MESSAGE)
        );
    }

    /**
     * Mistura duas cores. Usado para criar os efeitos de destaque translúcidos.
     */
    private static Color blend(Color c1, Color c2) {
        float alpha = c2.getAlpha() / 255.0f;
        float invAlpha = 1.0f - alpha;
        float r = (c1.getRed() * invAlpha) + (c2.getRed() * alpha);
        float g = (c1.getGreen() * invAlpha) + (c2.getGreen() * alpha);
        float b = (c1.getBlue() * invAlpha) + (c2.getBlue() * alpha);
        return new Color((int) r, (int) g, (int) b);
    }

    /**
     * O ponto de entrada da aplicação.
     */
    public static void main(String[] args) {
        // Garante que a GUI seja criada e executada na Event Dispatch Thread (EDT) do Swing.
        SwingUtilities.invokeLater(ChessGUI::new);
    }
}