package io.metersphere.base.mapper.ext;

import io.metersphere.base.domain.CustomFieldTemplate;
import io.metersphere.dto.CustomFieldDao;
import io.metersphere.dto.CustomFieldTemplateDao;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtCustomFieldTemplateMapper {
    List<String> getCustomFieldIds(@Param("templateId") String templateId);

    List<CustomFieldTemplateDao> list(@Param("request") CustomFieldTemplate request);

    List<CustomFieldDao> lisSimple(@Param("request") CustomFieldTemplate request);

    Long getLastOrder(@Param("templateId") String templateId, @Param("baseOrder") Long baseOrder);
}
