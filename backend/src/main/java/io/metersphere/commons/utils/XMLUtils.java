package io.metersphere.commons.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.metersphere.performance.parse.EngineSourceParserFactory;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.jetbrains.annotations.Nullable;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XMLUtils {

    public static void setExpandEntityReferencesFalse(DocumentBuilderFactory documentBuilderFactory) {
        try {
            String FEATURE = null;
            FEATURE = "http://javax.xml.XMLConstants/feature/secure-processing";
            documentBuilderFactory.setFeature(FEATURE, true);
            FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
            documentBuilderFactory.setFeature(FEATURE, true);
            FEATURE = "http://xml.org/sax/features/external-parameter-entities";
            documentBuilderFactory.setFeature(FEATURE, false);
            FEATURE = "http://xml.org/sax/features/external-general-entities";
            documentBuilderFactory.setFeature(FEATURE, false);
            FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
            documentBuilderFactory.setFeature(FEATURE, false);
            documentBuilderFactory.setXIncludeAware(false);
            documentBuilderFactory.setExpandEntityReferences(false);
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }

    private static void jsonToXmlStr(JSONObject jObj, StringBuffer buffer, StringBuffer tab) {
        Set<Map.Entry<String, Object>> se = jObj.entrySet();
        StringBuffer nowTab = new StringBuffer(tab.toString());
        for (Map.Entry<String, Object> en : se) {
            if (en == null || en.getValue() == null) continue;
            if (en.getValue() instanceof JSONObject) {
                buffer.append(tab).append("<").append(en.getKey()).append(">\n");
                JSONObject jo = jObj.getJSONObject(en.getKey());
                jsonToXmlStr(jo, buffer, nowTab.append("\t"));
                buffer.append(tab).append("</").append(en.getKey()).append(">\n");
            } else if (en.getValue() instanceof JSONArray) {
                JSONArray jarray = jObj.getJSONArray(en.getKey());
                for (int i = 0; i < jarray.size(); i++) {
                    buffer.append(tab).append("<").append(en.getKey()).append(">\n");
                    if (StringUtils.isNotBlank(jarray.getString(i))) {
                        JSONObject jsonobject = jarray.getJSONObject(i);
                        jsonToXmlStr(jsonobject, buffer, nowTab.append("\t"));
                        buffer.append(tab).append("</").append(en.getKey()).append(">\n");
                    }
                }
            } else if (en.getValue() instanceof String) {
                buffer.append(tab).append("<").append(en.getKey()).append(">").append(en.getValue());
                buffer.append("</").append(en.getKey()).append(">\n");
            }
        }
    }

    public static String jsonToXmlStr(JSONObject jObj) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        try {
            jsonToXmlStr(jObj, buffer, new StringBuffer(""));
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
        return buffer.toString();
    }

    //  传入完整的 xml 文本，转换成 json 对象
    public static JSONObject xmlConvertJson(String xml) {
        if (StringUtils.isBlank(xml)) return null;
        xml = delXmlHeader(xml);
        if (xml == null) return null;
        if (stringToDocument(xml) == null) {
            LogUtil.error("xml内容转换失败！");
            return null;
        }
        Element node = stringToDocument(xml).getRootElement();
        JSONObject result = getJsonObjectByDC(node);
        return result;
    }

    @Nullable
    public static String delXmlHeader(String xml) {
        int begin = xml.indexOf("?>");
        if (begin != -1) {
            if (begin + 2 >= xml.length()) {
                return null;
            }
            xml = xml.substring(begin + 2);
        }   //  <?xml version="1.0" encoding="utf-8"?> 若存在，则去除
        String rgex = ">";
        Pattern pattern = Pattern.compile(rgex);
        Matcher m = pattern.matcher(xml);
        xml = m.replaceAll("> ");
        rgex = "\\s*</";
        pattern = Pattern.compile(rgex);
        m = pattern.matcher(xml);
        xml = m.replaceAll(" </");
        return xml;
    }

    private static JSONObject getJsonObjectByDC(Element node) {
        JSONObject result = new JSONObject();
        List<Element> listElement = node.elements();// 所有一级子节点的list
        if (!listElement.isEmpty()) {
            List<JSONObject> list = new LinkedList<>();
            for (Element e : listElement) {// 遍历所有一级子节点
                JSONObject jsonObject = getJsonObjectByDC(e);
                //加xml标签上的属性 eg: <field length="2" scale="0" type="string">RB</field>
                //这里添加 length scale type
                JSONArray attributeArray = new JSONArray();
                if (!e.attributes().isEmpty()) {
                    JSONObject attributeJson = new JSONObject();
                    for (Attribute attribute : e.attributes()) {
                        attributeJson.put(attribute.getName(), attribute.getValue());
                    }
                    attributeArray.add(attributeJson);
                    jsonObject.put("attribute", attributeArray);
                }
                list.add(jsonObject);
            }
            if (list.size() == 1) {
                result.put(node.getName(), list.get(0));
            } else {
                result.put(node.getName(), list);
            }
        } else {
            if (!StringUtils.isAllBlank(node.getName(), node.getText())) {
                result.put(node.getName(), node.getText());
            }
        }
        return result;
    }

    //  预处理 xml 文本，转换成 tag + data 的列表
    private static List<String> preProcessXml(String xml) {
        int begin = xml.indexOf("?>");
        if (begin != -1) {
            if (begin + 2 >= xml.length()) {
                return null;
            }
            xml = xml.substring(begin + 2);
        }   //  <?xml version="1.0" encoding="utf-8"?> 若存在，则去除
        String rgex = ">";
        Pattern pattern = Pattern.compile(rgex);
        Matcher m = pattern.matcher(xml);
        xml = m.replaceAll("> ");
        rgex = "\\s*</";
        pattern = Pattern.compile(rgex);
        m = pattern.matcher(xml);
        xml = m.replaceAll(" </");
        return Arrays.asList(xml.split(" "));
    }

    //  传入预处理的列表，返回转换成功的 json 对象
    private static Object XmlTagToJsonObject(List<String> list) {
        if (list == null || list.size() == 0)
            return null;
        Stack<String> tagStack = new Stack<>(); //  tag 栈
        Stack<Object> valueStack = new Stack<>();   //  数据栈
        valueStack.push(new JSONObject());  //  最终结果将存放在第一个入栈的元素中
        for (String item : list) {
            String beginTag = isBeginTag(item), endTag = isEndTag(item);    //  判断当前 tag 是开始还是结尾
            if (beginTag != null) {
                tagStack.push(beginTag);
                valueStack.push(new JSONObject());
            } else if (endTag != null) {
                if (endTag.equals(tagStack.peek())) { //  是一对 tag
                    Object topValue = valueStack.peek();
                    if (topValue instanceof String) {    //  栈顶是纯数据 xml 节点
                        valueStack.pop();
                    }
                    valueStack.pop();
                    if (valueStack.peek() instanceof JSONObject) {
                        ((JSONObject) valueStack.peek()).put(tagStack.peek(), topValue);
                    }
                    tagStack.pop();
                }
            } else {
                valueStack.push(item);
            }
        }
        if (valueStack.empty())
            return null;
        return valueStack.peek();
    }

    private static String isEndTag(String tagLine) {
        String rgex = "</(\\w*)>";
        Pattern pattern = Pattern.compile(rgex);// 匹配的模式    
        Matcher m = pattern.matcher(tagLine);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String isBeginTag(String tagLine) {
        String rgex = "<(\\w*)>";
        Pattern pattern = Pattern.compile(rgex);// 匹配的模式    
        Matcher m = pattern.matcher(tagLine);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static Document stringToDocument(String xml) {
        try {
            return EngineSourceParserFactory.getDocument(new ByteArrayInputStream(xml.getBytes("utf-8")));
        } catch (Exception e) {
            LogUtil.error(e);
            return null;
        }
    }

    public static JSONObject xmlStringToJSONObject(String xml) {
        try {
            return elementToJSONObject(stringToDocument(xml).getRootElement());
        } catch (Exception e) {
            LogUtil.error(e);
            return null;
        }
    }

    public static JSONObject elementToJSONObject(Element node) {
        JSONObject result = new JSONObject();

        List<Element> listElement = node.elements();// 所有一级子节点的list
        if (!listElement.isEmpty()) {
            JSONArray jsonArray = new JSONArray();
            for (Element e : listElement) {// 遍历所有一级子节点
                JSONObject jsonObject = elementToJSONObject(e);
                jsonArray.add(jsonObject);
            }
            if (jsonArray.size() == 1) {
                result.put(node.getName(), jsonArray.getJSONObject(0));
            } else {
                result.put(node.getName(), jsonArray);
            }
        } else {
            if (!StringUtils.isAllBlank(node.getName(), node.getText())) {
                result.put(node.getName(), node.getText());
            }
        }
        return result;
    }

    private static final String ENCODING = "UTF-8";

    /**
     * JSON对象转漂亮的xml字符串
     *
     * @param json JSON对象
     * @return 漂亮的xml字符串
     */
    public static String jsonToPrettyXml(JSONObject json) {
        Document document = jsonToDocument(json);

        /* 格式化xml */
        OutputFormat format = OutputFormat.createPrettyPrint();

        // 设置缩进为4个空格
        format.setIndent(StringUtils.SPACE);
        format.setIndentSize(4);

        StringWriter formatXml = new StringWriter();
        XMLWriter writer = new XMLWriter(formatXml, format);
        try {
            writer.write(document);
        } catch (IOException e) {
            LogUtil.error("This object can not convert to xml", e);
        }

        return formatXml.toString();
    }

    /**
     * JSON对象转Document对象
     *
     * @param json JSON对象
     * @return Document对象
     */
    public static Document jsonToDocument(JSONObject json) {
        Document document = DocumentHelper.createDocument();
        document.setXMLEncoding(ENCODING);
        setDocument(json, document);
        return document;
    }

    private static void setDocument(JSONObject json, Document document) {
        for (String key : json.keySet()) {
            if (json.get(key) instanceof LinkedList<?>) {
                for (Object o : ((LinkedList<?>) json.get(key))) {
                    setDocument((JSONObject) o, document);
                }
            } else {
                Element root = jsonToElement(json.get(key), key);
                document.add(root);
            }


        }
    }

    /**
     * JSON对象转Element对象
     *
     * @param json     JSON对象
     * @param nodeName 节点名称
     * @return Element对象
     */
    public static Element jsonToElement(Object json, String nodeName) {
        Element node = DocumentHelper.createElement(nodeName);
        if (json instanceof JSONObject) {
            delObject(json, node);
        }
        if (json instanceof List<?>) {
            ((List<?>) json).forEach(t -> {
                jsonToElement(t, nodeName);
            });
        }
        if (json instanceof String) {
            Element element = DocumentHelper.createElement(json.toString());
            element.setText(json.toString());
            node.add(element);
        }
        return node;
    }

    private static void delObject(Object json, Element node) {
        for (String key : ((JSONObject) json).keySet()) {
            Object child = ((JSONObject) json).get(key);
            if (child instanceof JSONObject) {
                node.add(jsonToElement(((JSONObject) json).get(key), key));
            } else if (child instanceof LinkedList<?>) {
                ((LinkedList<?>) child).forEach(t -> {
                    node.add(jsonToElement(t, key));
                });
            } else {
                Element element = DocumentHelper.createElement(key);
                element.setText(((JSONObject) json).get(key).toString());
                node.add(element);
            }
        }
    }


}
