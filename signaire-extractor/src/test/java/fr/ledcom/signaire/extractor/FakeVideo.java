package fr.ledcom.signaire.extractor;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import fr.ledcom.signaire.model.Media;

/**
 * Videos that players play, invented, for the fake SignSuisse: one second of a color of their own, crossed by a
 * white bar at the bottom. MP4 of H.264 written without an encoder: every frame is a picture of its own (IDR) made of
 * uncompressed macroblocks (I_PCM), in the constrained baseline profile that every decoder reads, Android's and the
 * browsers'. The color comes out as given, but for the rounding of YCbCr (BT.601, as the stream says).
 */
public final class FakeVideo {

    public static final int WIDTH = 64;
    public static final int HEIGHT = 48;
    /** Frames, and frames per second: the timescale of the track. */
    static final int FRAMES = 8;
    private static final int RATE = 8;
    private static final int MACROBLOCK = 16;
    /** The white bar crosses the last row of macroblocks. */
    private static final int BAR = WIDTH / FRAMES;
    private static final int MB_TYPE_I_PCM = 25;
    private static final int PROFILE_BASELINE = 66;
    private static final int CONSTRAINED = 0b1100_0000;
    private static final int LEVEL_3 = 30;
    private static final int BT_601 = 6;
    private static final int NAL_IDR = 5;
    private static final int NAL_SPS = 7;
    private static final int NAL_PPS = 8;

    private FakeVideo() {
    }

    /** The color of a video of the fake, from its path: each its own. */
    public static Color color(Media video) {
        return color(URI.create(video.sourceUrl()).getPath());
    }

    static Color color(String path) {
        return Color.getHSBColor(Math.floorMod(path.hashCode(), 360) / 360f, 0.7f, 0.85f);
    }

    /** One second of a color, in MP4. */
    public static byte[] mp4(Color color) {
        byte[] sps = nal(NAL_SPS, sps());
        byte[] pps = nal(NAL_PPS, pps());
        List<byte[]> samples = new ArrayList<>();
        for (int frame = 0; frame < FRAMES; frame++) {
            byte[] slice = nal(NAL_IDR, slice(frame, color));
            samples.add(ByteBuffer.allocate(4 + slice.length).putInt(slice.length).put(slice).array());
        }
        byte[] ftyp = new Box("ftyp").ascii("isom").u32(0x200).ascii("isomiso2avc1mp41").toByteArray();
        int moovLength = moov(sps, pps, samples, 0).length;
        byte[] moov = moov(sps, pps, samples, ftyp.length + moovLength + 8);
        Box mdat = new Box("mdat");
        samples.forEach(mdat::bytes);
        byte[] media = mdat.toByteArray();
        return ByteBuffer.allocate(ftyp.length + moov.length + media.length).put(ftyp).put(moov).put(media).array();
    }

    /** The movie: one track of video, its samples in one chunk at an offset of the file. */
    private static byte[] moov(byte[] sps, byte[] pps, List<byte[]> samples, int offset) {
        int milliseconds = 1000 * FRAMES / RATE;
        Box mvhd = Box.full("mvhd", 0).u32(0).u32(0).u32(1000).u32(milliseconds).u32(0x1_0000).u16(0x100).zeros(10)
                .matrix().zeros(24).u32(2);
        Box tkhd = Box.full("tkhd", 3).u32(0).u32(0).u32(1).u32(0).u32(milliseconds).zeros(8).u16(0).u16(0).u16(0)
                .u16(0).matrix().u32(WIDTH << 16).u32(HEIGHT << 16);
        Box mdhd = Box.full("mdhd", 0).u32(0).u32(0).u32(RATE).u32(FRAMES).u16(0x55C4).u16(0);
        Box hdlr = Box.full("hdlr", 0).u32(0).ascii("vide").zeros(12).ascii("Signaire").u8(0);
        Box avcC = new Box("avcC").u8(1).u8(PROFILE_BASELINE).u8(CONSTRAINED).u8(LEVEL_3).u8(0xFF).u8(0xE1)
                .u16(sps.length).bytes(sps).u8(1).u16(pps.length).bytes(pps);
        Box avc1 = new Box("avc1").zeros(6).u16(1).zeros(16).u16(WIDTH).u16(HEIGHT).u32(0x48_0000).u32(0x48_0000)
                .u32(0).u16(1).zeros(32).u16(0x18).u16(0xFFFF).child(avcC);
        Box stsz = Box.full("stsz", 0).u32(0).u32(samples.size());
        samples.forEach(sample -> stsz.u32(sample.length));
        Box stbl = new Box("stbl").child(Box.full("stsd", 0).u32(1).child(avc1),
                Box.full("stts", 0).u32(1).u32(samples.size()).u32(1),
                Box.full("stsc", 0).u32(1).u32(1).u32(samples.size()).u32(1), stsz,
                Box.full("stco", 0).u32(1).u32(offset));
        Box minf = new Box("minf").child(Box.full("vmhd", 1).zeros(8),
                new Box("dinf").child(Box.full("dref", 0).u32(1).child(Box.full("url ", 1))), stbl);
        Box trak = new Box("trak").child(tkhd, new Box("mdia").child(mdhd, hdlr, minf));
        return new Box("moov").child(mvhd, trak).toByteArray();
    }

    /** The sequence: baseline, the size, BT.601 colors in the range of video, no frame held back. */
    private static byte[] sps() {
        Bits sps = new Bits().u(8, PROFILE_BASELINE).u(8, CONSTRAINED).u(8, LEVEL_3);
        sps.ue(0); // seq_parameter_set_id
        sps.ue(0); // log2_max_frame_num_minus4
        sps.ue(2); // pic_order_cnt_type: the order of decoding
        sps.ue(1); // max_num_ref_frames
        sps.u(1, 0); // gaps_in_frame_num_value_allowed_flag
        sps.ue(WIDTH / MACROBLOCK - 1);
        sps.ue(HEIGHT / MACROBLOCK - 1);
        sps.u(1, 1); // frame_mbs_only_flag
        sps.u(1, 1); // direct_8x8_inference_flag
        sps.u(1, 0); // frame_cropping_flag
        sps.u(1, 1); // vui_parameters_present_flag
        sps.u(1, 0); // aspect_ratio_info_present_flag
        sps.u(1, 0); // overscan_info_present_flag
        sps.u(1, 1); // video_signal_type_present_flag
        sps.u(3, 5); // video_format: unspecified
        sps.u(1, 0); // video_full_range_flag: 16 to 235
        sps.u(1, 1); // colour_description_present_flag
        sps.u(8, BT_601).u(8, BT_601).u(8, BT_601); // primaries, transfer, matrix
        sps.u(1, 0); // chroma_loc_info_present_flag
        sps.u(1, 0); // timing_info_present_flag: the container's
        sps.u(1, 0); // nal_hrd_parameters_present_flag
        sps.u(1, 0); // vcl_hrd_parameters_present_flag
        sps.u(1, 0); // pic_struct_present_flag
        sps.u(1, 1); // bitstream_restriction_flag
        sps.u(1, 1); // motion_vectors_over_pic_boundaries_flag
        sps.ue(0); // max_bytes_per_pic_denom
        sps.ue(0); // max_bits_per_mb_denom
        sps.ue(16); // log2_max_mv_length_horizontal
        sps.ue(16); // log2_max_mv_length_vertical
        sps.ue(0); // max_num_reorder_frames
        sps.ue(1); // max_dec_frame_buffering
        return sps.trailing();
    }

    private static byte[] pps() {
        Bits pps = new Bits();
        pps.ue(0); // pic_parameter_set_id
        pps.ue(0); // seq_parameter_set_id
        pps.u(1, 0); // entropy_coding_mode_flag: CAVLC
        pps.u(1, 0); // bottom_field_pic_order_in_frame_present_flag
        pps.ue(0); // num_slice_groups_minus1
        pps.ue(0); // num_ref_idx_l0_default_active_minus1
        pps.ue(0); // num_ref_idx_l1_default_active_minus1
        pps.u(1, 0); // weighted_pred_flag
        pps.u(2, 0); // weighted_bipred_idc
        pps.se(0); // pic_init_qp_minus26
        pps.se(0); // pic_init_qs_minus26
        pps.se(0); // chroma_qp_index_offset
        pps.u(1, 1); // deblocking_filter_control_present_flag
        pps.u(1, 0); // constrained_intra_pred_flag
        pps.u(1, 0); // redundant_pic_cnt_present_flag
        return pps.trailing();
    }

    /** A frame: one slice of the whole picture, its macroblocks given as they are. */
    private static byte[] slice(int frame, Color color) {
        byte[][][] planes = planes(frame, color);
        Bits slice = new Bits();
        slice.ue(0); // first_mb_in_slice
        slice.ue(7); // slice_type: I, as every slice of the picture
        slice.ue(0); // pic_parameter_set_id
        slice.u(4, 0); // frame_num, of an IDR picture
        slice.ue(frame % 2); // idr_pic_id: another than the previous picture's
        slice.u(1, 0); // no_output_of_prior_pics_flag
        slice.u(1, 0); // long_term_reference_flag
        slice.se(0); // slice_qp_delta
        slice.ue(1); // disable_deblocking_filter_idc: none
        for (int top = 0; top < HEIGHT; top += MACROBLOCK) {
            for (int left = 0; left < WIDTH; left += MACROBLOCK) {
                slice.ue(MB_TYPE_I_PCM);
                slice.align();
                block(slice, planes[0], left, top, MACROBLOCK);
                block(slice, planes[1], left / 2, top / 2, MACROBLOCK / 2);
                block(slice, planes[2], left / 2, top / 2, MACROBLOCK / 2);
            }
        }
        return slice.trailing();
    }

    private static void block(Bits slice, byte[][] plane, int left, int top, int size) {
        for (int y = top; y < top + size; y++) {
            slice.bytes(plane[y], left, size);
        }
    }

    /** Y, Cb and Cr of a frame: the color, and the bar at the bottom, further right each frame. */
    private static byte[][][] planes(int frame, Color color) {
        int[] background = ycbcr(color);
        int[] white = ycbcr(Color.WHITE);
        byte[][][] planes = {new byte[HEIGHT][WIDTH], new byte[HEIGHT / 2][WIDTH / 2], new byte[HEIGHT / 2][WIDTH / 2]};
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean bar = y >= HEIGHT - MACROBLOCK && x >= frame * BAR && x < (frame + 1) * BAR;
                int[] pixel = bar ? white : background;
                planes[0][y][x] = (byte) pixel[0];
                planes[1][y / 2][x / 2] = (byte) pixel[1];
                planes[2][y / 2][x / 2] = (byte) pixel[2];
            }
        }
        return planes;
    }

    /** BT.601, in the range of video (Y from 16 to 235): never 0, which the stream would have to escape. */
    static int[] ycbcr(Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        return new int[] {
            (int) Math.round(16 + (65.481 * r + 128.553 * g + 24.966 * b) / 255),
            (int) Math.round(128 + (-37.797 * r - 74.203 * g + 112.0 * b) / 255),
            (int) Math.round(128 + (112.0 * r - 93.786 * g - 18.214 * b) / 255)};
    }

    /**
     * A NAL unit of a reference picture or of its parameters: its header, then its payload, a byte 3 put in after two
     * zero bytes that would make a start code.
     */
    private static byte[] nal(int type, byte[] payload) {
        ByteArrayOutputStream nal = new ByteArrayOutputStream();
        nal.write(3 << 5 | type);
        int zeros = 0;
        for (byte value : payload) {
            if (zeros == 2 && (value & 0xFF) <= 3) {
                nal.write(3);
                zeros = 0;
            }
            nal.write(value);
            zeros = value == 0 ? zeros + 1 : 0;
        }
        return nal.toByteArray();
    }

    /** The bits of H.264's syntax: fixed lengths, Exp-Golomb codes (ue, se). */
    private static final class Bits {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int current;
        private int count;

        Bits u(int length, int value) {
            for (int bit = length - 1; bit >= 0; bit--) {
                current = current << 1 | (value >>> bit & 1);
                if (++count == 8) {
                    bytes.write(current);
                    current = 0;
                    count = 0;
                }
            }
            return this;
        }

        void ue(int value) {
            int length = 32 - Integer.numberOfLeadingZeros(value + 1);
            u(length - 1, 0);
            u(length, value + 1);
        }

        void se(int value) {
            ue(value <= 0 ? -2 * value : 2 * value - 1);
        }

        void align() {
            while (count != 0) {
                u(1, 0);
            }
        }

        void bytes(byte[] values, int offset, int length) {
            bytes.write(values, offset, length);
        }

        /** The stop bit and the zeros to the end of the byte, then the bytes. */
        byte[] trailing() {
            u(1, 1);
            align();
            return bytes.toByteArray();
        }
    }

    /** A box of ISO base media files: its size, its type, its content. */
    private static final class Box {
        private final ByteArrayOutputStream content = new ByteArrayOutputStream();
        private final String type;

        Box(String type) {
            this.type = type;
        }

        /** A box with a version, 0, and flags. */
        static Box full(String type, int flags) {
            return new Box(type).u32(flags);
        }

        Box u8(int value) {
            content.write(value);
            return this;
        }

        Box u16(int value) {
            return u8(value >>> 8).u8(value);
        }

        Box u32(int value) {
            return u16(value >>> 16).u16(value);
        }

        Box zeros(int length) {
            content.writeBytes(new byte[length]);
            return this;
        }

        Box ascii(String text) {
            content.writeBytes(text.getBytes(US_ASCII));
            return this;
        }

        Box bytes(byte[] values) {
            content.writeBytes(values);
            return this;
        }

        /** The identity, for the movie and the track. */
        Box matrix() {
            return u32(0x1_0000).u32(0).u32(0).u32(0).u32(0x1_0000).u32(0).u32(0).u32(0).u32(0x4000_0000);
        }

        Box child(Box... boxes) {
            for (Box box : boxes) {
                content.writeBytes(box.toByteArray());
            }
            return this;
        }

        byte[] toByteArray() {
            byte[] body = content.toByteArray();
            return ByteBuffer.allocate(8 + body.length).putInt(8 + body.length).put(type.getBytes(US_ASCII)).put(body)
                    .array();
        }
    }
}
