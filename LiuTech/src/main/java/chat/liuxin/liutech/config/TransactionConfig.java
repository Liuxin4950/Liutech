package chat.liuxin.liutech.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 事务管理配置类
 * 配置事务管理器和事务传播机制
 * 
 * @author liuxin
 */
@Configuration
@EnableTransactionManagement
public class TransactionConfig {

    /**
     * 配置事务管理器
     * 使用DataSourceTransactionManager管理数据库事务
     * 
     * @param dataSource 数据源
     * @return PlatformTransactionManager
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
        transactionManager.setDataSource(dataSource);
        
        // 设置事务超时时间（秒）
        transactionManager.setDefaultTimeout(30);
        
        // 设置是否在事务回滚时验证现有事务
        transactionManager.setValidateExistingTransaction(true);
        
        // 设置是否允许嵌套事务
        transactionManager.setNestedTransactionAllowed(true);
        
        return transactionManager;
    }
}