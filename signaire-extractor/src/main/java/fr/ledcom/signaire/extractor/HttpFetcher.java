package fr.ledcom.signaire.extractor;

import java.net.URI;
import java.nio.file.Path;

/** Fetches resources over HTTP. */
public interface HttpFetcher {

    /** Fetches a text resource, decoded with the charset announced by the server (UTF-8 by default). */
    String fetchText(URI uri);

    /** Downloads a resource to {@code target}, creating parent directories; no file is left on failure. */
    void download(URI uri, Path target);
}
