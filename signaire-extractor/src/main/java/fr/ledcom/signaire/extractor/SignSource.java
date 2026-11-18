package fr.ledcom.signaire.extractor;

import java.net.URI;
import java.nio.file.Path;

import fr.ledcom.signaire.model.Sign;

/** Where signs and their media come from. */
public interface SignSource extends SignSearch {

    /** Fetches the details of a sign found by the search; media are only referenced by URL. */
    Sign fetchSign(SearchHit hit);

    /** Downloads a media file. */
    void download(URI uri, Path target);
}
