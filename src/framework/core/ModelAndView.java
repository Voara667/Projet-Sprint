package framework.core;

import java.util.HashMap;
import java.util.Map;

public class ModelAndView {
    private String url;
    private final Map<String, Object> data = new HashMap<>();

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setAttribute(String key, Object value) {
        data.put(key, value);
    }

    public Map<String, Object> getAttributes() {
        return data;
    }
}
