package chat.liuxin.liutech.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.regex.Pattern;

/** 仅供显式启用的本地 MySQL 测试使用；建随机临时库，只读取权威脚本的 DDL。 */
class MysqlFixture implements AutoCloseable {
    final String baseUrl;
    final String schema = "liutech_test_" + UUID.randomUUID().toString().replace("-", "");
    final String username = System.getenv().getOrDefault("LIUTECH_TEST_MYSQL_USER", "root");
    final String password = System.getenv().getOrDefault("LIUTECH_TEST_MYSQL_PASSWORD", "");
    final DriverManagerDataSource dataSource;
    final JdbcTemplate jdbc;
    final SqlSessionTemplate session;
    final Path repositoryRoot;

    MysqlFixture(String... mapperXml) throws Exception {
        this(java.util.Map.of(),mapperXml);
    }

    /** 单项回归可指定连接与会话时区，不影响其它测试连接。 */
    MysqlFixture(java.util.Map<String,String> connectionOptions,String... mapperXml) throws Exception {
        String configuredUrl = System.getenv("LIUTECH_TEST_MYSQL_URL");
        String options = connectionOptions.entrySet().stream().map(entry -> "&"
            +java.net.URLEncoder.encode(entry.getKey(),java.nio.charset.StandardCharsets.UTF_8)+"="
            +java.net.URLEncoder.encode(entry.getValue(),java.nio.charset.StandardCharsets.UTF_8))
            .collect(java.util.stream.Collectors.joining());
        baseUrl = configuredUrl+options;
        if (!baseUrl.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/\\?.*")) {
            throw new IllegalArgumentException("测试只接受本机无库名的 JDBC URL");
        }
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
        }
        dataSource = new DriverManagerDataSource(baseUrl.replace("/?", "/" + schema + "?"), username, password);
        jdbc = new JdbcTemplate(dataSource);
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("Docs/SQL/sql.sql"))) root = root.getParent();
        repositoryRoot = root;
        String sql = Files.readString(root.resolve("Docs/SQL/sql.sql"));
        var ddl = Pattern.compile("CREATE TABLE IF NOT EXISTS [a-z_]+\\s*\\([\\s\\S]+?\\) ENGINE\\s*=[\\s\\S]+?;").matcher(sql);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            while (ddl.find()) statement.execute(ddl.group());
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setGlobalConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig()
                .setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig()
                        .setLogicNotDeleteValue("NULL").setLogicDeleteValue("NOW()")));
        if (mapperXml.length > 0) factory.setMapperLocations(java.util.Arrays.stream(mapperXml).map(ClassPathResource::new)
                .toArray(org.springframework.core.io.Resource[]::new));
        var sqlSessionFactory = factory.getObject();
        sqlSessionFactory.getConfiguration().addMapper(chat.liuxin.liutech.mapper.UserPurgeTaskMapper.class);
        session = new SqlSessionTemplate(sqlSessionFactory);
    }

    void useBaseline(String scope) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            var names = new java.util.ArrayList<String>();
            try (var tables = connection.getMetaData().getTables(schema, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) names.add(tables.getString("TABLE_NAME"));
            }
            for (String table : names) {
                if (!table.matches("[a-z_]+")) throw new IllegalStateException("未知测试表名");
                statement.execute("DROP TABLE " + table);
            }
            String sql = Files.readString(repositoryRoot.resolve("Docs/SQL/migrations/baseline/" + scope + "-v0.sql"));
            var ddl = Pattern.compile("CREATE TABLE IF NOT EXISTS [a-z_]+\\s*\\([\\s\\S]+?\\) ENGINE\\s*=[\\s\\S]+?;").matcher(sql);
            while (ddl.find()) statement.execute(ddl.group());
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    java.util.Map<String, String> structure(String scope) throws Exception {
        var result = new java.util.TreeMap<String, String>();
        try (var connection = dataSource.getConnection()) {
            var metadata = connection.getMetaData();
            var names = new java.util.ArrayList<String>();
            try (var tables = metadata.getTables(schema, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String table = tables.getString("TABLE_NAME");
                    if (!table.equals("flyway_schema_history") && table.startsWith("ai_") == scope.equals("ai")) names.add(table);
                }
            }
            for (String table : names) {
                try (var columns = metadata.getColumns(schema, null, table, null)) {
                    while (columns.next()) {
                        if (!table.equals(columns.getString("TABLE_NAME"))) continue;
                        result.put(table + ".column." + columns.getString("COLUMN_NAME"),
                                columns.getString("TYPE_NAME") + ":" + columns.getString("COLUMN_SIZE") + ":"
                                + columns.getString("DECIMAL_DIGITS") + ":" + columns.getString("NULLABLE") + ":"
                                + columns.getString("COLUMN_DEF") + ":" + columns.getString("IS_AUTOINCREMENT"));
                    }
                }
                try (var indexes = metadata.getIndexInfo(schema, null, table, false, false)) {
                    while (indexes.next()) {
                        String name = indexes.getString("INDEX_NAME");
                        if (name != null) result.put(table + ".index." + name + "." + indexes.getString("ORDINAL_POSITION"),
                                indexes.getString("COLUMN_NAME") + ":" + indexes.getString("NON_UNIQUE"));
                    }
                }
                try (var keys = metadata.getImportedKeys(schema, null, table)) {
                    while (keys.next()) result.put(table + ".foreign." + keys.getString("FKCOLUMN_NAME"),
                            keys.getString("PKTABLE_NAME") + ":" + keys.getString("PKCOLUMN_NAME") + ":"
                            + keys.getString("DELETE_RULE") + ":" + keys.getString("UPDATE_RULE"));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    <T> T transactional(T service) {
        var proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    @Override public void close() throws Exception {
        try (var connection = DriverManager.getConnection(baseUrl, username, password);
             var statement = connection.createStatement()) {
            statement.execute("DROP DATABASE " + schema);
        }
    }
}
