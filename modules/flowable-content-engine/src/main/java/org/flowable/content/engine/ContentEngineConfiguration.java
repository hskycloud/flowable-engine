/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.content.engine;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.TransactionFactory;
import org.flowable.content.api.ContentEngineConfigurationApi;
import org.flowable.content.api.ContentManagementService;
import org.flowable.content.api.ContentService;
import org.flowable.content.api.ContentStorage;
import org.flowable.content.engine.impl.ContentEngineImpl;
import org.flowable.content.engine.impl.ContentManagementServiceImpl;
import org.flowable.content.engine.impl.ContentServiceImpl;
import org.flowable.content.engine.impl.ServiceImpl;
import org.flowable.content.engine.impl.cfg.StandaloneContentEngineConfiguration;
import org.flowable.content.engine.impl.cfg.StandaloneInMemContentEngineConfiguration;
import org.flowable.content.engine.impl.db.ContentDbSchemaManager;
import org.flowable.content.engine.impl.db.EntityDependencyOrder;
import org.flowable.content.engine.impl.fs.SimpleFileSystemContentStorage;
import org.flowable.content.engine.impl.persistence.entity.ContentItemEntityManager;
import org.flowable.content.engine.impl.persistence.entity.ContentItemEntityManagerImpl;
import org.flowable.content.engine.impl.persistence.entity.TableDataManager;
import org.flowable.content.engine.impl.persistence.entity.TableDataManagerImpl;
import org.flowable.content.engine.impl.persistence.entity.data.ContentItemDataManager;
import org.flowable.content.engine.impl.persistence.entity.data.impl.MybatisContentItemDataManager;
import org.flowable.engine.common.AbstractEngineConfiguration;
import org.flowable.engine.common.api.FlowableException;
import org.flowable.engine.common.impl.cfg.BeansConfigurationHelper;
import org.flowable.engine.common.impl.cfg.standalone.StandaloneMybatisTransactionContextFactory;
import org.flowable.engine.common.impl.db.DbSqlSessionFactory;
import org.flowable.engine.common.impl.interceptor.CommandContextFactory;
import org.flowable.engine.common.impl.interceptor.CommandContextInterceptor;
import org.flowable.engine.common.impl.interceptor.CommandInterceptor;
import org.flowable.engine.common.impl.interceptor.DefaultCommandInvoker;
import org.flowable.engine.common.impl.interceptor.EngineConfigurationConstants;
import org.flowable.engine.common.impl.interceptor.LogInterceptor;
import org.flowable.engine.common.impl.interceptor.SessionFactory;
import org.flowable.engine.common.impl.interceptor.TransactionContextInterceptor;
import org.flowable.engine.common.impl.persistence.GenericManagerFactory;
import org.flowable.engine.common.impl.persistence.cache.EntityCache;
import org.flowable.engine.common.impl.persistence.cache.EntityCacheImpl;
import org.flowable.engine.common.impl.persistence.entity.Entity;
import org.flowable.engine.common.runtime.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

public class ContentEngineConfiguration extends AbstractEngineConfiguration implements ContentEngineConfigurationApi {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ContentEngineConfiguration.class);

    public static final String DEFAULT_MYBATIS_MAPPING_FILE = "org/flowable/content/db/mapping/mappings.xml";

    public static final String LIQUIBASE_CHANGELOG_PREFIX = "ACT_CO_";

    protected String contentEngineName = ContentEngines.NAME_DEFAULT;

    // SERVICES
    // /////////////////////////////////////////////////////////////////

    protected ContentManagementService contentManagementService = new ContentManagementServiceImpl();
    protected ContentService contentService = new ContentServiceImpl();

    // DATA MANAGERS ///////////////////////////////////////////////////

    protected ContentItemDataManager contentItemDataManager;

    // ADDITIONAL SERVICES /////////////////////////////////////////////

    protected ContentStorage contentStorage;
    protected String contentRootFolder;
    protected boolean createContentRootFolder = true;

    // ENTITY MANAGERS /////////////////////////////////////////////////
    protected ContentItemEntityManager contentItemEntityManager;
    protected TableDataManager tableDataManager;

    public static ContentEngineConfiguration createContentEngineConfigurationFromResourceDefault() {
        return createContentEngineConfigurationFromResource("flowable.content.cfg.xml", "contentEngineConfiguration");
    }

    public static ContentEngineConfiguration createContentEngineConfigurationFromResource(String resource) {
        return createContentEngineConfigurationFromResource(resource, "contentEngineConfiguration");
    }

    public static ContentEngineConfiguration createContentEngineConfigurationFromResource(String resource, String beanName) {
        return (ContentEngineConfiguration) BeansConfigurationHelper.parseEngineConfigurationFromResource(resource, beanName);
    }

    public static ContentEngineConfiguration createContentEngineConfigurationFromInputStream(InputStream inputStream) {
        return createContentEngineConfigurationFromInputStream(inputStream, "contentEngineConfiguration");
    }

    public static ContentEngineConfiguration createContentEngineConfigurationFromInputStream(InputStream inputStream, String beanName) {
        return (ContentEngineConfiguration) BeansConfigurationHelper.parseEngineConfigurationFromInputStream(inputStream, beanName);
    }

    public static ContentEngineConfiguration createStandaloneContentEngineConfiguration() {
        return new StandaloneContentEngineConfiguration();
    }

    public static ContentEngineConfiguration createStandaloneInMemContentEngineConfiguration() {
        return new StandaloneInMemContentEngineConfiguration();
    }

    // buildProcessEngine
    // ///////////////////////////////////////////////////////

    public ContentEngine buildContentEngine() {
        init();
        return new ContentEngineImpl(this);
    }

    // init
    // /////////////////////////////////////////////////////////////////////

    protected void init() {
        initCommandContextFactory();
        initTransactionContextFactory();
        initCommandExecutors();
        initIdGenerator();

        if (usingRelationalDatabase) {
            initDataSource();
            initDbSchemaManager();
            initDbSchema();
        }

        initBeans();
        initTransactionFactory();
        initSqlSessionFactory();
        initSessionFactories();
        initServices();
        initDataManagers();
        initEntityManagers();
        initContentStorage();
        initClock();
    }

    // services
    // /////////////////////////////////////////////////////////////////

    protected void initServices() {
        initService(contentManagementService);
        initService(contentService);
    }

    protected void initService(Object service) {
        if (service instanceof ServiceImpl) {
            ((ServiceImpl) service).setCommandExecutor(commandExecutor);
        }
    }

    // Data managers
    ///////////////////////////////////////////////////////////

    public void initDataManagers() {
        if (contentItemDataManager == null) {
            contentItemDataManager = new MybatisContentItemDataManager(this);
        }
    }

    public void initEntityManagers() {
        if (contentItemEntityManager == null) {
            contentItemEntityManager = new ContentItemEntityManagerImpl(this, contentItemDataManager);
        }
        if (tableDataManager == null) {
            tableDataManager = new TableDataManagerImpl(this);
        }
    }

    public void initContentStorage() {
        if (contentStorage == null) {
            if (contentRootFolder == null) {
                contentRootFolder = System.getProperty("user.home") + File.separator + "content";
            }

            File contentRootFile = new File(contentRootFolder);
            if (createContentRootFolder && !contentRootFile.exists()) {
                contentRootFile.mkdirs();
            }

            if (contentRootFile != null && contentRootFile.exists()) {
                LOGGER.info("Content file system root : {}", contentRootFile.getAbsolutePath());
            }

            contentStorage = new SimpleFileSystemContentStorage(contentRootFile);
        }
    }

    // data model ///////////////////////////////////////////////////////////////
    
    public void initDbSchemaManager() {
        if (this.dbSchemaManager == null) {
            this.dbSchemaManager = new ContentDbSchemaManager();
        }
    }

    public void initDbSchema() {
        try {
            DatabaseConnection connection = new JdbcConnection(dataSource.getConnection());
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(connection);
            database.setDatabaseChangeLogTableName(LIQUIBASE_CHANGELOG_PREFIX + database.getDatabaseChangeLogTableName());
            database.setDatabaseChangeLogLockTableName(LIQUIBASE_CHANGELOG_PREFIX + database.getDatabaseChangeLogLockTableName());

            if (StringUtils.isNotEmpty(databaseSchema)) {
                database.setDefaultSchemaName(databaseSchema);
                database.setLiquibaseSchemaName(databaseSchema);
            }

            if (StringUtils.isNotEmpty(databaseCatalog)) {
                database.setDefaultCatalogName(databaseCatalog);
                database.setLiquibaseCatalogName(databaseCatalog);
            }

            Liquibase liquibase = new Liquibase("org/flowable/content/db/liquibase/flowable-content-db-changelog.xml", new ClassLoaderResourceAccessor(), database);

            if (DB_SCHEMA_UPDATE_DROP_CREATE.equals(databaseSchemaUpdate)) {
                LOGGER.debug("Dropping and creating schema CONTENT");
                liquibase.dropAll();
                liquibase.update("content");
            } else if (DB_SCHEMA_UPDATE_TRUE.equals(databaseSchemaUpdate)) {
                LOGGER.debug("Updating schema CONTENT");
                liquibase.update("content");
            } else if (DB_SCHEMA_UPDATE_FALSE.equals(databaseSchemaUpdate)) {
                LOGGER.debug("Validating schema CONTENT");
                liquibase.validate();
            }
        } catch (Exception e) {
            throw new FlowableException("Error initialising content data schema", e);
        }
    }

    // session factories ////////////////////////////////////////////////////////

    public void initSessionFactories() {
        if (sessionFactories == null) {
            sessionFactories = new HashMap<Class<?>, SessionFactory>();

            if (usingRelationalDatabase) {
                initDbSqlSessionFactory();
            }
            
            addSessionFactory(new GenericManagerFactory(EntityCache.class, EntityCacheImpl.class));
            
            commandContextFactory.setSessionFactories(sessionFactories);
        }

        if (customSessionFactories != null) {
            for (SessionFactory sessionFactory : customSessionFactories) {
                addSessionFactory(sessionFactory);
            }
        }
    }

    public void initDbSqlSessionFactory() {
        if (dbSqlSessionFactory == null) {
            dbSqlSessionFactory = createDbSqlSessionFactory();
            dbSqlSessionFactory.setDatabaseType(databaseType);
            dbSqlSessionFactory.setSqlSessionFactory(sqlSessionFactory);
            dbSqlSessionFactory.setIdGenerator(idGenerator);
            dbSqlSessionFactory.setDatabaseTablePrefix(databaseTablePrefix);
            dbSqlSessionFactory.setTablePrefixIsSchema(tablePrefixIsSchema);
            dbSqlSessionFactory.setDatabaseCatalog(databaseCatalog);
            dbSqlSessionFactory.setDatabaseSchema(databaseSchema);
            addSessionFactory(dbSqlSessionFactory);
        }
        initDbSqlSessionFactoryEntitySettings();
    }

    public DbSqlSessionFactory createDbSqlSessionFactory() {
        return new DbSqlSessionFactory();
    }
    
    protected void initDbSqlSessionFactoryEntitySettings() {
        for (Class<? extends Entity> clazz : EntityDependencyOrder.INSERT_ORDER) {
            dbSqlSessionFactory.getInsertionOrder().add(clazz);
        }
        
        for (Class<? extends Entity> clazz : EntityDependencyOrder.DELETE_ORDER) {
            dbSqlSessionFactory.getDeletionOrder().add(clazz);
        }
    }

    // command executors
    // ////////////////////////////////////////////////////////

    public void initCommandExecutors() {
        initDefaultCommandConfig();
        initSchemaCommandConfig();
        initCommandInvoker();
        initCommandInterceptors();
        initCommandExecutor();
    }

    public void initCommandInvoker() {
        if (commandInvoker == null) {
            commandInvoker = new DefaultCommandInvoker();
        }
    }

    public void initCommandInterceptors() {
        if (commandInterceptors == null) {
            commandInterceptors = new ArrayList<CommandInterceptor>();
            if (customPreCommandInterceptors != null) {
                commandInterceptors.addAll(customPreCommandInterceptors);
            }
            commandInterceptors.addAll(getDefaultCommandInterceptors());
            if (customPostCommandInterceptors != null) {
                commandInterceptors.addAll(customPostCommandInterceptors);
            }
            commandInterceptors.add(commandInvoker);
        }
    }

    public Collection<? extends CommandInterceptor> getDefaultCommandInterceptors() {
        if (defaultCommandInterceptors == null) {
            List<CommandInterceptor> interceptors = new ArrayList<CommandInterceptor>();
            interceptors.add(new LogInterceptor());
            
            CommandInterceptor transactionInterceptor = createTransactionInterceptor();
            if (transactionInterceptor != null) {
                interceptors.add(transactionInterceptor);
            }
            
            if (commandContextFactory != null) {
                CommandContextInterceptor commandContextInterceptor = new CommandContextInterceptor(commandContextFactory);
                engineConfigurations.put(EngineConfigurationConstants.KEY_CONTENT_ENGINE_CONFIG, this);
                commandContextInterceptor.setEngineConfigurations(engineConfigurations);
                commandContextInterceptor.setCurrentEngineConfigurationKey(EngineConfigurationConstants.KEY_CONTENT_ENGINE_CONFIG);
                interceptors.add(commandContextInterceptor);
            }
           
            if (transactionContextFactory != null) {
                interceptors.add(new TransactionContextInterceptor(transactionContextFactory));
            }
            
            defaultCommandInterceptors = interceptors;
        }
        return defaultCommandInterceptors;
    }

    public CommandInterceptor createTransactionInterceptor() {
        return null;
    }

    // OTHER
    // ////////////////////////////////////////////////////////////////////

    public void initCommandContextFactory() {
        if (commandContextFactory == null) {
            commandContextFactory = new CommandContextFactory();
        }
    }

    public void initTransactionContextFactory() {
        if (transactionContextFactory == null) {
            transactionContextFactory = new StandaloneMybatisTransactionContextFactory();
        }
    }

    // myBatis SqlSessionFactory
    // ////////////////////////////////////////////////

    @Override
    public InputStream getMyBatisXmlConfigurationStream() {
        return getResourceAsStream(DEFAULT_MYBATIS_MAPPING_FILE);
    }

    // getters and setters
    // //////////////////////////////////////////////////////

    @Override
    public String getEngineName() {
        return contentEngineName;
    }

    public ContentEngineConfiguration setEngineName(String contentEngineName) {
        this.contentEngineName = contentEngineName;
        return this;
    }

    @Override
    public ContentEngineConfiguration setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
        return this;
    }

    @Override
    public ContentEngineConfiguration setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcDriver(String jdbcDriver) {
        this.jdbcDriver = jdbcDriver;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcUsername(String jdbcUsername) {
        this.jdbcUsername = jdbcUsername;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcPassword(String jdbcPassword) {
        this.jdbcPassword = jdbcPassword;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcMaxActiveConnections(int jdbcMaxActiveConnections) {
        this.jdbcMaxActiveConnections = jdbcMaxActiveConnections;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcMaxIdleConnections(int jdbcMaxIdleConnections) {
        this.jdbcMaxIdleConnections = jdbcMaxIdleConnections;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcMaxCheckoutTime(int jdbcMaxCheckoutTime) {
        this.jdbcMaxCheckoutTime = jdbcMaxCheckoutTime;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcMaxWaitTime(int jdbcMaxWaitTime) {
        this.jdbcMaxWaitTime = jdbcMaxWaitTime;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcPingEnabled(boolean jdbcPingEnabled) {
        this.jdbcPingEnabled = jdbcPingEnabled;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcPingConnectionNotUsedFor(int jdbcPingConnectionNotUsedFor) {
        this.jdbcPingConnectionNotUsedFor = jdbcPingConnectionNotUsedFor;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcDefaultTransactionIsolationLevel(int jdbcDefaultTransactionIsolationLevel) {
        this.jdbcDefaultTransactionIsolationLevel = jdbcDefaultTransactionIsolationLevel;
        return this;
    }

    @Override
    public ContentEngineConfiguration setJdbcPingQuery(String jdbcPingQuery) {
        this.jdbcPingQuery = jdbcPingQuery;
        return this;
    }

    @Override
    public ContentEngineConfiguration setDataSourceJndiName(String dataSourceJndiName) {
        this.dataSourceJndiName = dataSourceJndiName;
        return this;
    }

    public ContentManagementService getContentManagementService() {
        return contentManagementService;
    }

    public ContentEngineConfiguration setContentManagementService(ContentManagementService contentManagementService) {
        this.contentManagementService = contentManagementService;
        return this;
    }

    public ContentService getContentService() {
        return contentService;
    }

    public ContentEngineConfiguration setContentService(ContentService contentService) {
        this.contentService = contentService;
        return this;
    }

    public ContentEngineConfiguration getContentEngineConfiguration() {
        return this;
    }

    public ContentItemDataManager getContentItemDataManager() {
        return contentItemDataManager;
    }

    public ContentEngineConfiguration setContentItemDataManager(ContentItemDataManager contentItemDataManager) {
        this.contentItemDataManager = contentItemDataManager;
        return this;
    }

    public ContentItemEntityManager getContentItemEntityManager() {
        return contentItemEntityManager;
    }

    public ContentEngineConfiguration setContentItemEntityManager(ContentItemEntityManager contentItemEntityManager) {
        this.contentItemEntityManager = contentItemEntityManager;
        return this;
    }

    public TableDataManager getTableDataManager() {
        return tableDataManager;
    }

    public ContentEngineConfiguration setTableDataManager(TableDataManager tableDataManager) {
        this.tableDataManager = tableDataManager;
        return this;
    }

    public ContentStorage getContentStorage() {
        return contentStorage;
    }

    public ContentEngineConfiguration setContentStorage(ContentStorage contentStorage) {
        this.contentStorage = contentStorage;
        return this;
    }

    public String getContentRootFolder() {
        return contentRootFolder;
    }

    public ContentEngineConfiguration setContentRootFolder(String contentRootFolder) {
        this.contentRootFolder = contentRootFolder;
        return this;
    }

    public boolean isCreateContentRootFolder() {
        return createContentRootFolder;
    }

    public ContentEngineConfiguration setCreateContentRootFolder(boolean createContentRootFolder) {
        this.createContentRootFolder = createContentRootFolder;
        return this;
    }

    @Override
    public ContentEngineConfiguration setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
        return this;
    }

    @Override
    public ContentEngineConfiguration setTransactionFactory(TransactionFactory transactionFactory) {
        this.transactionFactory = transactionFactory;
        return this;
    }

    @Override
    public ContentEngineConfiguration setCustomMybatisMappers(Set<Class<?>> customMybatisMappers) {
        this.customMybatisMappers = customMybatisMappers;
        return this;
    }

    @Override
    public ContentEngineConfiguration setCustomMybatisXMLMappers(Set<String> customMybatisXMLMappers) {
        this.customMybatisXMLMappers = customMybatisXMLMappers;
        return this;
    }

    @Override
    public ContentEngineConfiguration setCustomSessionFactories(List<SessionFactory> customSessionFactories) {
        this.customSessionFactories = customSessionFactories;
        return this;
    }

    @Override
    public ContentEngineConfiguration setUsingRelationalDatabase(boolean usingRelationalDatabase) {
        this.usingRelationalDatabase = usingRelationalDatabase;
        return this;
    }

    @Override
    public ContentEngineConfiguration setDatabaseTablePrefix(String databaseTablePrefix) {
        this.databaseTablePrefix = databaseTablePrefix;
        return this;
    }

    @Override
    public ContentEngineConfiguration setDatabaseCatalog(String databaseCatalog) {
        this.databaseCatalog = databaseCatalog;
        return this;
    }

    @Override
    public ContentEngineConfiguration setDatabaseSchema(String databaseSchema) {
        this.databaseSchema = databaseSchema;
        return this;
    }

    @Override
    public ContentEngineConfiguration setTablePrefixIsSchema(boolean tablePrefixIsSchema) {
        this.tablePrefixIsSchema = tablePrefixIsSchema;
        return this;
    }

    @Override
    public ContentEngineConfiguration setSessionFactories(Map<Class<?>, SessionFactory> sessionFactories) {
        this.sessionFactories = sessionFactories;
        return this;
    }

    @Override
    public ContentEngineConfiguration setDatabaseSchemaUpdate(String databaseSchemaUpdate) {
        this.databaseSchemaUpdate = databaseSchemaUpdate;
        return this;
    }

    @Override
    public ContentEngineConfiguration setClock(Clock clock) {
        this.clock = clock;
        return this;
    }
}
