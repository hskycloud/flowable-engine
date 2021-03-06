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
package org.flowable.form.engine;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.flowable.editor.form.converter.FormJsonConverter;
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
import org.flowable.form.api.FormEngineConfigurationApi;
import org.flowable.form.api.FormManagementService;
import org.flowable.form.api.FormRepositoryService;
import org.flowable.form.api.FormService;
import org.flowable.form.engine.impl.FormEngineImpl;
import org.flowable.form.engine.impl.FormManagementServiceImpl;
import org.flowable.form.engine.impl.FormRepositoryServiceImpl;
import org.flowable.form.engine.impl.FormServiceImpl;
import org.flowable.form.engine.impl.ServiceImpl;
import org.flowable.form.engine.impl.cfg.StandaloneFormEngineConfiguration;
import org.flowable.form.engine.impl.cfg.StandaloneInMemFormEngineConfiguration;
import org.flowable.form.engine.impl.db.EntityDependencyOrder;
import org.flowable.form.engine.impl.db.FormDbSchemaManager;
import org.flowable.form.engine.impl.deployer.CachingAndArtifactsManager;
import org.flowable.form.engine.impl.deployer.FormDefinitionDeployer;
import org.flowable.form.engine.impl.deployer.FormDefinitionDeploymentHelper;
import org.flowable.form.engine.impl.deployer.ParsedDeploymentBuilderFactory;
import org.flowable.form.engine.impl.el.ExpressionManager;
import org.flowable.form.engine.impl.parser.FormDefinitionParseFactory;
import org.flowable.form.engine.impl.persistence.deploy.DefaultDeploymentCache;
import org.flowable.form.engine.impl.persistence.deploy.Deployer;
import org.flowable.form.engine.impl.persistence.deploy.DeploymentCache;
import org.flowable.form.engine.impl.persistence.deploy.DeploymentManager;
import org.flowable.form.engine.impl.persistence.deploy.FormDefinitionCacheEntry;
import org.flowable.form.engine.impl.persistence.entity.FormDefinitionEntityManager;
import org.flowable.form.engine.impl.persistence.entity.FormDefinitionEntityManagerImpl;
import org.flowable.form.engine.impl.persistence.entity.FormDeploymentEntityManager;
import org.flowable.form.engine.impl.persistence.entity.FormDeploymentEntityManagerImpl;
import org.flowable.form.engine.impl.persistence.entity.FormInstanceEntityManager;
import org.flowable.form.engine.impl.persistence.entity.FormInstanceEntityManagerImpl;
import org.flowable.form.engine.impl.persistence.entity.FormResourceEntityManager;
import org.flowable.form.engine.impl.persistence.entity.FormResourceEntityManagerImpl;
import org.flowable.form.engine.impl.persistence.entity.TableDataManager;
import org.flowable.form.engine.impl.persistence.entity.TableDataManagerImpl;
import org.flowable.form.engine.impl.persistence.entity.data.FormDefinitionDataManager;
import org.flowable.form.engine.impl.persistence.entity.data.FormDeploymentDataManager;
import org.flowable.form.engine.impl.persistence.entity.data.FormInstanceDataManager;
import org.flowable.form.engine.impl.persistence.entity.data.FormResourceDataManager;
import org.flowable.form.engine.impl.persistence.entity.data.impl.MybatisFormDefinitionDataManager;
import org.flowable.form.engine.impl.persistence.entity.data.impl.MybatisFormDeploymentDataManager;
import org.flowable.form.engine.impl.persistence.entity.data.impl.MybatisFormInstanceDataManager;
import org.flowable.form.engine.impl.persistence.entity.data.impl.MybatisFormResourceDataManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

public class FormEngineConfiguration extends AbstractEngineConfiguration implements FormEngineConfigurationApi {

    protected static final Logger LOGGER = LoggerFactory.getLogger(FormEngineConfiguration.class);

    public static final String DEFAULT_MYBATIS_MAPPING_FILE = "org/flowable/form/db/mapping/mappings.xml";

    public static final String LIQUIBASE_CHANGELOG_PREFIX = "ACT_FO_";

    protected String formEngineName = FormEngines.NAME_DEFAULT;

    // SERVICES
    // /////////////////////////////////////////////////////////////////

    protected FormManagementService formManagementService = new FormManagementServiceImpl();
    protected FormRepositoryService formRepositoryService = new FormRepositoryServiceImpl();
    protected FormService formService = new FormServiceImpl();

    // DATA MANAGERS ///////////////////////////////////////////////////

    protected FormDeploymentDataManager deploymentDataManager;
    protected FormDefinitionDataManager formDefinitionDataManager;
    protected FormResourceDataManager resourceDataManager;
    protected FormInstanceDataManager formInstanceDataManager;

    // ENTITY MANAGERS /////////////////////////////////////////////////
    protected FormDeploymentEntityManager deploymentEntityManager;
    protected FormDefinitionEntityManager formDefinitionEntityManager;
    protected FormResourceEntityManager resourceEntityManager;
    protected FormInstanceEntityManager formInstanceEntityManager;
    protected TableDataManager tableDataManager;

    protected ExpressionManager expressionManager;

    protected FormJsonConverter formJsonConverter = new FormJsonConverter();

    protected ObjectMapper objectMapper = new ObjectMapper();

    // DEPLOYERS
    // ////////////////////////////////////////////////////////////////

    protected FormDefinitionDeployer formDeployer;
    protected FormDefinitionParseFactory formParseFactory;
    protected ParsedDeploymentBuilderFactory parsedDeploymentBuilderFactory;
    protected FormDefinitionDeploymentHelper formDeploymentHelper;
    protected CachingAndArtifactsManager cachingAndArtifactsManager;
    protected List<Deployer> customPreDeployers;
    protected List<Deployer> customPostDeployers;
    protected List<Deployer> deployers;
    protected DeploymentManager deploymentManager;

    protected int formDefinitionCacheLimit = -1; // By default, no limit
    protected DeploymentCache<FormDefinitionCacheEntry> formDefinitionCache;

    public static FormEngineConfiguration createFormEngineConfigurationFromResourceDefault() {
        return createFormEngineConfigurationFromResource("flowable.form.cfg.xml", "formEngineConfiguration");
    }

    public static FormEngineConfiguration createFormEngineConfigurationFromResource(String resource) {
        return createFormEngineConfigurationFromResource(resource, "formEngineConfiguration");
    }

    public static FormEngineConfiguration createFormEngineConfigurationFromResource(String resource, String beanName) {
        return (FormEngineConfiguration) BeansConfigurationHelper.parseEngineConfigurationFromResource(resource, beanName);
    }

    public static FormEngineConfiguration createFormEngineConfigurationFromInputStream(InputStream inputStream) {
        return createFormEngineConfigurationFromInputStream(inputStream, "formEngineConfiguration");
    }

    public static FormEngineConfiguration createFormEngineConfigurationFromInputStream(InputStream inputStream, String beanName) {
        return (FormEngineConfiguration) BeansConfigurationHelper.parseEngineConfigurationFromInputStream(inputStream, beanName);
    }

    public static FormEngineConfiguration createStandaloneFormEngineConfiguration() {
        return new StandaloneFormEngineConfiguration();
    }

    public static FormEngineConfiguration createStandaloneInMemFormEngineConfiguration() {
        return new StandaloneInMemFormEngineConfiguration();
    }

    // buildProcessEngine
    // ///////////////////////////////////////////////////////

    public FormEngine buildFormEngine() {
        init();
        return new FormEngineImpl(this);
    }

    // init
    // /////////////////////////////////////////////////////////////////////

    protected void init() {
        initExpressionManager();
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
        initDeployers();
        initClock();
    }

    // services
    // /////////////////////////////////////////////////////////////////

    protected void initServices() {
        initService(formManagementService);
        initService(formRepositoryService);
        initService(formService);
    }

    protected void initService(Object service) {
        if (service instanceof ServiceImpl) {
            ((ServiceImpl) service).setCommandExecutor(commandExecutor);
        }
    }

    public void initExpressionManager() {
        if (expressionManager == null) {
            expressionManager = new ExpressionManager();
        }
    }

    // Data managers
    ///////////////////////////////////////////////////////////

    public void initDataManagers() {
        if (deploymentDataManager == null) {
            deploymentDataManager = new MybatisFormDeploymentDataManager(this);
        }
        if (formDefinitionDataManager == null) {
            formDefinitionDataManager = new MybatisFormDefinitionDataManager(this);
        }
        if (resourceDataManager == null) {
            resourceDataManager = new MybatisFormResourceDataManager(this);
        }
        if (formInstanceDataManager == null) {
            formInstanceDataManager = new MybatisFormInstanceDataManager(this);
        }
    }

    public void initEntityManagers() {
        if (deploymentEntityManager == null) {
            deploymentEntityManager = new FormDeploymentEntityManagerImpl(this, deploymentDataManager);
        }
        if (formDefinitionEntityManager == null) {
            formDefinitionEntityManager = new FormDefinitionEntityManagerImpl(this, formDefinitionDataManager);
        }
        if (resourceEntityManager == null) {
            resourceEntityManager = new FormResourceEntityManagerImpl(this, resourceDataManager);
        }
        if (formInstanceEntityManager == null) {
            formInstanceEntityManager = new FormInstanceEntityManagerImpl(this, formInstanceDataManager);
        }
        if (tableDataManager == null) {
            tableDataManager = new TableDataManagerImpl(this);
        }
    }

    // data model ///////////////////////////////////////////////////////////////

    public void initDbSchemaManager() {
        if (this.dbSchemaManager == null) {
            this.dbSchemaManager = new FormDbSchemaManager();
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

            Liquibase liquibase = new Liquibase("org/flowable/form/db/liquibase/flowable-form-db-changelog.xml", new ClassLoaderResourceAccessor(), database);

            if (DB_SCHEMA_UPDATE_DROP_CREATE.equals(databaseSchemaUpdate)) {
                LOGGER.debug("Dropping and creating schema FORM");
                liquibase.dropAll();
                liquibase.update("form");
            } else if (DB_SCHEMA_UPDATE_TRUE.equals(databaseSchemaUpdate)) {
                LOGGER.debug("Updating schema FORM");
                liquibase.update("form");
            } else if (DB_SCHEMA_UPDATE_FALSE.equals(databaseSchemaUpdate)) {
                LOGGER.debug("Validating schema FORM");
                liquibase.validate();
            }
        } catch (Exception e) {
            throw new FlowableException("Error initialising form data schema", e);
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
    
    protected void initDbSqlSessionFactoryEntitySettings() {
        for (Class<? extends Entity> clazz : EntityDependencyOrder.INSERT_ORDER) {
            dbSqlSessionFactory.getInsertionOrder().add(clazz);
        }
        
        for (Class<? extends Entity> clazz : EntityDependencyOrder.DELETE_ORDER) {
            dbSqlSessionFactory.getDeletionOrder().add(clazz);
        }
    }

    public DbSqlSessionFactory createDbSqlSessionFactory() {
        return new DbSqlSessionFactory();
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
                engineConfigurations.put(EngineConfigurationConstants.KEY_FORM_ENGINE_CONFIG, this);
                commandContextInterceptor.setEngineConfigurations(engineConfigurations);
                commandContextInterceptor.setCurrentEngineConfigurationKey(EngineConfigurationConstants.KEY_FORM_ENGINE_CONFIG);
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

    // deployers
    // ////////////////////////////////////////////////////////////////

    protected void initDeployers() {
        if (formParseFactory == null) {
            formParseFactory = new FormDefinitionParseFactory();
        }

        if (this.formDeployer == null) {
            this.deployers = new ArrayList<Deployer>();
            if (customPreDeployers != null) {
                this.deployers.addAll(customPreDeployers);
            }
            this.deployers.addAll(getDefaultDeployers());
            if (customPostDeployers != null) {
                this.deployers.addAll(customPostDeployers);
            }
        }

        // Decision cache
        if (formDefinitionCache == null) {
            if (formDefinitionCacheLimit <= 0) {
                formDefinitionCache = new DefaultDeploymentCache<FormDefinitionCacheEntry>();
            } else {
                formDefinitionCache = new DefaultDeploymentCache<FormDefinitionCacheEntry>(formDefinitionCacheLimit);
            }
        }

        deploymentManager = new DeploymentManager(formDefinitionCache, this);
        deploymentManager.setDeployers(deployers);
        deploymentManager.setDeploymentEntityManager(deploymentEntityManager);
        deploymentManager.setFormDefinitionEntityManager(formDefinitionEntityManager);
    }

    public Collection<? extends Deployer> getDefaultDeployers() {
        List<Deployer> defaultDeployers = new ArrayList<Deployer>();

        if (formDeployer == null) {
            formDeployer = new FormDefinitionDeployer();
        }

        initDmnDeployerDependencies();

        formDeployer.setIdGenerator(idGenerator);
        formDeployer.setParsedDeploymentBuilderFactory(parsedDeploymentBuilderFactory);
        formDeployer.setFormDeploymentHelper(formDeploymentHelper);
        formDeployer.setCachingAndArtifactsManager(cachingAndArtifactsManager);

        defaultDeployers.add(formDeployer);
        return defaultDeployers;
    }

    public void initDmnDeployerDependencies() {
        if (parsedDeploymentBuilderFactory == null) {
            parsedDeploymentBuilderFactory = new ParsedDeploymentBuilderFactory();
        }
        if (parsedDeploymentBuilderFactory.getFormParseFactory() == null) {
            parsedDeploymentBuilderFactory.setFormParseFactory(formParseFactory);
        }

        if (formDeploymentHelper == null) {
            formDeploymentHelper = new FormDefinitionDeploymentHelper();
        }

        if (cachingAndArtifactsManager == null) {
            cachingAndArtifactsManager = new CachingAndArtifactsManager();
        }
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

    public InputStream getMyBatisXmlConfigurationStream() {
        return getResourceAsStream(DEFAULT_MYBATIS_MAPPING_FILE);
    }

    // getters and setters
    // //////////////////////////////////////////////////////

    public String getEngineName() {
        return formEngineName;
    }

    public FormEngineConfiguration setEngineName(String formEngineName) {
        this.formEngineName = formEngineName;
        return this;
    }

    public FormManagementService getFormManagementService() {
        return formManagementService;
    }

    public FormEngineConfiguration setFormManagementService(FormManagementService formManagementService) {
        this.formManagementService = formManagementService;
        return this;
    }

    public FormRepositoryService getFormRepositoryService() {
        return formRepositoryService;
    }

    public FormEngineConfiguration setFormRepositoryService(FormRepositoryService formRepositoryService) {
        this.formRepositoryService = formRepositoryService;
        return this;
    }

    public FormService getFormService() {
        return formService;
    }

    public FormEngineConfiguration setFormService(FormService formService) {
        this.formService = formService;
        return this;
    }

    public DeploymentManager getDeploymentManager() {
        return deploymentManager;
    }

    public FormEngineConfiguration getFormEngineConfiguration() {
        return this;
    }

    public FormDefinitionDeployer getFormDeployer() {
        return formDeployer;
    }

    public FormEngineConfiguration setFormDeployer(FormDefinitionDeployer formDeployer) {
        this.formDeployer = formDeployer;
        return this;
    }

    public FormDefinitionParseFactory getFormParseFactory() {
        return formParseFactory;
    }

    public FormEngineConfiguration setFormParseFactory(FormDefinitionParseFactory formParseFactory) {
        this.formParseFactory = formParseFactory;
        return this;
    }

    public int getFormCacheLimit() {
        return formDefinitionCacheLimit;
    }

    public FormEngineConfiguration setFormDefinitionCacheLimit(int formDefinitionCacheLimit) {
        this.formDefinitionCacheLimit = formDefinitionCacheLimit;
        return this;
    }

    public DeploymentCache<FormDefinitionCacheEntry> getFormDefinitionCache() {
        return formDefinitionCache;
    }

    public FormEngineConfiguration setFormDefinitionCache(DeploymentCache<FormDefinitionCacheEntry> formDefinitionCache) {
        this.formDefinitionCache = formDefinitionCache;
        return this;
    }

    public FormDeploymentDataManager getDeploymentDataManager() {
        return deploymentDataManager;
    }

    public FormEngineConfiguration setDeploymentDataManager(FormDeploymentDataManager deploymentDataManager) {
        this.deploymentDataManager = deploymentDataManager;
        return this;
    }

    public FormDefinitionDataManager getFormDefinitionDataManager() {
        return formDefinitionDataManager;
    }

    public FormEngineConfiguration setFormDefinitionDataManager(FormDefinitionDataManager formDefinitionDataManager) {
        this.formDefinitionDataManager = formDefinitionDataManager;
        return this;
    }

    public FormResourceDataManager getResourceDataManager() {
        return resourceDataManager;
    }

    public FormEngineConfiguration setResourceDataManager(FormResourceDataManager resourceDataManager) {
        this.resourceDataManager = resourceDataManager;
        return this;
    }

    public FormInstanceDataManager getFormInstanceDataManager() {
        return formInstanceDataManager;
    }

    public FormEngineConfiguration setFormInstanceDataManager(FormInstanceDataManager formInstanceDataManager) {
        this.formInstanceDataManager = formInstanceDataManager;
        return this;
    }

    public FormDeploymentEntityManager getDeploymentEntityManager() {
        return deploymentEntityManager;
    }

    public FormEngineConfiguration setDeploymentEntityManager(FormDeploymentEntityManager deploymentEntityManager) {
        this.deploymentEntityManager = deploymentEntityManager;
        return this;
    }

    public FormDefinitionEntityManager getFormDefinitionEntityManager() {
        return formDefinitionEntityManager;
    }

    public FormEngineConfiguration setFormDefinitionEntityManager(FormDefinitionEntityManager formDefinitionEntityManager) {
        this.formDefinitionEntityManager = formDefinitionEntityManager;
        return this;
    }

    public FormResourceEntityManager getResourceEntityManager() {
        return resourceEntityManager;
    }

    public FormEngineConfiguration setResourceEntityManager(FormResourceEntityManager resourceEntityManager) {
        this.resourceEntityManager = resourceEntityManager;
        return this;
    }

    public FormInstanceEntityManager getFormInstanceEntityManager() {
        return formInstanceEntityManager;
    }

    public FormEngineConfiguration setFormInstanceEntityManager(FormInstanceEntityManager formInstanceEntityManager) {
        this.formInstanceEntityManager = formInstanceEntityManager;
        return this;
    }

    public TableDataManager getTableDataManager() {
        return tableDataManager;
    }

    public FormEngineConfiguration setTableDataManager(TableDataManager tableDataManager) {
        this.tableDataManager = tableDataManager;
        return this;
    }

    public ExpressionManager getExpressionManager() {
        return expressionManager;
    }

    public FormEngineConfiguration setExpressionManager(ExpressionManager expressionManager) {
        this.expressionManager = expressionManager;
        return this;
    }

    public FormJsonConverter getFormJsonConverter() {
        return formJsonConverter;
    }

    public FormEngineConfiguration setFormJsonConverter(FormJsonConverter formJsonConverter) {
        this.formJsonConverter = formJsonConverter;
        return this;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public FormEngineConfiguration setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        return this;
    }
}
