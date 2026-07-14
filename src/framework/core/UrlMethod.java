package framework.core;

public class UrlMethod {
    private final String url;
    private final String method;

    public UrlMethod(String url, String method) {
        this.url = url;
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public String getMethod() {
        return method;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UrlMethod)) return false;
        UrlMethod other = (UrlMethod) obj;
        if (url == null) return other.url == null && (method == null ? other.method == null : method.equalsIgnoreCase(other.method));
        return url.equals(other.url) && (method == null ? other.method == null : method.equalsIgnoreCase(other.method));
    }

    @Override
    public int hashCode() {
        String m = method == null ? "" : method.toUpperCase();
        return (url + m).hashCode();
    }
}
