package fr.ledcom.signaire.extractor;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/** The media of a sign: which are chosen, their files in the cache and the bundle. */
final class SignMedia {

    private static final String MEDIA_DIRECTORY = "media/";
    private static final Pattern UNSAFE_FILE_NAME_CHARACTERS = Pattern.compile("[^A-Za-z0-9._-]");

    private SignMedia() {
    }

    /** The media of the sign that the choice downloads. */
    static List<Media> chosen(Sign sign, MediaChoice choice) {
        List<Media> chosen = new ArrayList<>();
        if (choice.videos()) {
            chosen.addAll(sign.videos());
            sign.examples().stream().map(Example::video).filter(Objects::nonNull).forEach(chosen::add);
            sign.dialectVariants().forEach(variant -> chosen.add(variant.video()));
        }
        if (choice.images()) {
            chosen.addAll(sign.images());
        }
        return chosen;
    }

    /** The sign with each video (of the sign, its examples and dialect variants) and image replaced. */
    static Sign with(Sign sign, UnaryOperator<Media> video, UnaryOperator<Media> image) {
        return sign.toBuilder()
                .videos(sign.videos().stream().map(video).toList())
                .examples(sign.examples().stream()
                        .map(example -> example.video() == null ? example
                                : new Example(example.text(), video.apply(example.video())))
                        .toList())
                .dialectVariants(sign.dialectVariants().stream()
                        .map(variant -> new DialectVariant(variant.dialects(), video.apply(variant.video())))
                        .toList())
                .images(sign.images().stream().map(image).toList())
                .build();
    }

    /** The media referenced by its SignSuisse URL only (a reused sign may have had it downloaded). */
    static Media linked(Media media) {
        return media.file() == null ? media : new Media(media.sourceUrl(), null);
    }

    /** The file of a media, in the cache and in the bundle. */
    static String file(Media media) {
        URI uri = URI.create(media.sourceUrl());
        String path = uri.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isEmpty()) {
            throw new ExtractionException("No file name in media URL " + uri);
        }
        return MEDIA_DIRECTORY + UNSAFE_FILE_NAME_CHARACTERS.matcher(name).replaceAll("_");
    }
}
