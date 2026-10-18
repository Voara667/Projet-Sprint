package framework.core;

public final class UploadedFile {
    private final String fieldName;
    private final String fileName;
    private final String extension;
    private final String contentType;
    private final long size;
    private final byte[] bytes;

    public UploadedFile(String fieldName, String fileName, String extension,
                        String contentType, long size, byte[] bytes) {
        this.fieldName = fieldName;
        this.fileName = fileName;
        this.extension = extension;
        this.contentType = contentType;
        this.size = size;
        this.bytes = bytes;
    }

    public String getFieldName() { return fieldName; }
    public String getFileName() { return fileName; }
    public String getExtension() { return extension; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    /** Returns the original in-memory byte array. */
    public byte[] getBytes() { return bytes; }

    @Override
    public String toString() {
        return "UploadedFile{fieldName='" + fieldName + "', fileName='" + fileName
                + "', extension='" + extension + "', contentType='" + contentType
                + "', size=" + size + "}";
    }
}
