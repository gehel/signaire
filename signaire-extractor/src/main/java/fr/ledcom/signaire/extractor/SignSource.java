package fr.ledcom.signaire.extractor;

import java.net.URI;
import java.nio.file.Path;

import fr.ledcom.signaire.model.Sign;
import net.jcip.annotations.ThreadSafe;

/** Where signs and their media come from: the stages of an extraction call it at the same time. */
@ThreadSafe
public interface SignSource extends SignSearch {

    /** Fetches the details of a sign found by the search; media are only referenced by URL. */
    Sign fetchSign(SearchHit hit);

    /** Downloads a media file. */
    void download(URI uri, Path target);
}
