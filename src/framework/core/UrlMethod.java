package framework.core;

public class UrlMethod {
    private final String url;
    private final HttpMethod method;

    public UrlMethod(String url, HttpMethod method) {
        this.url = url;
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UrlMethod)) return false;
        UrlMethod other = (UrlMethod) obj;
        if (url == null) {
            return other.url == null && method == other.method;
        }
        return url.equals(other.url) && method == other.method;
    }

    @Override
    public int hashCode() {
        int result = url == null ? 0 : url.hashCode();
        result = 31 * result + (method == null ? 0 : method.hashCode());
        return result;
    }
}
