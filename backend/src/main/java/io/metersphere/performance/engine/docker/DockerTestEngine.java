package io.metersphere.performance.engine.docker;

import com.alibaba.fastjson.JSON;
import io.metersphere.base.domain.LoadTestReportResult;
import io.metersphere.base.domain.LoadTestReportWithBLOBs;
import io.metersphere.base.domain.TestResource;
import io.metersphere.base.mapper.LoadTestReportResultMapper;
import io.metersphere.commons.constants.ReportKeys;
import io.metersphere.commons.constants.ResourceStatusEnum;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.LocalAddressUtils;
import io.metersphere.commons.utils.UrlTestUtils;
import io.metersphere.config.KafkaProperties;
import io.metersphere.controller.ResultHolder;
import io.metersphere.dto.BaseSystemConfigDTO;
import io.metersphere.dto.NodeDTO;
import io.metersphere.i18n.Translator;
import io.metersphere.performance.engine.AbstractEngine;
import io.metersphere.performance.engine.request.StartTestRequest;
import io.metersphere.service.SystemParameterService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DockerTestEngine extends AbstractEngine {
    private static final String BASE_URL = "http://%s:%d";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final RestTemplate restTemplateWithTimeOut = new RestTemplate();

    static {
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectionRequestTimeout(2000);
        httpRequestFactory.setConnectTimeout(2000);
        httpRequestFactory.setReadTimeout(10000);
        restTemplateWithTimeOut.setRequestFactory(httpRequestFactory);
    }

    public DockerTestEngine(LoadTestReportWithBLOBs loadTestReport) {
        this.init(loadTestReport);
    }

    @Override
    protected void init(LoadTestReportWithBLOBs loadTestReport) {
        super.init(loadTestReport);
    }

    @Override
    public void start() {
        Integer runningSumThreadNum = getRunningThreadNum();
        int totalThreadNum = resourceList.stream()
                .filter(r -> ResourceStatusEnum.VALID.name().equals(r.getStatus()))
                .map(r -> JSON.parseObject(r.getConfiguration(), NodeDTO.class).getMaxConcurrency())
                .reduce(Integer::sum)
                .orElse(0);
        if (threadNum > totalThreadNum - runningSumThreadNum) {
            MSException.throwException(Translator.get("max_thread_insufficient"));
        }
        Object[] resourceRatios = resourceList.stream()
                .filter(r -> ResourceStatusEnum.VALID.name().equals(r.getStatus()))
                .map(r -> JSON.parseObject(r.getConfiguration(), NodeDTO.class).getMaxConcurrency())
                .map(r -> r * 1.0 / totalThreadNum)
                .map(r -> String.format("%.2f", r))
                .toArray();
        // 保存一个 completeCount
        LoadTestReportResult completeCount = new LoadTestReportResult();
        completeCount.setId(UUID.randomUUID().toString());
        completeCount.setReportId(loadTestReport.getId());
        completeCount.setReportKey(ReportKeys.ReportCompleteCount.name());
        completeCount.setReportValue("" + resourceRatios.length); // 初始化一个 completeCount, 这个值用在data-streaming中
        LoadTestReportResultMapper loadTestReportResultMapper = CommonBeanFactory.getBean(LoadTestReportResultMapper.class);
        loadTestReportResultMapper.insertSelective(completeCount);

        for (int i = 0, size = resourceList.size(); i < size; i++) {
            runTest(resourceList.get(i), resourceRatios, i);
        }
    }

    private void runTest(TestResource resource, Object[] ratios, int resourceIndex) {

        String configuration = resource.getConfiguration();
        NodeDTO node = JSON.parseObject(configuration, NodeDTO.class);
        String nodeIp = node.getIp();
        Integer port = node.getPort();

        BaseSystemConfigDTO baseInfo = CommonBeanFactory.getBean(SystemParameterService.class).getBaseInfo();
        KafkaProperties kafkaProperties = CommonBeanFactory.getBean(KafkaProperties.class);
        String metersphereUrl = "http://localhost:8081"; // 占位符
        if (baseInfo != null) {
            metersphereUrl = baseInfo.getUrl();
        }
        // docker 不能从 localhost 中下载文件, 本地开发
        if (StringUtils.contains(metersphereUrl, "http://localhost") ||
                StringUtils.contains(metersphereUrl, "http://127.0.0.1")) {
            metersphereUrl = "http://" + LocalAddressUtils.getIpAddress("en0") + ":8081";
        }

        String jmeterPingUrl = metersphereUrl + "/jmeter/ping"; // 检查下载地址是否正确
        if (!UrlTestUtils.testUrlWithTimeOut(jmeterPingUrl, 1000)) {
            MSException.throwException(Translator.get("run_load_test_file_init_error"));
        }

        Map<String, String> env = new HashMap<>();
        env.put("RATIO", StringUtils.join(ratios, ","));
        env.put("RESOURCE_INDEX", "" + resourceIndex);
        env.put("METERSPHERE_URL", metersphereUrl);
        env.put("START_TIME", "" + System.currentTimeMillis());
        env.put("TEST_ID", this.loadTestReport.getTestId());
        env.put("REPORT_ID", this.loadTestReport.getId());
        env.put("BOOTSTRAP_SERVERS", kafkaProperties.getBootstrapServers());
        env.put("LOG_TOPIC", kafkaProperties.getLog().getTopic());
        env.put("JMETER_REPORTS_TOPIC", kafkaProperties.getReport().getTopic());
        env.put("RESOURCE_ID", resource.getId());
        env.put("THREAD_NUM", "0");// 传入0表示不用修改线程数
        env.put("HEAP", HEAP);
        env.put("GC_ALGO", GC_ALGO);
        env.put("GRANULARITY", performanceTestService.getGranularity(this.loadTestReport.getId()).toString());
        env.put("BACKEND_LISTENER", resourcePool.getBackendListener().toString());


        StartTestRequest startTestRequest = new StartTestRequest();
        startTestRequest.setImage(JMETER_IMAGE);
        startTestRequest.setEnv(env);

        String uri = String.format(BASE_URL + "/jmeter/container/start", nodeIp, port);
        try {
            ResultHolder result = restTemplate.postForObject(uri, startTestRequest, ResultHolder.class);
            if (result == null) {
                MSException.throwException(Translator.get("start_engine_fail"));
            }
            if (!result.isSuccess()) {
                MSException.throwException(result.getMessage());
            }
        } catch (MSException e) {
            throw e;
        } catch (Exception e) {
            MSException.throwException("Please check node-controller status.");
        }
    }

    @Override
    public void stop() {
        String testId = loadTestReport.getTestId();
        this.resourceList.forEach(r -> {
            NodeDTO node = JSON.parseObject(r.getConfiguration(), NodeDTO.class);
            String ip = node.getIp();
            Integer port = node.getPort();

            String uri = String.format(BASE_URL + "/jmeter/container/stop/" + testId, ip, port);
            try {
                ResultHolder result = restTemplateWithTimeOut.getForObject(uri, ResultHolder.class);
                if (result == null) {
                    MSException.throwException(Translator.get("container_delete_fail"));
                }
                if (!result.isSuccess()) {
                    MSException.throwException(result.getMessage());
                }
            } catch (MSException e) {
                throw e;
            } catch (Exception e) {
                MSException.throwException("Please check node-controller status.");
            }
        });
    }

}
