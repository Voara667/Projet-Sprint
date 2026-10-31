package framework.core;

import jakarta.servlet.http.HttpSession;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Session {
    private final Map<String, Object> data = new ConcurrentHashMap<>();
    private String id;
    private volatile boolean invalidated;

    public String getId() {
        return id;
    }

    public Object get(String key) {
        return key == null ? null : data.get(key);
    }

    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        return type != null && type.isInstance(value) ? type.cast(value) : null;
    }

    public Session set(String key, Object value) {
        if (invalidated) {
            throw new IllegalStateException("La session est invalidée et ne peut plus être modifiée.");
        }
        if (key == null) {
            throw new IllegalArgumentException("La clé de session ne peut pas être null.");
        }
        if (value == null) {
            data.remove(key);
        } else {
            data.put(key, value);
        }
        return this;
    }

    public void remove(String key) {
        if (key != null) {
            data.remove(key);
        }
    }

    public boolean contains(String key) {
        return key != null && data.containsKey(key);
    }

    public void clear() {
        data.clear();
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void invalidate() {
        data.clear();
        invalidated = true;
    }

    public boolean isInvalidated() {
        return invalidated;
    }

    synchronized void loadFrom(HttpSession httpSession) {
        if (invalidated) {
            return;
        }
        data.clear();
        id = httpSession.getId();
        Enumeration<String> names = httpSession.getAttributeNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            Object value = httpSession.getAttribute(name);
            if (value != null) {
                data.put(name, value);
            }
        }
    }

    synchronized void saveTo(HttpSession httpSession) {
        if (httpSession == null || invalidated) {
            return;
        }
        Enumeration<String> names = httpSession.getAttributeNames();
        List<String> existingNames = new ArrayList<>();
        while (names != null && names.hasMoreElements()) {
            existingNames.add(names.nextElement());
        }
        for (String name : existingNames) {
            if (!data.containsKey(name)) {
                httpSession.removeAttribute(name);
            }
        }
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (httpSession.getAttribute(entry.getKey()) != entry.getValue()) {
                httpSession.setAttribute(entry.getKey(), entry.getValue());
            }
        }
    }
}
