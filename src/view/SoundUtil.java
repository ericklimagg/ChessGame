package view;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.net.URL;

/**
 * Utilitário para carregar e tocar efeitos sonoros.
 * Procura por arquivos .wav na pasta /sounds/ dentro do classpath de recursos.
 */
public final class SoundUtil {

    private SoundUtil() { /* Classe utilitária, não instanciável */ }

    /**
     * Toca um arquivo de som uma vez.
     * @param filename O nome do arquivo (ex: "move.wav") a ser procurado em /resources/sounds/
     */
    public static synchronized void playSound(final String filename) {
        new Thread(() -> {
            try {
                Clip clip = AudioSystem.getClip();
                
                // =========================================================================
                // A CORREÇÃO ESTÁ AQUI: O caminho agora é "/sounds/" em vez de "/resources/sounds/"
                URL resourceUrl = SoundUtil.class.getResource("/sounds/" + filename);
                // =========================================================================

                if (resourceUrl == null) {
                    System.err.println("Arquivo de som não encontrado: " + filename);
                    return;
                }
                AudioInputStream inputStream = AudioSystem.getAudioInputStream(resourceUrl);
                clip.open(inputStream);
                clip.start();
            } catch (Exception e) {
                System.err.println("Erro ao tocar o som '" + filename + "': " + e.getMessage());
            }
        }).start();
    }
}