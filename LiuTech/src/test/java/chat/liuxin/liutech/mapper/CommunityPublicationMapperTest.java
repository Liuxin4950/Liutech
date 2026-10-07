package chat.liuxin.liutech.mapper;

import chat.liuxin.liutech.resp.CommunityResp;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.executor.SimpleExecutor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.Test;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CommunityPublicationMapperTest {
    @Test void receiptRecheckReadsACommitThatOccurredWhileWaitingForTheArticleLock() throws Exception {
        var configuration=new Configuration();
        new MapperAnnotationBuilder(configuration,CommunityMapper.class).parse();
        var statement=configuration.getMappedStatement(CommunityMapper.class.getName()+".publication",false);
        var committed=new AtomicReference<List<CommunityResp.Published>>(List.of());
        var databaseReads=new AtomicInteger();
        // 使用真实 MyBatis 一级缓存，只替换数据库读取，模拟另一事务在等待文章锁时提交。
        var executor=new SimpleExecutor(configuration,mock(Transaction.class)) {
            @Override @SuppressWarnings("unchecked")
            public <E> List<E> doQuery(MappedStatement mapped,Object parameter,RowBounds bounds,
                                       ResultHandler handler,BoundSql sql) {
                databaseReads.incrementAndGet();
                return (List<E>)committed.get();
            }
        };
        try {
            assertTrue(executor.query(statement,"same-task",RowBounds.DEFAULT,null).isEmpty());
            var receipt=new CommunityResp.Published(99L,new Date(),true);
            committed.set(List.of(receipt));
            var reread=executor.<CommunityResp.Published>query(statement,"same-task",RowBounds.DEFAULT,null);
            assertEquals(List.of(receipt),reread);
            assertEquals(2,databaseReads.get());
        } finally { executor.close(false); }
    }
}
