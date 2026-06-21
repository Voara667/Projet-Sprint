package framework.core;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class UrlMappingLoader {

    public static final String CONTEXT_ATTRIBUTE_NAME = "urlControllerMap";

    public static Map<String, String> loadMappings(String xmlFilePath) {
        Map<String, String> map = new HashMap<>();
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            File f = new File(xmlFilePath);
            if (!f.exists()) {
                System.err.println("UrlMappingLoader: mapping file not found: " + xmlFilePath);
                return map;
            }
            Document doc = db.parse(f);
            doc.getDocumentElement().normalize();
            NodeList mappingNodes = doc.getElementsByTagName("mapping");
            for (int i = 0; i < mappingNodes.getLength(); i++) {
                Element mappingEl = (Element) mappingNodes.item(i);
                NodeList urlNodes = mappingEl.getElementsByTagName("url");
                NodeList classNodes = mappingEl.getElementsByTagName("class");
                if (urlNodes.getLength() == 0 || classNodes.getLength() == 0) {
                    continue;
                }
                String url = urlNodes.item(0).getTextContent().trim();
                String className = classNodes.item(0).getTextContent().trim();
                if (!url.isEmpty() && !className.isEmpty()) {
                    map.put(url, className);
                }
            }
        } catch (Exception e) {
            System.err.println("UrlMappingLoader: error loading mappings from " + xmlFilePath + ": " + e.getMessage());
        }
        return map;
    }
}
