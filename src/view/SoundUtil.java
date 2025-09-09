package view;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.net.URL;

/**
 * Classe utilitária para carregar e reproduzir efeitos sonoros.
 * Encapsula a lógica de áudio do Java Sound API para tocar clipes de som
 * de forma simples e assíncrona.
 */
public final class SoundUtil {

    // Construtor privado para impedir a instanciação, reforçando o padrão de classe utilitária.
    private SoundUtil() {}

    /**
     * Toca um arquivo de som (.wav) uma única vez.
     * A reprodução é feita em uma nova thread para não bloquear a Event Dispatch Thread (EDT) do Swing,
     * garantindo que a interface gráfica permaneça responsiva enquanto o som está tocando.
     * O método é sincronizado para prevenir condições de corrida caso seja chamado
     * múltiplas vezes em rápida sucessão.
     *
     * @param filename O nome do arquivo (ex: "move.wav") a ser procurado na pasta /sounds/ do classpath.
     */
    public static synchronized void playSound(final String filename) {
        new Thread(() -> {
            try {
                // Obtém um recurso de clipe de áudio do sistema.
                Clip clip = AudioSystem.getClip();
                
                // Carrega o recurso de som do classpath a partir da pasta /sounds/.
                URL resourceUrl = SoundUtil.class.getResource("/sounds/" + filename);

                if (resourceUrl == null) {
                    System.err.println("Arquivo de som não encontrado: " + filename);
                    return;
                }
                
                // Cria um fluxo de entrada de áudio a partir do recurso encontrado.
                AudioInputStream inputStream = AudioSystem.getAudioInputStream(resourceUrl);
                
                // Abre o clipe com o fluxo de áudio e inicia a reprodução.
                clip.open(inputStream);
                clip.start();
            } catch (Exception e) {
                // Trata exceções que possam ocorrer durante o carregamento ou reprodução do som.
                System.err.println("Erro ao tocar o som '" + filename + "': " + e.getMessage());
            }
        }).start();
    }
}