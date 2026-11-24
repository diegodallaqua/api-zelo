package com.zelo.ocorrencia.infra;

import com.zelo.compartilhado.erro.ArquivoInvalidoException;
import com.zelo.compartilhado.erro.ArquivoInvalidoException.Motivo;
import com.zelo.ocorrencia.application.OcorrenciaProperties;
import com.zelo.ocorrencia.infra.ProcessadorDeFotos.FotoProcessada;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessadorDeFotosTest {

    private final ProcessadorDeFotos processador = new ProcessadorDeFotos(new OcorrenciaProperties(
            ZoneId.of("America/Sao_Paulo"), 5, 25, Duration.ofDays(15), 90, 30, 5,
            new OcorrenciaProperties.Mapa(500, 1000),
            new OcorrenciaProperties.Fotos(1, 5, DataSize.ofMegabytes(1), 1920, 0.8f, 50)));

    @Test
    void redimensionaEConverteParaJpeg() throws IOException {
        FotoProcessada foto = processador.processar(arquivo(imagem("png", 3000, 1500, BufferedImage.TYPE_INT_ARGB), "a.png"));

        assertThat(foto.contentType()).isEqualTo("image/jpeg");
        assertThat(foto.largura()).isEqualTo(1920);
        assertThat(foto.altura()).isEqualTo(960);
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(foto.conteudo()));
        assertThat(lida.getWidth()).isEqualTo(1920);
    }

    @Test
    void naoAmpliaFotosPequenas() throws IOException {
        FotoProcessada foto = processador.processar(arquivo(imagem("jpg", 800, 600, BufferedImage.TYPE_INT_RGB), "b.jpg"));
        assertThat(foto.largura()).isEqualTo(800);
        assertThat(foto.altura()).isEqualTo(600);
    }

    @Test
    void removeMetadadosExif() throws IOException {
        byte[] comExif = inserirExif(imagem("jpg", 400, 300, BufferedImage.TYPE_INT_RGB));
        assertThat(new String(comExif, StandardCharsets.ISO_8859_1)).contains("Exif");

        FotoProcessada foto = processador.processar(arquivo(comExif, "c.jpg"));

        assertThat(new String(foto.conteudo(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
    }

    @Test
    void recusaFormatoPeloConteudoENaoPelaExtensao() {
        MockMultipartFile falso = new MockMultipartFile("fotos", "foto.jpg", "image/jpeg",
                "%PDF-1.7 não é imagem".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> processador.processar(falso))
                .isInstanceOf(ArquivoInvalidoException.class)
                .extracting("motivo").isEqualTo(Motivo.FORMATO_NAO_SUPORTADO);
    }

    @Test
    void recusaArquivoGrande() {
        MockMultipartFile grande = new MockMultipartFile("fotos", "g.jpg", "image/jpeg", new byte[2 * 1024 * 1024]);
        assertThatThrownBy(() -> processador.processar(grande))
                .isInstanceOf(ArquivoInvalidoException.class)
                .extracting("motivo").isEqualTo(Motivo.TAMANHO_EXCEDIDO);
    }

    @Test
    void recusaJpegCorrompido() {
        byte[] corrompido = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        assertThatThrownBy(() -> processador.processar(arquivo(corrompido, "x.jpg")))
                .isInstanceOf(ArquivoInvalidoException.class)
                .extracting("motivo").isEqualTo(Motivo.CONTEUDO_INVALIDO);
    }

    @Test
    void recusaArquivoVazio() {
        assertThatThrownBy(() -> processador.processar(arquivo(new byte[0], "v.jpg")))
                .isInstanceOf(ArquivoInvalidoException.class)
                .extracting("motivo").isEqualTo(Motivo.CONTEUDO_INVALIDO);
    }

    private static MockMultipartFile arquivo(byte[] conteudo, String nome) {
        return new MockMultipartFile("fotos", nome, "application/octet-stream", conteudo);
    }

    private static byte[] imagem(String formato, int largura, int altura, int tipo) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, tipo);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(15, 123, 108));
        g.fillRect(0, 0, largura / 2, altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, saida);
        return saida.toByteArray();
    }

    /** Insere um segmento APP1/EXIF mínimo (TIFF vazio) logo após o marcador SOI. */
    private static byte[] inserirExif(byte[] jpeg) {
        byte[] tiff = {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0};
        byte[] cabecalho = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        int tamanho = 2 + cabecalho.length + tiff.length;
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(jpeg, 0, 2);
        saida.write(0xFF);
        saida.write(0xE1);
        saida.write(tamanho >> 8);
        saida.write(tamanho & 0xFF);
        saida.writeBytes(cabecalho);
        saida.writeBytes(tiff);
        saida.write(jpeg, 2, jpeg.length - 2);
        return saida.toByteArray();
    }
}
