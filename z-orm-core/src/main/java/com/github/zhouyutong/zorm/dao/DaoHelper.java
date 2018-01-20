package com.github.zhouyutong.zorm.dao;

import com.github.zhouyutong.zorm.annotation.Dao;
import com.github.zhouyutong.zorm.annotation.PK;
import com.github.zhouyutong.zorm.entity.IdEntity;
import com.github.zhouyutong.zorm.exception.DaoException;
import com.github.zhouyutong.zorm.exception.DaoMethodParameterException;
import com.github.zhouyutong.zorm.query.*;
import com.github.zhouyutong.zorm.utils.StrUtils;
import com.google.common.collect.Lists;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * 通用的help方法放入此类
 * 主键字段类型的支持都在此类中
 *
 * @Author zhouyutong
 * @Date 2017/4/27
 */
public class DaoHelper {
    private DaoHelper() {
    }

    public static Update entity2Update(Object entity, List<String> propetyList) {
        Update update = new Update();

        List<Field> fieldList = Lists.newArrayList();
        fieldList.addAll(Arrays.asList(entity.getClass().getDeclaredFields()));

        for (Field field : fieldList) {
            if (isFinalOrStatic(field)) {
                continue;
            }
            String propertyName = field.getName();
            //propetyList为空所有属性都需要更新，否则只更新包含的属性
            if (CollectionUtils.isEmpty(propetyList) || propetyList.contains(propertyName)) {
                update.set(propertyName, getColumnValue(field, entity));
            }
        }

        return update;
    }

    /**
     * 根据field得到对应值
     *
     * @param field - 字段对象
     * @param bean  - 对应的bean
     * @return - 返回filed值
     */
    public static Object getColumnValue(Field field, Object bean) {
        field.setAccessible(true);
        try {
            return field.get(bean);
        } catch (Exception e) {
            throw new DaoException("无法获取entity[" + bean.getClass().getName() + "]的属性[" + field.getName() + "]的值", e);
        }
    }

    /**
     * 根据field得到对应值
     *
     * @param fieldName - 字段对象
     * @param bean      - 对应的bean
     * @return - 返回filed值
     */
    public static Object getColumnValue(String fieldName, Object bean) {
        try {
            Field field = bean.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(bean);
        } catch (Exception e) {
            throw new DaoException("无法获取entity[" + bean.getClass().getName() + "]的属性[" + fieldName + "]的值", e);
        }
    }

    /**
     * 根据field得到对应值
     *
     * @param field - 字段对象
     * @param bean  - 对应的bean
     * @return - 返回filed值
     */
    public static void setColumnValue(Field field, Object bean, Object v) {
        field.setAccessible(true);
        try {
            field.set(bean, v);
        } catch (Exception e) {
            throw new DaoException("无法设置entity[" + bean.getClass().getName() + "]的属性[" + field.getName() + "],值[" + v + "]", e);
        }
    }

    /**
     * 判断某个field是否常量或静态变量
     *
     * @param field
     * @return
     */
    public static boolean isFinalOrStatic(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers);
    }

    /**
     * 校验query
     */
    public static void checkArgumentCriteria(Criteria criteria) {
        if (criteria == null) {
            throw new DaoMethodParameterException("Param criteria must be not null");
        }
    }

    /**
     * 校验query
     */
    public static void checkArgumentQuery(Query query) {
        if (query == null) {
            throw new DaoMethodParameterException("Param query must be not null");
        }
    }

    /**
     * 校验id
     */
    public static void checkArgumentId(Serializable id) {
        if (id == null) {
            throw new DaoMethodParameterException("Param id must be not null");
        }

        if (id instanceof Long) {
            if (((Long) id).longValue() < 0) {
                throw new DaoMethodParameterException("Param id must be >= 0");
            }
            return;
        }

        if (id instanceof String) {
            if (StringUtils.isBlank((String) id)) {
                throw new DaoMethodParameterException("Param id must be not empty");
            }
            return;
        }

        throw new DaoMethodParameterException("Param id's type must be long or String");

    }

    /**
     * 校验ids
     */
    public static void checkArgumentIds(List<Serializable> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            throw new DaoMethodParameterException("Param ids must be not null and empty");
        }
        checkArgumentId(ids.get(0));
    }

    /**
     * 校验pageable
     */
    public static void checkArgumentPageable(Pageable pageable) {
        if (pageable == null) {
            throw new DaoMethodParameterException("Param pageable must be not null");
        }
    }

    /**
     * 校验fields
     */
    public static void checkArgumentFields(List<String> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            throw new DaoMethodParameterException("Param fields must be not null and empty");
        }
    }

    /**
     * 校验orderBys
     */
    public static void checkArgumentOrderBys(List<OrderBy> orderBys) {
        if (CollectionUtils.isEmpty(orderBys)) {
            throw new DaoMethodParameterException("Param orderBys must be not null and empty");
        }
    }

    /**
     * 查询前的校验
     */
    public static void checkArgument(String sql) {
        if (StringUtils.isBlank(sql)) {
            throw new DaoMethodParameterException("Param sql must be not null and empty");
        }
    }

    /**
     * 更新操作前的校验
     */
    public static void checkArgumentEntity(Object entity) {
        if (entity == null) {
            throw new DaoMethodParameterException("Param entity must be not null");
        }
    }

    /**
     * 更新操作前的校验
     */
    public static void checkArgumentUpdate(Update update) {
        if (update == null) {
            throw new DaoMethodParameterException("Param update must be not null");
        }
        if (update.getSetMap().isEmpty()) {
            throw new DaoMethodParameterException("Param update must be set");
        }
    }

    /**
     * 校验daoClass必须符合框架的规范
     *
     * @param daoClass
     */
    public static void checkDaoClass(Class daoClass) {
        String daoClassName = daoClass.getName();
        //得到dao注解描述信息
        Dao daoAnnotation = (Dao) daoClass.getAnnotation(Dao.class);
        if (daoAnnotation == null) {
            throw new DaoException("entity[" + daoClassName + "] must have Dao annotation");
        }
    }

    /**
     * 得到dao对象的注解属性
     *
     * @param daoClass
     */
    public static String getSettingsName(Class daoClass) {
        checkDaoClass(daoClass);
        Dao daoAnnotation = (Dao) daoClass.getAnnotation(Dao.class);
        return daoAnnotation.settingBeanName();
    }

    /**
     * 得到entity的主键值
     *
     * @param idEntity
     * @return
     */
    public static Field getPkField(IdEntity idEntity) {
        Field pkField = null;
        Field[] fields = idEntity.getClass().getDeclaredFields();
        for (Field field : fields) {
            PK pkAnnotation = field.getAnnotation(PK.class);
            if (pkAnnotation != null) {
                pkField = field;
                break;
            }
        }
        return pkField;
    }


    /**
     * 根据field得到对应值
     *
     * @param idEntity - 字段对象
     * @return - 返回filed值
     */
    public static Serializable getPkValue(IdEntity idEntity) {
        Field pkField = getPkField(idEntity);
        return (Serializable) getColumnValue(pkField, idEntity);
    }

    /**
     * 判断idEntity是否由外部service设置的主键
     *
     * @param pkValue
     * @return
     */
    public static boolean hasSetPkValue(Object pkValue) {
        if (pkValue == null) {
            return false;
        }
        if (pkValue instanceof Long && ((Long) pkValue).longValue() > 0L) {
            return true;
        }
        if (pkValue instanceof Integer && ((Integer) pkValue).intValue() > 0) {
            return true;
        }
        if (pkValue instanceof String && StringUtils.isNotBlank(pkValue.toString())) {
            return true;
        }
        return false;
    }
}
