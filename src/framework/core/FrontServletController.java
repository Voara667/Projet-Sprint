package framework.core;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

public class FrontServletController extends HttpServlet {

    private Map<String, String> urlControllerMap;

    @Override
    public void init() throws ServletException {
        super.init();
        ServletContext ctx = getServletContext();
        Object attr = ctx.getAttribute(UrlMappingLoader.CONTEXT_ATTRIBUTE_NAME);
        if (attr instanceof Map) {
            //noinspection unchecked
            urlControllerMap = (Map<String, String>) attr;
        } else {
            urlControllerMap = new java.util.HashMap<>();
            ctx.log("FrontServletController: Warning - no url mapping found in ServletContext under attribute '" + UrlMappingLoader.CONTEXT_ATTRIBUTE_NAME + "'");
        }
        if (urlControllerMap.isEmpty()) {
            ctx.log("FrontServletController: Warning - urlControllerMap is empty");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null) {
            path = "/";
        }
        String controllerClass = null;
        if (urlControllerMap != null) {
            controllerClass = urlControllerMap.get(path);
        }
        resp.setContentType("text/html; charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html><head><meta charset=\"UTF-8\"><title>Front Controller</title></head><body>");
        if (controllerClass != null) {
            out.println("<h1>Path: " + escapeHtml(path) + "</h1>");
            out.println("<p>Controller class: " + escapeHtml(controllerClass) + "</p>");
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.println("<h1>Path: " + escapeHtml(path) + "</h1>");
            out.println("<p>No controller configured for this path.</p>");
        }
        out.println("</body></html>");
        out.flush();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
