package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmTreatmentTemplate;
import com.ruoyi.hospital.mapper.TcmTreatmentTemplateMapper;
import com.ruoyi.hospital.service.ITcmTreatmentTemplateService;

@Service
public class TcmTreatmentTemplateServiceImpl implements ITcmTreatmentTemplateService
{
    @Autowired
    private TcmTreatmentTemplateMapper templateMapper;
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final List<TcmTreatmentTemplate> DEFAULT_TEMPLATES = Arrays.asList(
            template("tmpl-01", "失眠标准方案", "失眠症", "内科", "适用于心脾两虚型失眠", "[\"百会\",\"神门\",\"三阴交\",\"安眠\",\"内关\",\"足三里\"]", "[\"formula-5\"]", "忌浓茶咖啡，睡前泡脚，规律作息"),
            template("tmpl-02", "颈椎病方案", "颈椎病", "骨伤科", "适用于颈型和神经根型颈椎病", "[\"风池\",\"天柱\",\"大椎\",\"后溪\",\"肩井\",\"合谷\",\"外关\"]", "[]", "注意颈部保暖，避免长时间低头，适当做颈部操"),
            template("tmpl-03", "慢性胃炎方案", "慢性胃炎", "内科", "适用于脾胃虚寒型胃炎", "[\"中脘\",\"足三里\",\"天枢\",\"脾俞\",\"胃俞\",\"内关\"]", "[\"formula-1\"]", "饮食清淡，忌辛辣生冷，定时定量进食"),
            template("tmpl-04", "月经不调方案", "月经不调", "妇科", "适用于气滞血瘀型月经不调", "[\"关元\",\"气海\",\"三阴交\",\"太冲\",\"血海\",\"合谷\"]", "[\"formula-3\"]", "经期注意保暖，避免剧烈运动，保持心情舒畅"),
            template("tmpl-05", "头痛方案", "头痛", "内科", "适用于各类头痛", "[\"百会\",\"太阳\",\"风池\",\"合谷\",\"太冲\",\"印堂\"]", "[\"formula-6\"]", "注意休息，避免风寒，保持充足睡眠"),
            template("tmpl-06", "腰痛方案", "腰痛", "骨伤科", "适用于寒湿腰痛和肾虚腰痛", "[\"肾俞\",\"命门\",\"委中\",\"腰阳关\",\"大肠俞\",\"环跳\"]", "[\"formula-8\"]", "避免久坐久站，注意腰部保暖，适当腰背肌锻炼"),
            template("tmpl-07", "感冒方案", "感冒", "内科", "适用于风寒感冒", "[\"风池\",\"大椎\",\"合谷\",\"列缺\",\"迎香\",\"风府\"]", "[\"formula-7\"]", "多饮温水，注意保暖休息，清淡饮食"),
            template("tmpl-08", "高血压方案", "高血压", "内科", "适用于肝阳上亢型高血压", "[\"太冲\",\"太溪\",\"曲池\",\"百会\",\"风池\",\"足三里\"]", "[\"formula-2\"]", "低盐饮食，避免情绪激动，监测血压，适当运动"),
            template("tmpl-09", "便秘方案", "便秘", "内科", "适用于气虚便秘和阴虚便秘", "[\"天枢\",\"大肠俞\",\"支沟\",\"上巨虚\",\"足三里\",\"照海\"]", "[\"formula-4\"]", "多食粗纤维食物，适量饮水，养成定时排便习惯"),
            template("tmpl-10", "肩周炎方案", "肩周炎", "骨伤科", "适用于肩关节周围炎", "[\"肩髃\",\"肩髎\",\"肩贞\",\"曲池\",\"合谷\",\"外关\",\"条口\"]", "[]", "坚持肩关节功能锻炼，注意保暖，避免过度劳累")
    );
    private final Object defaultTemplateSeedLock = new Object();
    private volatile boolean defaultTemplatesChecked;

    @Override
    public List<TcmTreatmentTemplate> selectTcmTreatmentTemplateList(TcmTreatmentTemplate t) {
        ensureDefaultTreatmentTemplates();
        return templateMapper.selectTcmTreatmentTemplateList(t);
    }
    @Override
    public TcmTreatmentTemplate selectTcmTreatmentTemplateById(String id) {
        return templateMapper.selectTcmTreatmentTemplateById(id);
    }
    @Override
    public int insertTcmTreatmentTemplate(TcmTreatmentTemplate t) {
        if (t.getId() == null || t.getId().isEmpty())
            t.setId(java.util.UUID.randomUUID().toString());
        t.setCreateTime(DateUtils.getNowDate());
        return templateMapper.insertTcmTreatmentTemplate(t);
    }
    @Override
    public int updateTcmTreatmentTemplate(TcmTreatmentTemplate t) {
        return templateMapper.updateTcmTreatmentTemplate(t);
    }
    @Override
    public TcmTreatmentTemplate softDeleteTcmTreatmentTemplate(String id) {
        TcmTreatmentTemplate t = templateMapper.selectTcmTreatmentTemplateById(id);
        if (t == null) throw new ServiceException("模板不存在");
        t.setDeletedAt(new SimpleDateFormat(DATETIME_FORMAT).format(new Date()));
        t.setIsActive(0);
        templateMapper.updateTcmTreatmentTemplate(t);
        return t;
    }
    @Override
    public TcmTreatmentTemplate restoreTcmTreatmentTemplate(String id) {
        TcmTreatmentTemplate t = templateMapper.selectTcmTreatmentTemplateById(id);
        if (t == null) throw new ServiceException("模板不存在");
        t.setDeletedAt(null); t.setIsActive(1);
        templateMapper.updateTcmTreatmentTemplate(t);
        return t;
    }
    @Override
    public int hardDeleteTcmTreatmentTemplate(String id) {
        TcmTreatmentTemplate t = templateMapper.selectTcmTreatmentTemplateById(id);
        if (t == null) throw new ServiceException("模板不存在");
        if (t.getDeletedAt() == null || t.getDeletedAt().isEmpty())
            throw new ServiceException("该记录未被软删除");
        try {
            Date d = new SimpleDateFormat(DATETIME_FORMAT).parse(t.getDeletedAt());
            if (System.currentTimeMillis() - d.getTime() < 90L*24*60*60*1000)
                throw new ServiceException("删除不满3个月");
        } catch (ServiceException e) { throw e; }
          catch (Exception e) { throw new ServiceException("时间解析错误"); }
        return templateMapper.deleteTcmTreatmentTemplateById(id);
    }

    private void ensureDefaultTreatmentTemplates()
    {
        if (defaultTemplatesChecked)
        {
            return;
        }
        synchronized (defaultTemplateSeedLock)
        {
            if (defaultTemplatesChecked)
            {
                return;
            }
            List<TcmTreatmentTemplate> activeTemplates = templateMapper.selectTcmTreatmentTemplateList(new TcmTreatmentTemplate());
            Set<String> activeNames = new HashSet<>();
            if (activeTemplates != null)
            {
                for (TcmTreatmentTemplate existing : activeTemplates)
                {
                    if (existing != null && existing.getName() != null)
                    {
                        activeNames.add(existing.getName());
                    }
                }
            }
            for (TcmTreatmentTemplate defaultTemplate : DEFAULT_TEMPLATES)
            {
                if (templateMapper.selectTcmTreatmentTemplateById(defaultTemplate.getId()) != null
                        || activeNames.contains(defaultTemplate.getName()))
                {
                    continue;
                }
                TcmTreatmentTemplate copy = copyDefaultTemplate(defaultTemplate);
                copy.setCreateTime(DateUtils.getNowDate());
                templateMapper.insertTcmTreatmentTemplate(copy);
            }
            defaultTemplatesChecked = true;
        }
    }

    private static TcmTreatmentTemplate copyDefaultTemplate(TcmTreatmentTemplate source)
    {
        return template(
                source.getId(),
                source.getName(),
                source.getDisease(),
                source.getCategory(),
                source.getDescription(),
                source.getAcupointsJson(),
                source.getFormulaIds(),
                source.getAdvice());
    }

    private static TcmTreatmentTemplate template(String id, String name, String disease, String category,
            String description, String acupointsJson, String formulaIds, String advice)
    {
        TcmTreatmentTemplate template = new TcmTreatmentTemplate();
        template.setId(id);
        template.setName(name);
        template.setDisease(disease);
        template.setCategory(category);
        template.setDescription(description);
        template.setAcupointsJson(acupointsJson);
        template.setFormulaIds(formulaIds);
        template.setAdvice(advice);
        template.setIsActive(1);
        return template;
    }
}
