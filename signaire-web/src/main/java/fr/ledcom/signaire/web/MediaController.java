package fr.ledcom.signaire.web;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import fr.ledcom.signaire.core.media.MediaLibrary;

/**
 * Serves the media of the bundles at the path they have in their bundle ({@code media/…}). Spring MVC
 * answers range requests, which browsers use to seek in videos.
 */
@RestController
public class MediaController {

    /** Bundles are replaced as a whole, so a media doesn't change much. */
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(7));

    private final MediaLibrary library;

    public MediaController(MediaLibrary library) {
        this.library = library;
    }

    @GetMapping("/media/{*path}")
    public ResponseEntity<Resource> media(@PathVariable("path") String path) {
        return library.find("media" + path)
                .map(media -> ResponseEntity.ok()
                        .contentType(MediaTypeFactory.getMediaType(media).orElse(MediaType.APPLICATION_OCTET_STREAM))
                        .cacheControl(CACHE)
                        .body(media))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
