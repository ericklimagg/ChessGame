package view;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Classe utilitária para carregar, redimensionar e gerenciar em cache os ícones das peças.
 * Otimiza o desempenho evitando o carregamento e redimensionamento repetido de imagens
 * através de um cache LRU (Least Recently Used).
 * Também fornece um mecanismo de fallback, gerando ícones "placeholder" caso os
 * arquivos de imagem não sejam encontrados.
 */
public final class ImageUtil {

    // Caminhos padrão para busca dos recursos de imagem.
    private static final String CLASSPATH_PREFIX = "/resources/";
    private static final String FILE_PREFIX = "resources" + File.separator;

    // Define a capacidade máxima do cache para evitar consumo excessivo de memória.
    private static final int MAX_CACHE = 256;

    // Implementação de um cache LRU (Least Recently Used) para armazenar os ícones já processados.
    // Quando o cache atinge a capacidade máxima, o item menos utilizado recentemente é removido.
    // A estrutura é sincronizada para garantir a segurança em ambientes com múltiplas threads.
    private static final Map<String, ImageIcon> ICON_CACHE =
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ImageIcon> eldest) {
                    return size() > MAX_CACHE;
                }
            };

    // Construtor privado para impedir a instanciação de uma classe utilitária.
    private ImageUtil() {}

    /** * Limpa o cache de ícones. Pode ser útil para liberar memória ou recarregar imagens.
     */
    public static synchronized void clearCache() {
        ICON_CACHE.clear();
    }

    /** * Pré-carrega uma lista de imagens em um tamanho específico para o cache.
     * Útil para carregar os ícones mais comuns durante a inicialização da aplicação.
     * @param size O tamanho (largura e altura) para pré-carregar os ícones.
     * @param filenames Nomes dos arquivos de imagem a serem pré-carregados.
     */
    public static void preload(int size, String... filenames) {
        if (filenames == null) return;
        for (String f : filenames) {
            try { getIcon(f, size); } catch (Exception ignored) {}
        }
    }

    /**
     * Monta o nome do arquivo da peça (ex: "wK.png") e o recupera do cache ou o carrega.
     * Se a imagem não for encontrada, um ícone de placeholder é gerado dinamicamente.
     *
     * @param isWhite Cor da peça (true para branca, false para preta).
     * @param pieceChar Símbolo da peça ('K', 'Q', 'R', 'B', 'N', 'P').
     * @param size Dimensão (largura e altura) desejada para o ícone.
     * @return Um ImageIcon da peça no tamanho especificado.
     */
    public static ImageIcon getPieceIcon(boolean isWhite, char pieceChar, int size) {
        char p = Character.toUpperCase(pieceChar);
        if ("KQRBNP".indexOf(p) < 0) {
            return placeholderIcon('?', isWhite, sanitizeSize(size));
        }
        String prefix = isWhite ? "w" : "b";
        String filename = prefix + p + ".png";
        ImageIcon icon = getIcon(filename, size);
        if (icon == null) {
            // Fallback para um ícone gerado programaticamente se o arquivo não for encontrado.
            return placeholderIcon(p, isWhite, sanitizeSize(size));
        }
        return icon;
    }

    /** * Sobrecarga do método getPieceIcon para conveniência, aceitando o símbolo da peça como String.
     */
    public static ImageIcon getPieceIcon(boolean isWhite, String pieceSymbol, int size) {
        Objects.requireNonNull(pieceSymbol, "pieceSymbol");
        char ch = pieceSymbol.isEmpty() ? '?' : pieceSymbol.charAt(0);
        return getPieceIcon(isWhite, ch, size);
    }

    /**
     * Carrega um ícone a partir de um nome de arquivo, redimensiona-o com alta qualidade e o armazena em cache.
     * Primeiro, verifica o cache. Se não encontrar, carrega a imagem, a redimensiona e a adiciona ao cache.
     *
     * @param filename O nome do arquivo da imagem (ex: "wK.png").
     * @param size O tamanho desejado em pixels.
     * @return O ImageIcon redimensionado, ou null se a imagem não for encontrada.
     */
    public static ImageIcon getIcon(String filename, int size) {
        size = sanitizeSize(size);
        String cacheKey = filename + "|" + size;
        
        // Bloco sincronizado para acesso seguro ao cache a partir de múltiplas threads.
        synchronized (ImageUtil.class) {
            ImageIcon cached = ICON_CACHE.get(cacheKey);
            if (cached != null) return cached;
        }

        BufferedImage img = loadBuffered(filename);
        if (img == null) return null;

        BufferedImage scaled = scaleImageHQ(img, size, size);
        ImageIcon icon = new ImageIcon(scaled);

        // Bloco sincronizado para inserção segura no cache.
        synchronized (ImageUtil.class) {
            ICON_CACHE.put(cacheKey, icon);
        }
        return icon;
    }

    /**
     * Tenta carregar uma imagem como um BufferedImage de várias fontes, em ordem de prioridade:
     * 1) Do classpath, dentro da pasta /resources/.
     * 2) Do classpath, na raiz.
     * 3) Do sistema de arquivos, a partir da pasta local 'resources/'.
     *
     * @param filename O nome do arquivo a ser carregado.
     * @return O objeto BufferedImage, ou null se não for encontrado em nenhuma das fontes.
     */
    public static BufferedImage loadBuffered(String filename) {
        if (filename == null || filename.isEmpty()) return null;

        // 1) Classpath com prefixo /resources/
        try {
            URL url = ImageUtil.class.getResource(CLASSPATH_PREFIX + filename);
            if (url != null) {
                return ImageIO.read(url);
            }
        } catch (Exception ignored) {}

        // 2) Classpath na raiz
        try {
            URL url = ImageUtil.class.getResource("/" + filename);
            if (url != null) {
                return ImageIO.read(url);
            }
        } catch (Exception ignored) {}

        // 3) Sistema de arquivos local
        try {
            File f = new File(FILE_PREFIX + filename);
            if (f.exists()) {
                return ImageIO.read(f);
            }
        } catch (Exception ignored) {}

        return null; // Retorna null se a imagem não for encontrada.
    }

    /**
     * Gera programaticamente um ícone "placeholder" com a letra da peça.
     * Este método serve como um fallback robusto para o caso de os arquivos de imagem
     * das peças estarem ausentes, evitando que a aplicação quebre.
     */
    public static ImageIcon placeholderIcon(char pieceChar, boolean isWhite, int size) {
        size = sanitizeSize(size);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            // Habilita antialiasing para obter um resultado visual de maior qualidade.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color bg = isWhite ? new Color(245, 245, 245) : new Color(60, 60, 60);
            Color fg = isWhite ? new Color(25, 25, 25) : new Color(230, 230, 230);

            g.setColor(bg);
            g.fillRoundRect(0, 0, size, size, size / 6, size / 6);

            g.setColor(isWhite ? new Color(200, 200, 200) : new Color(40, 40, 40));
            g.drawRoundRect(0, 0, size - 1, size - 1, size / 6, size / 6);

            g.setColor(fg);
            int fontSize = Math.max(12, (int) (size * 0.55));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            
            // Centraliza o texto no ícone.
            FontMetrics fm = g.getFontMetrics();
            String s = String.valueOf(Character.toUpperCase(pieceChar));
            int textW = fm.stringWidth(s);
            int textH = fm.getAscent();
            int x = (size - textW) / 2;
            int y = (size + textH) / 2 - Math.max(2, size / 30);
            g.drawString(s, x, y);

        } finally {
            // É crucial liberar os recursos gráficos após o uso.
            g.dispose();
        }
        return new ImageIcon(img);
    }

    // --- Métodos Auxiliares Privados ---

    /**
     * Garante que o tamanho da imagem seja um valor positivo e razoável.
     */
    private static int sanitizeSize(int size) {
        if (size <= 0) return 1;
        return Math.min(size, 1024); // Impõe um limite superior para evitar uso excessivo de memória.
    }

    /** * Redimensiona uma BufferedImage usando Graphics2D com hints de alta qualidade.
     * Este método produz um resultado visual superior ao método `getScaledInstance()` da classe Image.
     */
    private static BufferedImage scaleImageHQ(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            // Define hints de renderização para priorizar a qualidade da imagem.
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }
}