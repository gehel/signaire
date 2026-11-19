package fr.ledcom.signaire.extractor;

/**
 * The media of the signs to download into the bundles. The others are only referenced by their SignSuisse URL:
 * pages show them from SignSuisse, exports have no file for them.
 */
public enum MediaChoice {
    /** Videos (of the signs, examples and dialect variants) and images. */
    ALL(true, true),
    VIDEOS(true, false),
    IMAGES(false, true),
    NONE(false, false);

    private final boolean videos;
    private final boolean images;

    MediaChoice(boolean videos, boolean images) {
        this.videos = videos;
        this.images = images;
    }

    public boolean videos() {
        return videos;
    }

    public boolean images() {
        return images;
    }
}
