package io.metersphere.notice.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import io.metersphere.commons.constants.NoticeConstants;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.notice.sender.NoticeModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;

import java.util.LinkedHashMap;
import java.util.Map;

public class LarkClient {
    public static SendResult send(String webhook, NoticeModel noticeModel, String context) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        CloseableHttpResponse response = null;

        Map<String, Object> mp = new LinkedHashMap<>();
        JSONObject js = new JSONObject();

        // 添加 msg_type
        mp.put("msg_type", "interactive");

        // 创建 schema 部分
        js.put("schema", "2.0");

        // 创建 config 部分
        JSONObject configJS = new JSONObject();

        // 创建 style 部分
        JSONObject styleJS = new JSONObject();

        // 创建 text_size 部分
        JSONObject sizeJS = new JSONObject();

        // 创建 normal_v2 部分
        JSONObject normalJS = new JSONObject();
        normalJS.put("default", "normal");
        normalJS.put("pc", "normal");
        normalJS.put("mobile", "heading");

        // 将 normalJS 放入 sizeJS
        sizeJS.put("normal_v2", normalJS);

        // 将 sizeJS 放入 styleJS
        styleJS.put("text_size", sizeJS);

        // 将 styleJS 放入 configJS
        configJS.put("update_multi", true);
        configJS.put("style", styleJS);

        // 将 configJS 放入 js
        js.put("config", configJS);

        // 创建 body 部分
        JSONObject bodyJS = new JSONObject();
        bodyJS.put("direction", "vertical");
        bodyJS.put("padding", "12px 12px 12px 12px");

        // 创建 elements 数组
        JSONArray elements = new JSONArray();

        // 创建第一个元素 (markdown)
        JSONObject markdownElement = new JSONObject();
        markdownElement.put("tag", "markdown");
        markdownElement.put("content", context);
        markdownElement.put("text_align", "left");
        markdownElement.put("text_size", "normal_v2");
        markdownElement.put("margin", "0px 0px 0px 0px");
        elements.add(markdownElement);

        // 创建第二个元素 (button)
        JSONObject buttonElement = new JSONObject();
        buttonElement.put("tag", "button");

        // 创建 button text
        JSONObject buttonText = new JSONObject();
        buttonText.put("tag", "plain_text");
        buttonText.put("content", "📖历史报告");
        buttonElement.put("text", buttonText);

        buttonElement.put("type", "default");
        buttonElement.put("width", "default");
        buttonElement.put("size", "medium");

        // 创建行为部分
        JSONArray behaviors = new JSONArray();
        JSONObject behavior = new JSONObject();
        behavior.put("type", "open_url");
        behavior.put("default_url", "http://192.168.190.61:8081/#/api/automation/report");
        behavior.put("pc_url", "");
        behavior.put("ios_url", "");
        behavior.put("android_url", "");
        behaviors.add(behavior);

        buttonElement.put("behaviors", behaviors);
        buttonElement.put("margin", "0px 0px 0px 0px");

        // 将 button 添加到 elements 数组中
        elements.add(buttonElement);

        // 将 elements 添加到 bodyJS 中
        bodyJS.put("elements", elements);

        // 将 bodyJS 放入 js
        js.put("body", bodyJS);

        // 创建 header 部分
        JSONObject headerJS = new JSONObject();
        JSONObject title = new JSONObject();
        title.put("tag", "plain_text");
        title.put("content", "接口自动化测试报告");

        JSONObject subtitle = new JSONObject();
        subtitle.put("tag", "plain_text");
        subtitle.put("content", "");

        headerJS.put("title", title);
        headerJS.put("subtitle", subtitle);

        System.out.println(NoticeConstants.Event.EXECUTE_SUCCESSFUL);
        if (StringUtils.equals(noticeModel.getEvent(), NoticeConstants.Event.EXECUTE_SUCCESSFUL)) {
            headerJS.put("template", "green");
        } else {
            headerJS.put("template", "red");
        }
        headerJS.put("padding", "12px 12px 12px 12px");

        // 将 header 放入 js
        js.put("header", headerJS);

        // 将 js 放入 mp
        mp.put("card", js);

        // 输出为 UTF-8 编码
        String output = new JSONObject(mp).toString();


        SendResult sendResult = new SendResult();
        try {
            // 创建Http Post请求
            HttpPost httpPost = new HttpPost(webhook);
            // 创建请求内容
            StringEntity entity = new StringEntity(output, ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);
            // 执行http请求
            response = httpClient.execute(httpPost);
           /* if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                String result = EntityUtils.toString(response.getEntity());
                JSONObject obj = JSONObject.parseObject(result);
                Integer errcode = obj.getInteger("errcode");
                sendResult.setErrorCode(errcode);
                sendResult.setErrorMsg(obj.getString("errmsg"));
                sendResult.setIsSuccess(errcode.equals(0));
            }*/
        } catch (Exception e) {
            LogUtil.error(e);
        } finally {
            try {
                response.close();
            } catch (IOException e) {
                LogUtil.error(e);
            }
        }
        return sendResult;
    }
}
