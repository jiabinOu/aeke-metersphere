package io.metersphere.api.dto.definition.request.processors.pre;

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.annotation.JSONType;
import io.metersphere.api.dto.RunningParamKeys;
import io.metersphere.api.dto.definition.request.ParameterConfig;
import io.metersphere.api.dto.scenario.environment.EnvironmentConfig;
import io.metersphere.api.dto.shell.filter.ScriptFilter;
import io.metersphere.plugin.core.MsParameter;
import io.metersphere.plugin.core.MsTestElement;
import io.metersphere.utils.JMeterVars;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jmeter.modifiers.JSR223PreProcessor;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;

import java.util.Collection;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@JSONType(typeName = "JSR223PreProcessor")
public class MsJSR223PreProcessor extends MsTestElement {
    private String type = "JSR223PreProcessor";
    private String clazzName = MsJSR223PreProcessor.class.getCanonicalName();

    @JSONField(ordinal = 20)
    private String script;

    @JSONField(ordinal = 21)
    private String scriptLanguage;

    @Override
    public void toHashTree(HashTree tree, List<MsTestElement> hashTree, MsParameter msParameter) {
        ParameterConfig config = (ParameterConfig) msParameter;
        // 非导出操作，且不是启用状态则跳过执行
        if (!config.isOperating() && !this.isEnable()) {
            return;
        }
        ScriptFilter.verify(this.getScriptLanguage(), this.getName(), script);
        if (StringUtils.isEmpty(this.getEnvironmentId())) {
            if (config.getConfig() != null) {
                if (config.getProjectId() != null) {
                    String evnId = config.getConfig().get(config.getProjectId()).getApiEnvironmentid();
                    this.setEnvironmentId(evnId);
                } else {
                    Collection<EnvironmentConfig> evnConfigList = config.getConfig().values();
                    if (evnConfigList != null && !evnConfigList.isEmpty()) {
                        for (EnvironmentConfig configItem : evnConfigList) {
                            String evnId = configItem.getApiEnvironmentid();
                            this.setEnvironmentId(evnId);
                            break;
                        }
                    }
                }
            }
        }
        //替换环境变量
        if (StringUtils.isNotEmpty(script)) {
            script = StringUtils.replace(script, RunningParamKeys.API_ENVIRONMENT_ID, "\"" + RunningParamKeys.RUNNING_PARAMS_PREFIX + this.getEnvironmentId() + ".\"");
        }
        if (config.isOperating()) {
            if (StringUtils.isNotEmpty(script) && script.startsWith(JMeterVars.class.getCanonicalName())) {
                return;
            }
        }

        final HashTree jsr223PreTree = tree.add(getJSR223PreProcessor());
        if (CollectionUtils.isNotEmpty(hashTree)) {
            hashTree.forEach(el -> {
                el.toHashTree(jsr223PreTree, el.getHashTree(), config);
            });
        }
    }

    public JSR223PreProcessor getJSR223PreProcessor() {

        JSR223PreProcessor processor = new JSR223PreProcessor();
        processor.setEnabled(this.isEnable());
        if (StringUtils.isNotEmpty(this.getName())) {
            processor.setName(this.getName());
        } else {
            processor.setName("JSR223PreProcessor");
        }
        processor.setProperty(TestElement.TEST_CLASS, JSR223PreProcessor.class.getName());
        processor.setProperty(TestElement.GUI_CLASS, SaveService.aliasToClass("TestBeanGUI"));
        processor.setProperty("cacheKey", false);
        processor.setProperty("scriptLanguage", this.getScriptLanguage());
        if (StringUtils.isNotEmpty(this.getScriptLanguage()) && this.getScriptLanguage().equals("nashornScript")) {
            processor.setProperty("scriptLanguage", "nashorn");
        }
        if (StringUtils.isNotEmpty(this.getScriptLanguage()) && this.getScriptLanguage().equals("rhinoScript")) {
            processor.setProperty("scriptLanguage", "rhino");
        }
        if (StringUtils.isNotEmpty(this.getScriptLanguage()) && this.getScriptLanguage().equals("javascript")) {
            processor.setProperty("scriptLanguage", "rhino");
        }

        processor.setProperty("script", this.getScript());
        return processor;
    }

}
