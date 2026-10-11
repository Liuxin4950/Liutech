package chat.liuxin.liutech.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourcesAssociationMapperTest {
    @Test
    void adminAssociationsIncludeDeletedAndDraftPostsWithoutPaginationTruncation() throws Exception {
        var configuration = new MybatisConfiguration();
        String path = "mapper/ResourcesMapper.xml";
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, path, configuration.getSqlFragments()).parse();
        }
        var boundSql = configuration.getMappedStatement(
                "chat.liuxin.liutech.mapper.ResourcesMapper.selectAssociatedPostsForAdmin")
                .getBoundSql(Map.of("resourceIds", List.of(7L, 8L)));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertTrue(sql.contains("SELECT DISTINCT"), sql);
        assertTrue(sql.contains("INNER JOIN posts p ON p.id = pa.post_id"), sql);
        assertTrue(sql.contains("pa.deleted_at IS NULL"), sql);
        assertTrue(sql.contains("p.deleted_at"), sql);
        assertFalse(sql.contains("p.deleted_at IS NULL"), sql);
        assertFalse(sql.contains("p.status ="), sql);
        assertFalse(sql.contains("LIMIT"), sql);
        assertEquals(2, boundSql.getParameterMappings().size());
        assertTrue(sql.endsWith("ORDER BY pa.resource_id, p.id DESC"), sql);
    }
}
