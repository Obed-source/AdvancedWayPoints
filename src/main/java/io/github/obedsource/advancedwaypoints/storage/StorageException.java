package io.github.obedsource.advancedwaypoints.storage;

/** Storage failure, independent of the database technology. */
public class StorageException extends Exception {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
