package com.zelo.ocorrencia.infra;

import com.zelo.compartilhado.erro.ArquivoInvalidoException;
import com.zelo.compartilhado.erro.ArquivoInvalidoException.Motivo;
import com.zelo.ocorrencia.application.OcorrenciaProperties;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Valida e normaliza fotos (RNF03, RNF11, RNF13): confere o tipo pelo conteúdo (não pela extensão),
 * limita tamanho e resolução, aplica a orientação EXIF, redimensiona e regrava como JPEG. A regravação
 * descarta todos os metadados, inclusive EXIF com GPS.
 */
@Component
public class ProcessadorDeFotos {

    public static final String CONTENT_TYPE_SAIDA = "image/jpeg";

    private enum Formato { JPEG, PNG, WEBP }

    public record FotoProcessada(byte[] conteudo, String contentType, int largura, int altura) {
    }

    private final OcorrenciaProperties.Fotos config;

    public ProcessadorDeFotos(OcorrenciaProperties properties) {
        this.config = properties.fotos();
    }

    public FotoProcessada processar(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "foto";
        if (arquivo.isEmpty()) {
            throw new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO, "O arquivo '" + nome + "' está vazio.");
        }
        if (arquivo.getSize() > config.tamanhoMaximo().toBytes()) {
            throw new ArquivoInvalidoException(Motivo.TAMANHO_EXCEDIDO,
                    "A foto '" + nome + "' excede o limite de " + config.tamanhoMaximo().toMegabytes() + " MB.");
        }
        try {
            return processar(arquivo.getBytes(), nome);
        } catch (IOException e) {
            throw new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO, "Não foi possível ler a foto '" + nome + "'.");
        }
    }

    FotoProcessada processar(byte[] bytes, String nome) {
        Formato formato = detectarFormato(bytes);
        if (formato == null) {
            throw new ArquivoInvalidoException(Motivo.FORMATO_NAO_SUPORTADO,
                    "Formato da foto '" + nome + "' não suportado. Envie JPEG, PNG ou WEBP.");
        }
        try {
            exigirResolucaoAceitavel(bytes, nome);
            BufferedImage original = formato == Formato.JPEG
                    // Thumbnailator aplica a orientação EXIF ao ler JPEG.
                    ? Thumbnails.of(new ByteArrayInputStream(bytes)).scale(1.0).asBufferedImage()
                    : ImageIO.read(new ByteArrayInputStream(bytes));
            if (original == null) {
                throw corrompida(nome);
            }
            BufferedImage rgb = paraRgbSobreBranco(original);
            int maior = Math.max(rgb.getWidth(), rgb.getHeight());
            BufferedImage final_ = maior > config.dimensaoMaxima()
                    ? Thumbnails.of(rgb).size(config.dimensaoMaxima(), config.dimensaoMaxima())
                    .imageType(BufferedImage.TYPE_INT_RGB).asBufferedImage()
                    : rgb;
            return new FotoProcessada(codificarJpeg(final_), CONTENT_TYPE_SAIDA, final_.getWidth(), final_.getHeight());
        } catch (IOException | RuntimeException e) {
            if (e instanceof ArquivoInvalidoException invalido) {
                throw invalido;
            }
            throw corrompida(nome);
        }
    }

    private void exigirResolucaoAceitavel(byte[] bytes, String nome) throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw corrompida(nome);
            }
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada, true, true);
                long pixels = (long) leitor.getWidth(0) * leitor.getHeight(0);
                if (pixels > config.megapixelsMaximo() * 1_000_000L) {
                    throw new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO,
                            "A foto '" + nome + "' tem resolução acima do limite de " + config.megapixelsMaximo() + " megapixels.");
                }
            } finally {
                leitor.dispose();
            }
        }
    }

    private byte[] codificarJpeg(BufferedImage imagem) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ImageOutputStream destino = ImageIO.createImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(config.qualidadeJpeg());
            escritor.setOutput(destino);
            // Sem metadados: o arquivo final não carrega EXIF.
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return saida.toByteArray();
    }

    private static BufferedImage paraRgbSobreBranco(BufferedImage origem) {
        if (origem.getType() == BufferedImage.TYPE_INT_RGB) {
            return origem;
        }
        BufferedImage rgb = new BufferedImage(origem.getWidth(), origem.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(origem, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static Formato detectarFormato(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Formato.JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Formato.PNG;
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Formato.WEBP;
        }
        return null;
    }

    private static ArquivoInvalidoException corrompida(String nome) {
        return new ArquivoInvalidoException(Motivo.CONTEUDO_INVALIDO,
                "Não foi possível ler a foto '" + nome + "'. O arquivo pode estar corrompido.");
    }
}
