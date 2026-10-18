package framework.core;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Map;

public final class MultipartSupport {
    private static final String PARTS_ATTRIBUTE = MultipartSupport.class.getName() + ".parts";

    private MultipartSupport() {
    }

    public static boolean isMultipart(HttpServletRequest req) {
        String contentType = req.getContentType();
        return contentType != null && contentType.regionMatches(true, 0, "multipart/", 0, 10);
    }

    public static void prepare(HttpServletRequest req)
            throws UploadException, IOException, ServletException {
        if (!isMultipart(req) || req.getAttribute(PARTS_ATTRIBUTE) != null) {
            return;
        }
        try {
            req.setAttribute(PARTS_ATTRIBUTE, req.getParts());
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                throw new UploadException(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "Envoi multipart trop volumineux : " + e.getMessage());
            }
            throw new UploadException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Configuration multipart absente : ajoutez <multipart-config> à la servlet dans web.xml.");
        }
    }

    public static Map<String, UploadedFile> readFiles(HttpServletRequest req)
            throws IOException, ServletException {
        if (!isMultipart(req)) {
            return new LinkedHashMap<>();
        }
        Object stored = req.getAttribute(PARTS_ATTRIBUTE);
        Collection<Part> parts;
        if (stored instanceof Collection) {
            parts = (Collection<Part>) stored;
        } else {
            parts = req.getParts();
            req.setAttribute(PARTS_ATTRIBUTE, parts);
        }
        Map<String, UploadedFile> files = new LinkedHashMap<>();
        for (Part part : parts) {
            String submitted = part.getSubmittedFileName();
            if (submitted == null) {
                continue;
            }
            String fileName = cleanFileName(submitted);
            if (fileName.isEmpty() || files.containsKey(part.getName())) {
                continue;
            }
            byte[] bytes = part.getInputStream().readAllBytes();
            files.put(part.getName(), new UploadedFile(part.getName(), fileName,
                    extensionOf(fileName), part.getContentType(), part.getSize(), bytes));
        }
        return files;
    }

    public static String cleanFileName(String submitted) {
        if (submitted == null || submitted.isEmpty()) {
            return "";
        }
        int slash = Math.max(submitted.lastIndexOf('/'), submitted.lastIndexOf('\\'));
        return submitted.substring(slash + 1);
    }

    public static String extensionOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
