package framework.core;

import jakarta.servlet.ServletException;

public class UploadException extends ServletException {
    private final int status;

    public UploadException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
