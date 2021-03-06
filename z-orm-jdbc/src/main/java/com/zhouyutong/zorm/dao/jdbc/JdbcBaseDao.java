package com.zhouyutong.zorm.dao.jdbc;

import com.google.common.collect.Lists;
import com.zhouyutong.zorm.constant.MixedConstant;
import com.zhouyutong.zorm.dao.AbstractBaseDao;
import com.zhouyutong.zorm.dao.DaoHelper;
import com.zhouyutong.zorm.dao.DatabaseRouter;
import com.zhouyutong.zorm.entity.IdEntity;
import com.zhouyutong.zorm.enums.DialectEnum;
import com.zhouyutong.zorm.query.*;
import com.zhouyutong.zorm.utils.ExceptionTranslator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于JdbcTemplate的Dao实现<br>
 *
 * @author zhouyutong
 * @since 2015/11/24
 */
@Slf4j
public abstract class JdbcBaseDao<T> extends AbstractBaseDao<T> implements ApplicationContextAware {
    private Class<T> entityClass;
    private EntityMapper<T> entityMapper;
    private JdbcSettings jdbcSettings;
    private DatabaseRouter router;
    private ApplicationContext applicationContext;

    @Override
    public Class<T> getGenericClass() {
        return this.entityClass;
    }

    @Override
    public boolean exists(Serializable id) {
        DaoHelper.checkArgumentId(id);

        return this.exists(Criteria.where(entityMapper.getPkFieldName(), id));
    }

    @Override
    public boolean exists(Criteria criteria) {
        DaoHelper.checkArgumentCriteria(criteria);

        return null != this.findOne(Arrays.asList(entityMapper.getPkFieldName()), criteria);
    }

    @Override
    public long countByCriteria(Criteria criteria) {
        DaoHelper.checkArgumentCriteria(criteria);

        List<Object> valueList = Lists.newArrayList();
        StringBuilder sql = new StringBuilder();
        sql.append(JdbcHelper.SELECT_COUNT());
        sql.append(JdbcHelper.FROM(entityClass));
        sql.append(JdbcHelper.WHERE(criteria, valueList, entityMapper));

        try {
            if (log.isDebugEnabled()) {
                log.debug("=========countByCriteria request:" + DaoHelper.formatSql(sql.toString(), valueList));
            }
            long count = ((JdbcTemplate) router.readRoute()).queryForObject(sql.toString(), valueList.toArray(), Long.class);
            if (log.isDebugEnabled()) {
                log.debug("=========countByCriteria response:" + count);
            }
            return count;
        } catch (DataAccessException e) {
            throw ExceptionTranslator.translate(e, jdbcSettings.getDialectEnum());
        }
    }

    @Override
    public long countAll() {
        StringBuilder sql = new StringBuilder();
        sql.append(JdbcHelper.SELECT_COUNT());
        sql.append(JdbcHelper.FROM(entityClass));

        try {
            if (log.isDebugEnabled()) {
                log.debug("=========countAll request:" + DaoHelper.formatSql(sql.toString(), null));
            }
            long count = ((JdbcTemplate) router.readRoute()).queryForObject(sql.toString(), Long.class);
            if (log.isDebugEnabled()) {
                log.debug("=========countAll response:" + count);
            }
            return count;
        } catch (DataAccessException e) {
            throw ExceptionTranslator.translate(e, jdbcSettings.getDialectEnum());
        }
    }

    @Override
    protected long countBySql(String sql, LinkedHashMap<String, Object> param) {
        DaoHelper.checkArgument(sql);

        List<Object> valueList = MapUtils.isEmpty(param) ? null : Lists.newArrayList(param.values());
        try {
            if (log.isDebugEnabled()) {
                log.debug("=========countBySql request:" + DaoHelper.formatSql(sql.toString(), valueList));
            }
            long count;
            if (CollectionUtils.isEmpty(valueList)) {
                count = ((JdbcTemplate) router.readRoute()).queryForObject(sql, Long.class);
            } else {
                count = ((JdbcTemplate) router.readRoute()).queryForObject(sql, valueList.toArray(), Long.class);
            }
            if (log.isDebugEnabled()) {
                log.debug("=========countBySql response:" + count);
            }
            return count;
        } catch (DataAccessException e) {
            throw ExceptionTranslator.translate(e, jdbcSettings.getDialectEnum());
        }
    }

    @Override
    public T findOneById(Serializable id) {
        DaoHelper.checkArgumentId(id);

        return this.findOne(Criteria.where(entityMapper.getPkFieldName(), id));
    }

    @Override
    public T findOneByQuery(Query query) {
        DaoHelper.checkArgumentQuery(query);

        query.offset(MixedConstant.INT_0).limit(MixedConstant.INT_1);
        List<T> entityList = this.findListByQuery(query);
        return CollectionUtils.isEmpty(entityList) ? null : entityList.get(MixedConstant.INT_0);
    }

    @Override
    protected T findOneBySql(String sqlOrgin, LinkedHashMap<String, Object> param) {
        DaoHelper.checkArgument(sqlOrgin);

        List<T> entityList = this.findListBySql(sqlOrgin, param);
        return CollectionUtils.isEmpty(entityList) ? null : entityList.get(MixedConstant.INT_0);
    }

    @Override
    public List<T> findListByIds(List<Serializable> ids) {
        DaoHelper.checkArgumentIds(ids);

        return this.findList(Criteria.where(entityMapper.getPkFieldName(), CriteriaOperators.IN, ids));
    }

    @Override
    public List<T> findListByQuery(Query query) {
        DaoHelper.checkArgumentQuery(query);

        List<Object> valueList = Lists.newArrayList();
        StringBuilder sql = new StringBuilder();
        sql.append(JdbcHelper.SELECT(query, entityMapper));
        sql.append(JdbcHelper.FROM(entityClass));
        sql.append(JdbcHelper.WHERE(query.getCriteria(), valueList, entityMapper));
        sql.append(JdbcHelper.GROUP_BY(query.getGroupBys(), entityMapper));
        sql.append(JdbcHelper.ORDER_BY(query.getOrderBys(), entityMapper));
        sql.append(JdbcHelper.LIMIT(query.getOffset(), query.getLimit(), jdbcSettings.getDialectEnum(), sql));

        try {
            if (log.isDebugEnabled()) {
                log.debug("=========findListByQuery request:" + DaoHelper.formatSql(sql.toString(), valueList));
            }
            List<Map<String, Object>> list = ((JdbcTemplate) router.readRoute()).queryForList(sql.toString(), valueList.toArray());
            if (log.isDebugEnabled()) {
                log.debug("=========findListByQuery response:" + list);
            }
            if (CollectionUtils.isEmpty(list)) {
                return null;
            }
            List<T> entityList = Lists.newArrayList();
            for (Map<String, Object> map : list) {
                entityList.add(JdbcHelper.map2Entity(map, entityMapper, entityClass));
            }
            return entityList;
        } catch (DataAccessException e) {
            throw ExceptionTranslator.translate(e, jdbcSettings.getDialectEnum());
        }
    }

    @Override
    public List<T> findListByQuery(Query query, Pageable pageable) {
        DaoHelper.checkArgumentQuery(query);
        DaoHelper.checkArgumentPageable(pageable);

        int limit = pageable.getPageSize();
        int offset = (pageable.getPageNumber() - MixedConstant.INT_1) * limit;
        query.offset(offset).limit(limit);
        return this.findListByQuery(query);
    }

    @Override
    protected List<T> findListBySql(String sql, LinkedHashMap<String, Object> param) {
        DaoHelper.checkArgument(sql);
        List<Object> valueList = MapUtils.isEmpty(param) ? null : Lists.newArrayList(param.values());
        try {
            if (log.isDebugEnabled()) {
                log.debug("=========findListBySql request:" + DaoHelper.formatSql(sql.toString(), valueList));
            }
            List<Map<String, Object>> list;
            if (CollectionUtils.isEmpty(valueList)) {
                list = ((JdbcTemplate) router.readRoute()).queryForList(sql);
            } else {
                list = ((JdbcTemplate) router.readRoute()).queryForList(sql, valueList.toArray());
            }
            if (log.isDebugEnabled()) {
                log.debug("=========findListBySql response:" + list);
            }
            if (CollectionUtils.isEmpty(list)) {
                return null;
            }
            List<T> entityList = Lists.newArrayList();
            for (Map<String, Object> map : list) {
                entityList.add(JdbcHelper.map2Entity(map, entityMapper, entityClass));
            }
            return entityList;
        } catch (DataAccessException e) {
            throw ExceptionTranslator.translate(e, jdbcSettings.getDialectEnum());
        }
    }

    @Override
    public int insert(T entity) {
        DaoHelper.checkArgumentEntity(entity);

        final IdEntity idEntity = (IdEntity) entity;
        final Field pkField = DaoHelper.getPkField(idEntity);
        final Object pkValue = DaoHelper.getColumnValue(pkField, idEntity);
        final List<Object> valueList = Lists.newArrayList();
        StringBuilder insertSql = new StringBuilder();

        PreparedStatementCreator psc = connection -> {
            String insertSqlToUse = JdbcHelper.INSERT(idEntity, valueList, entityMapper, entityClass, jdbcSettings.getDialectEnum(), connection);
            insertSql.append(insertSqlToUse);
            PreparedStatement ps;
            if (DaoHelper.hasSetPkValue(pkValue)) {
                ps = connection.prepareStatement(insertSqlToUse);
            } else {
                ps = connection.prepareStatement(insertSqlToUse, new String[]{entityMapper.getPkFieldName()});
            }

            int i = MixedConstant.INT_0;
            for (Object value : valueList) {
                StatementCreatorUtils.setParameterValue(ps, ++i, SqlTypeValue.TYPE_UNKNOWN, value);
            }
            return ps;
        };

        int n;
        try {
            if (log.isDebugEnabled()) {
                log.debug("=========insert request:" + DaoHelper.formatSql(insertSql.toString(), valueList));
            }
            if (DaoHelper.hasSetPkValue(pkValue) || DialectEnum.ORACLE.equals(jdbcSettings.getDialectEnum())) {//KeyHolder不支持oracle
                n = ((JdbcTemplate) router.writeRoute()).update(psc);
            } else {
                KeyHolder keyHolder = new GeneratedKeyHolder();
                n = ((JdbcTemplate) router.writeRoute()).update(psc, keyHolder);
                DaoHelper.setColumnValue(pkField, idEntity, keyHolder.getKey());
            }
            if (log.isDebugEnabled()) {
                log.debug("=========insert response:" + n);
            }
            return n;
        } catch (DataAccessException e) {
            throw ExceptionTranslator.translate(e, jdbcSettings.getDialectEnum());
        }
    }

    @Override
    public int insert(List<T> entity) {
        throw new IllegalArgumentException("JdbcBaseDao not support batch insert.");
    }

    @Override
    public int update(T entity) {
        DaoHelper.checkArgumentEntity(entity);

        return this.update(entity, null);
    }

    @Override
    public int update(T entity, List<String> propetyList) {
        DaoHelper.checkArgumentEntity(entity);

        IdEntity idEntity = (IdEntity) entity;
        Serializable pkValue = DaoHelper.getPkValue(idEntity);
        return this.updateById(pkValue, DaoHelper.entity2Update(entity, propetyList));
    }

    @Override
    public int updateById(Serializable id, Update update) {
        DaoHelper.checkArgumentId(id);
        DaoHelper.checkArgumentUpdate(update);

        return this.updateByCriteria(Criteria.where(entityMapper.getPkFieldName(), id), update);
    }

    @Override
    public int updateByIds(List<Serializable> ids, Update update) {
        DaoHelper.checkArgumentIds(ids);
        DaoHelper.checkArgumentUpdate(update);

        return this.updateByCriteria(Criteria.where(entityMapper.getPkFieldName(), CriteriaOperators.IN, ids), update);
    }

    @Override
    public int updateByCriteria(Criteria criteria, Update update) {
        DaoHelper.checkArgumentCriteria(criteria);
        DaoHelper.checkArgumentUpdate(update);

        List<Object> valueList = Lists.newArrayList();
        StringBuilder sql = new StringBuilder();

        sql.append(JdbcHelper.UPDATE(entityClass));
        sql.append(JdbcHelper.SET(update, valueList, entityMapper));
        sql.append(JdbcHelper.WHERE(criteria, valueList, entityMapper));

        try {
            if (log.isDebugEnabled()) {
                log.debug("=========updateByCriteria request:" + DaoHelper.formatSql(sql.toString(), valueList));
            }
            int n = ((JdbcTemplate) router.writeRoute()).update(sql.toString(), valueList.toArray());
            if (log.isDebugEnabled()) {
                log.debug("=========countByCriteria response:" + n);
            }
            return n;
        } catch (DataAccessException e) {
            throw ExceptionTranslator.translate(e, jdbcSettings.getDialectEnum());
        }
    }

    @Override
    protected int updateBySql(String sql, LinkedHashMap<String, Object> param) {
        DaoHelper.checkArgument(sql);

        List<Object> valueList = MapUtils.isEmpty(param) ? null : Lists.newArrayList(param.values());
        try {
            if (log.isDebugEnabled()) {
                log.debug("=========updateBySql request:" + DaoHelper.formatSql(sql.toString(), valueList));
            }
            int n;
            if (CollectionUtils.isEmpty(valueList)) {
                n = ((JdbcTemplate) router.writeRoute()).update(sql);
            } else {
                n = ((JdbcTemplate) router.writeRoute()).update(sql, valueList.toArray());
            }
            if (log.isDebugEnabled()) {
                log.debug("=========updateBySql response:" + n);
            }
            return n;
        } catch (DataAccessException e) {
            throw ExceptionTranslator.translate(e, jdbcSettings.getDialectEnum());
        }
    }

    @Override
    public int deleteById(Serializable id) {
        DaoHelper.checkArgumentId(id);

        StringBuilder sql = new StringBuilder();
        sql.append(JdbcHelper.DELETE(entityClass));
        try {
            if (log.isDebugEnabled()) {
                List<Object> valueList = Lists.newArrayList(id);
                log.debug("=========deleteById request:" + DaoHelper.formatSql(sql.toString(), valueList));
            }
            int n = ((JdbcTemplate) router.writeRoute()).update(sql.toString(), new Object[]{id});
            if (log.isDebugEnabled()) {
                log.debug("=========deleteById response:" + n);
            }
            return n;
        } catch (DataAccessException e) {
            throw ExceptionTranslator.translate(e, jdbcSettings.getDialectEnum());
        }
    }

    @Override
    public int deleteBySql(String sql, LinkedHashMap<String, Object> param) {
        throw new RuntimeException("JdbcBaseDao do not support The Method");
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    protected void afterPropertiesSet() {
        Class daoClass = this.getClass();
        //得到泛型entityClass
        ParameterizedType type = (ParameterizedType) daoClass.getGenericSuperclass();
        Type[] p = type.getActualTypeArguments();
        this.entityClass = (Class<T>) p[0];
        JdbcHelper.checkEntityClass(this.entityClass);

        //得到jdbcSettings
        String settingsName = DaoHelper.getSettingsName(daoClass);
        this.jdbcSettings = (JdbcSettings) this.applicationContext.getBean(settingsName);
        if (this.jdbcSettings == null) {
            throw new RuntimeException("注解Dao的属性settingBeanName[" + settingsName + "]必须对应一个有效的JdbcSettings bean");
        }

        //create router
        JdbcDatabaseRouterFactory.INSTANCE.setDatabaseRouter(this.jdbcSettings);
        this.entityMapper = new EntityMapper(this.entityClass);
        this.router = JdbcDatabaseRouterFactory.INSTANCE.getDatabaseRouter(this.jdbcSettings);
    }
}