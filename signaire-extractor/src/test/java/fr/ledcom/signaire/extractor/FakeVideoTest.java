package fr.ledcom.signaire.extractor;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.model.TestSigns;

/**
 * The videos of the fake SignSuisse, as far as the JVM can tell without a decoder: an MP4 of one track of H.264, one
 * second long, whose frames carry the color as given. {@code AndroidIT} plays one on the phone.
 */
class FakeVideoTest {

    private static final Color COLOR = new Color(65, 98, 217);
    private static final int MACROBLOCK = 16 * 16 * 3 / 2;

    private final ByteBuffer video = ByteBuffer.wrap(FakeVideo.mp4(COLOR));

    @Test
    void isAnMp4OfOneTrackOfVideoOfOneSecond() {
        Map<String, ByteBuffer> file = boxes(video);
        assertThat(file).containsOnlyKeys("ftyp", "moov", "mdat");
        assertThat(ascii(file.get("ftyp"), 0, 4)).isEqualTo("isom");
        Map<String, ByteBuffer> track = boxes(boxes(file.get("moov")).get("trak"));
        Map<String, ByteBuffer> media = boxes(track.get("mdia"));
        ByteBuffer mdhd = media.get("mdhd");
        assertThat(mdhd.getInt(16)).as("duration").isEqualTo(mdhd.getInt(12));
        assertThat(ascii(media.get("hdlr"), 8, 4)).isEqualTo("vide");
        ByteBuffer tkhd = track.get("tkhd");
        int size = tkhd.limit();
        assertThat(new int[] {tkhd.getInt(size - 8) >>> 16, tkhd.getInt(size - 4) >>> 16})
                .containsExactly(FakeVideo.WIDTH, FakeVideo.HEIGHT);
    }

    @Test
    void itsFramesArePicturesOfH264OfTheColorAsGiven() {
        int[] ycbcr = FakeVideo.ycbcr(COLOR);
        byte[] macroblock = new byte[MACROBLOCK];
        Arrays.fill(macroblock, 0, 256, (byte) ycbcr[0]);
        Arrays.fill(macroblock, 256, 320, (byte) ycbcr[1]);
        Arrays.fill(macroblock, 320, MACROBLOCK, (byte) ycbcr[2]);
        assertThat(samples()).hasSize(FakeVideo.FRAMES).allSatisfy(sample -> {
            assertThat(sample.getInt(0)).as("length of the NAL unit").isEqualTo(sample.limit() - 4);
            assertThat(sample.get(4) & 0x1F).as("type of the NAL unit: an IDR picture").isEqualTo(5);
            assertThat(bytes(sample)).containsSequence(toList(macroblock));
        });
        ByteBuffer stsd = table().get("stsd");
        String description = ascii(stsd, 0, stsd.limit());
        assertThat(description).contains("avc1", "avcC");
        int avcC = description.indexOf("avcC") + 4;
        assertThat(stsd.get(avcC + 8) & 0x1F).as("first NAL unit of the configuration: a sequence").isEqualTo(7);
    }

    @Test
    void eachVideoHasAColorOfItsOwn() {
        assertThat(FakeVideo.color(TestSigns.GARE.videos().getFirst()))
                .isNotEqualTo(FakeVideo.color(TestSigns.LAUSANNE.videos().getFirst()));
    }

    /** The frames, where the sample table says they are. */
    private List<ByteBuffer> samples() {
        Map<String, ByteBuffer> table = table();
        ByteBuffer sizes = table.get("stsz");
        int offset = table.get("stco").getInt(8);
        List<ByteBuffer> samples = new ArrayList<>();
        for (int i = 0; i < sizes.getInt(8); i++) {
            int size = sizes.getInt(12 + 4 * i);
            samples.add(video.slice(offset, size));
            offset += size;
        }
        assertThat(offset).as("end of the samples, the end of the file").isEqualTo(video.limit());
        return samples;
    }

    private Map<String, ByteBuffer> table() {
        Map<String, ByteBuffer> track = boxes(boxes(boxes(video).get("moov")).get("trak"));
        return boxes(boxes(boxes(track.get("mdia")).get("minf")).get("stbl"));
    }

    /** The boxes in a box (or a file), by type, their content without their header; they fill it. */
    private static Map<String, ByteBuffer> boxes(ByteBuffer parent) {
        Map<String, ByteBuffer> boxes = new LinkedHashMap<>();
        int position = 0;
        while (position < parent.limit()) {
            int size = parent.getInt(position);
            boxes.put(ascii(parent, position + 4, 4), parent.slice(position + 8, size - 8));
            position += size;
        }
        assertThat(position).as("end of the last box").isEqualTo(parent.limit());
        return boxes;
    }

    private static String ascii(ByteBuffer buffer, int index, int length) {
        byte[] bytes = new byte[length];
        buffer.get(index, bytes);
        return new String(bytes, US_ASCII);
    }

    private static List<Byte> bytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.limit()];
        buffer.get(0, bytes);
        return toList(bytes);
    }

    private static List<Byte> toList(byte[] bytes) {
        List<Byte> list = new ArrayList<>(bytes.length);
        for (byte value : bytes) {
            list.add(value);
        }
        return list;
    }
}
