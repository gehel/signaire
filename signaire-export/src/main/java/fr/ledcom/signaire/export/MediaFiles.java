package fr.ledcom.signaire.export;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/** Names and types of media files. */
final class MediaFiles {

    private MediaFiles() {
    }

    /** File name of a media, without directories: from the bundle, else from its URL. */
    static String fileName(Media media) {
        String path = Objects.requireNonNullElse(media.file(), media.sourceUrl());
        int query = path.indexOf('?');
        String withoutQuery = query < 0 ? path : path.substring(0, query);
        return withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
    }

    static String extension(Media media) {
        String name = fileName(media);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String mediaType(Media media) {
        return switch (extension(media)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "mp4", "m4v" -> "video/mp4";
            case "webm" -> "video/webm";
            default -> "application/octet-stream";
        };
    }

    static Optional<Media> firstVideo(Sign sign) {
        return sign.videos().stream().findFirst();
    }

    static Optional<Media> firstImage(Sign sign) {
        return sign.images().stream().findFirst();
    }
}
