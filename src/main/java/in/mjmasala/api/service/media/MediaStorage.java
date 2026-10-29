package in.mjmasala.api.service.media;

import java.util.Optional;

/**
 * Where uploaded files live.
 *
 * The shipped implementation writes to a directory. Putting the files on S3 (or any object
 * store) means adding an implementation of this interface and marking the bean
 * {@code @Primary} — nothing above it knows the difference, because callers only ever deal
 * in filenames and bytes.
 */
public interface MediaStorage {

    /**
     * Writes a file and returns the name it was stored under.
     *
     * Names are generated, never taken from the upload — see {@link MediaService}.
     */
    String store(String filename, byte[] content);

    Optional<byte[]> read(String filename);

    void delete(String filename);
}
