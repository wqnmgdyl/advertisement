package com.kh.ad.handler;

import com.alibaba.fastjson.JSON;
import com.kh.ad.dump.table.*;
import com.kh.ad.index.DataTable;
import com.kh.ad.index.IndexAware;
import com.kh.ad.index.adplan.AdPlanIndex;
import com.kh.ad.index.adplan.AdPlanObject;
import com.kh.ad.index.adunit.AdUnitIndex;
import com.kh.ad.index.adunit.AdUnitObject;
import com.kh.ad.index.creative.CreativeIndex;
import com.kh.ad.index.creative.CreativeObject;
import com.kh.ad.index.creativeunit.CreativeUnitIndex;
import com.kh.ad.index.creativeunit.CreativeUnitObject;
import com.kh.ad.index.district.UnitDistrictIndex;
import com.kh.ad.index.interest.UnitItIndex;
import com.kh.ad.index.keyword.UnitKeywordIndex;
import com.kh.ad.mysql.constant.OpType;
import com.kh.ad.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 1.索引之间存在着层级的划分，也就是以来关系的划分
 * 2.加载全量索引其实是增量索引“添加”的一种特殊实现
 *
 * @author han.ke
 */
@Slf4j
public class AdLevelDataHandler {

    public static void handleLevel2(AdPlanTable planTable, OpType type) {
        AdPlanObject planObject = new AdPlanObject(planTable.getId(),
                planTable.getUserId(),
                planTable.getPlanStatus(),
                planTable.getStartDate(),
                planTable.getEndDate());
        handleBinLogEvent(DataTable.of(AdPlanIndex.class),
                planObject.getPlanId(),
                planObject,
                type);
    }

    public static void handleLevel2(AdCreativeTable creativeTable, OpType type) {
        CreativeObject creativeObject = new CreativeObject(creativeTable.getAdId(),
                creativeTable.getName(),
                creativeTable.getType(),
                creativeTable.getMaterialType(),
                creativeTable.getHeight(),
                creativeTable.getWidth(),
                creativeTable.getAuditStatus(),
                creativeTable.getAdUrl());
        handleBinLogEvent(DataTable.of(CreativeIndex.class),
                creativeObject.getAdId(),
                creativeObject,
                type);
    }

    public static void handleLevel3(AdUnitTable unitTable, OpType type) {
        AdPlanObject adPlanObject = DataTable.of(
                AdPlanIndex.class).get(unitTable.getPlanId());
        if (null == adPlanObject) {
            log.error("handleLevel3 found AdPlanObject error: {}", unitTable.getPlanId());
            return;
        }
        AdUnitObject unitObject = new AdUnitObject(unitTable.getUnitId(),
                unitTable.getUnitStatus(),
                unitTable.getPositionType(),
                unitTable.getPlanId(),
                adPlanObject);

        handleBinLogEvent(DataTable.of(AdUnitIndex.class),
                unitTable.getUnitId(),
                unitObject,
                type);
    }

    public static void handleLevel3(AdCreativeUnitTable creativeUnitTable, OpType type) {
        if (type == OpType.UPDATE) {
            log.error("CreativeUnitIndex not support update");
            return;
        }
        AdUnitObject unitObject = DataTable.of(AdUnitIndex.class).
                get(creativeUnitTable.getUnitId());
        CreativeObject creativeObject = DataTable.of(CreativeIndex.class).
                get(creativeUnitTable.getAdId());
        if (null == unitObject || null == creativeObject) {
            log.error("AdCreativeUnitTable index error: {}",
                    JSON.toJSONString(creativeUnitTable));
            return;
        }
        CreativeUnitObject creativeUnitObject = new CreativeUnitObject(
                creativeUnitTable.getAdId(),
                creativeUnitTable.getUnitId());
        handleBinLogEvent(DataTable.of(CreativeUnitIndex.class),
                CommonUtils.stringConcat(creativeUnitObject.getAdId().toString(),
                        creativeUnitObject.getUnitId().toString()),
                creativeUnitObject,
                type);

    }

    public static void handleLevel4(AdUnitDistrictTable unitDistrictTable,
                                    OpType type) {
        if (type == OpType.UPDATE) {
            log.error("district index can not support update");
            return;
        }

        AdUnitObject unitObject = DataTable.of(AdUnitIndex.class).
                get(unitDistrictTable.getUnitId());
        if (unitObject == null) {
            log.error("AdUnitDistrictTable index error: {}", unitDistrictTable.getUnitId());
            return;
        }
        String key = CommonUtils.stringConcat(
                unitDistrictTable.getProvince(),
                unitDistrictTable.getCity());
        Set<Long> value = new HashSet<>(
                Collections.singleton(unitDistrictTable.getUnitId())
        );
        handleBinLogEvent(DataTable.of(UnitDistrictIndex.class),
                key, value, type);
    }

    public static void handleLevel4(AdUnitItTable unitItTable, OpType type) {
        if (type == OpType.UPDATE) {
            log.error("it index can not support update");
            return;
        }
        AdUnitObject unitObject = DataTable.of(AdUnitIndex.class).
                get(unitItTable.getUnitId());
        if (unitObject == null) {
            log.error("AdUnitItTable index error: {}", unitItTable.getUnitId());
            return;
        }
        Set<Long> value = new HashSet<>(Collections.singleton(unitItTable.getUnitId()));
        handleBinLogEvent(DataTable.of(UnitItIndex.class),
                unitItTable.getItTag(),
                value,
                type);
    }

    public static void handleLevel4(AdUnitKeywordTable keywordTable, OpType type) {
        if (type == OpType.UPDATE) {
            log.error("keyword index can not support update");
            return;
        }
        AdUnitObject unitObject = DataTable.of(AdUnitIndex.class).get(keywordTable.getUnitId());
        if (unitObject == null) {
            log.error("AdUnitKeywordTable index error: {}",
                    keywordTable.getUnitId());
            return;
        }
        Set<Long> value = new HashSet<>(Collections.singleton(keywordTable.getUnitId()));
        handleBinLogEvent(DataTable.of(UnitKeywordIndex.class),
                keywordTable.getKeyword(),
                value,
                type);
    }

    private static <K, V> void handleBinLogEvent(IndexAware<K, V> index,
                                                 K key,
                                                 V value,
                                                 OpType type) {
        switch (type) {
            case ADD:
                index.add(key, value);
                break;
            case UPDATE:
                index.update(key, value);
                break;
            case DELETE:
                index.delete(key, value);
                break;
            default:
                break;
        }
    }
}
