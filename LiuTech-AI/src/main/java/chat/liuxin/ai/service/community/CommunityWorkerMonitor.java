package chat.liuxin.ai.service.community;

import chat.liuxin.ai.dto.community.CommunityTask;
import chat.liuxin.ai.dto.community.CommunityWorkerStatus;
import chat.liuxin.ai.mapper.CommunityMapper;
import chat.liuxin.ai.infra.config.AiChatProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 仅保存当前进程的执行状态。锁内不做数据库、网络或模型 I/O，读取不等待模型返回。 */
@Service
public class CommunityWorkerMonitor {
    private final CommunityMapper mapper;
    private final Clock clock;
    private final long pollIntervalMs;
    private final long initialDelayMs;
    private final long modelTimeoutMs;
    private final long progressWarningMs;
    private final String instanceId=UUID.randomUUID().toString();
    private final Instant startedAt;
    private Instant heartbeatAt,pollStartedAt,pollFinishedAt,successfulPollAt,phaseStartedAt,previousPhaseStartedAt,previousPhaseFinishedAt;
    private Instant errorAt,currentTaskStartedAt,lastTaskStartedAt,lastTaskFinishedAt;
    private Instant progressAt,modelDeadlineAt;
    private String phase="STARTING",previousPhase,error,taskId,model,leaseToken,lastTaskId,lastTaskStatus;
    private Long botId,postId,eventId;
    private boolean busy,closed,cancelled,pollFailed;
    private boolean modelInFlight;
    private int failures;

    @Autowired
    public CommunityWorkerMonitor(CommunityMapper mapper,AiChatProperties properties,
        @Value("${spring.ai.community.poll-delay-ms:5000}") long pollIntervalMs,
        @Value("${spring.ai.community.initial-delay-ms:15000}") long initialDelayMs,
        @Value("${spring.ai.community.progress-warning-ms:60000}") long progressWarningMs) {
        this(mapper,pollIntervalMs,initialDelayMs,Math.max(1000,properties.getSseTimeout()),progressWarningMs,Clock.systemUTC());
    }
    CommunityWorkerMonitor(CommunityMapper mapper,long pollIntervalMs,long initialDelayMs,long modelTimeoutMs,long progressWarningMs,Clock clock) {
        this.mapper=mapper;this.clock=clock;this.pollIntervalMs=Math.max(1,pollIntervalMs);
        this.initialDelayMs=Math.max(0,initialDelayMs);this.startedAt=clock.instant();this.phaseStartedAt=startedAt;
        this.modelTimeoutMs=modelTimeoutMs;
        this.progressWarningMs=Math.max(1000,progressWarningMs);this.progressAt=startedAt;
    }
    public synchronized void heartbeat() { if(!closed) heartbeatAt=clock.instant(); }
    public synchronized void pollStarted() { if(closed)return;busy=true;pollFailed=false;pollStartedAt=clock.instant();changePhase("CLAIMING_EVENTS"); }
    public synchronized void phase(String value) { if(!closed) changePhase(value); }
    public synchronized void event(long id,long bot,long post) { if(closed)return;eventId=id;botId=bot;postId=post;progressAt=clock.instant();changePhase("INGESTING_EVENTS"); }
    public synchronized void acquiredLease(String token) { if(!closed)leaseToken=token; }
    public synchronized void releasedLease(String token) { if(token.equals(leaseToken))leaseToken=null; }
    public synchronized void taskStarted(CommunityTask task) {
        if(closed)return;
        taskId=task.getId();botId=task.getBotId();postId=task.getPostId();eventId=task.getEventId();
        model=null;modelInFlight=false;modelDeadlineAt=null;cancelled=false;currentTaskStartedAt=clock.instant();changePhase("CHECKING_SOURCE");
    }
    /** 预演和已经结束/取消的调用不能覆盖当前后台任务。 */
    public synchronized void taskPhase(String id,String value) {
        if(!closed && !cancelled && id.equals(taskId)) changePhase(value);
    }
    public synchronized void model(String id,String value) {
        if(!closed && !cancelled && id.equals(taskId)) {
            model=value;modelInFlight=true;modelDeadlineAt=clock.instant().plusMillis(modelTimeoutMs);changePhase("MODEL_GENERATION");
        }
    }
    public synchronized void modelFinished(String id) {
        if(id.equals(taskId)) {
            modelInFlight=false;progressAt=clock.instant();
            if(!closed && !cancelled) changePhase("VALIDATING_RESULT");
        }
    }
    public synchronized void progress(String id) {
        if(!closed && !cancelled && id!=null && id.equals(taskId)) progressAt=clock.instant();
    }
    public synchronized void cancelled(String id) {
        if(!closed && id.equals(taskId)) { cancelled=true;changePhase("CANCELLING"); }
    }
    public synchronized void taskFinished(String id,String status) {
        if(!id.equals(taskId)) return;
        lastTaskId=id;lastTaskStatus=cancelled?"CANCELLED":status;lastTaskFinishedAt=clock.instant();
        lastTaskStartedAt=currentTaskStartedAt;currentTaskStartedAt=null;
        taskId=null;botId=null;postId=null;eventId=null;model=null;modelInFlight=false;modelDeadlineAt=null;cancelled=false;if(!closed)changePhase("FINISHING");
    }
    public synchronized void taskError(String id,String reason) {
        if(id.equals(taskId) && !cancelled) {error=reason;errorAt=clock.instant();}
    }
    public synchronized void pollError(String reason) {pollFailed=true;error=reason;errorAt=clock.instant();}
    public synchronized void pollFinished() {
        busy=false;leaseToken=null;eventId=null;botId=null;postId=null;pollFinishedAt=clock.instant();
        if(pollFailed) failures++;else { failures=0;successfulPollAt=pollFinishedAt; }
        if(!closed) changePhase(pollFailed?"ERROR":"IDLE");
    }
    public synchronized void stopped() {closed=true;changePhase("STOPPED");}
    private void changePhase(String value) {
        if(value.equals(phase)) return;
        previousPhase=phase;previousPhaseStartedAt=phaseStartedAt;previousPhaseFinishedAt=clock.instant();phase=value;phaseStartedAt=previousPhaseFinishedAt;progressAt=phaseStartedAt;
    }
    private synchronized Local local() {
        return new Local(heartbeatAt,pollStartedAt,pollFinishedAt,successfulPollAt,phase,phaseStartedAt,previousPhase,
            previousPhaseStartedAt,previousPhaseFinishedAt,taskId,botId,postId,eventId,model,leaseToken,busy,closed,cancelled,error,errorAt,
            failures,progressAt,modelInFlight,modelDeadlineAt,currentTaskStartedAt,lastTaskId,lastTaskStatus,lastTaskStartedAt,lastTaskFinishedAt);
    }
    private record Local(Instant heartbeatAt,Instant pollStartedAt,Instant pollFinishedAt,Instant successfulPollAt,
        String phase,Instant phaseStartedAt,String previousPhase,Instant previousPhaseStartedAt,Instant previousPhaseFinishedAt,String taskId,
        Long botId,Long postId,Long eventId,String model,String leaseToken,boolean busy,boolean closed,boolean cancelled,
        String error,Instant errorAt,int failures,Instant progressAt,boolean modelInFlight,Instant modelDeadlineAt,
        Instant currentTaskStartedAt,String lastTaskId,String lastTaskStatus,Instant lastTaskStartedAt,Instant lastTaskFinishedAt) {}

    public CommunityWorkerStatus status() {
        Local current=local();
        Local requested=current;
        Map<String,Object> worker=null,counts=null;
        String taskStatus=null;
        boolean databaseAvailable=true;
        try {
            worker=mapper.workerStatus();counts=mapper.queueStatus();
            if(worker==null || counts==null) databaseAvailable=false;
            if(current.taskId()!=null) {
                CommunityTask task=mapper.taskById(current.taskId());
                taskStatus=task==null?null:task.getStatus();
                if("CANCELLED".equals(taskStatus)) {cancelled(current.taskId());current=local();}
            }
        } catch(RuntimeException unavailable) {databaseAvailable=false;}
        current=local();
        if(!java.util.Objects.equals(requested.taskId(),current.taskId())) taskStatus=null;
        boolean leaseReadChanged=!java.util.Objects.equals(requested.leaseToken(),current.leaseToken());
        Instant now=clock.instant();
        long heartbeatAge=current.heartbeatAt()==null?-1:age(current.heartbeatAt(),now);
        boolean schedulerAlive=!current.closed() && heartbeatAge>=0 && heartbeatAge<=Math.max(15000,pollIntervalMs*3);
        var queue=new CommunityWorkerStatus.Queue(number(counts,"readyCount"),number(counts,"delayedCount"),
            number(counts,"leasedCount"),number(counts,"failedCount"),nullableNumber(counts,"nextDueSeconds"),number(counts,"oldestReadySeconds"));
        Long leaseRemaining=nullableNumber(worker,"leaseRemainingSeconds");
        String databaseToken=string(worker,"leaseToken");
        boolean leaseOccupied=databaseToken!=null && number(worker,"leaseOccupied")>0;
        boolean leaseOwned=leaseOccupied && databaseToken.equals(current.leaseToken());
        String leaseState=!databaseAvailable || leaseReadChanged?"UNKNOWN":leaseOccupied?(leaseOwned?"OWNED_HERE":"OWNED_ELSEWHERE"):
            leaseRemaining!=null?"EXPIRED":"AVAILABLE";
        String state,reason;
        if(current.closed()) {state="STOPPED";reason="本实例执行器已停止";}
        else if(!schedulerAlive && age(startedAt,now)>initialDelayMs+Math.max(15000,pollIntervalMs*3)) {
            state="STALE";reason="调度心跳已超时，不能确认后台执行器仍在工作";
        } else if(!databaseAvailable) {state="ERROR";reason="无法读取数据库执行租约和队列，当前阶段仅代表本实例";}
        else if(current.modelInFlight() && current.modelDeadlineAt()!=null && now.isAfter(current.modelDeadlineAt())) {
            state="STALE";reason="在途模型调用超过配置执行时限，需核对执行线程超时或取消收尾";
        } else if(current.busy() && !current.modelInFlight() && age(current.progressAt(),now)>progressWarningMs) {
            state="STALE";reason="当前阶段进展未更新超过检查窗口，可能正在等待数据库或内部服务；需核对执行线程";
        }
        else if(current.cancelled()) {state="CANCELLING";reason="任务已取消；等待在途调用返回，生成结果不会发表";}
        else if(current.busy()) {
            state=current.taskId()==null?"POLLING":"RUNNING";
            reason=current.taskId()==null?"正在领取或交接社区任务":"后台正在执行当前任务";
            if(current.leaseToken()!=null && !leaseOwned && !leaseReadChanged) {state="STALE";reason="本实例的执行租约已失效，需核对其他实例或数据库连接";}
        } else if(leaseOccupied) {state="WAITING_LEASE";reason="另一个实例或尚未释放的执行租约占用队列；剩余 "+leaseRemaining+" 秒";}
        else if(current.failures()>0) {state="ERROR";reason="最近轮询失败，等待下次重试；"+current.error();}
        else if(current.heartbeatAt()==null) {state="STARTING";reason="服务已启动，等待首次调度；尚无 Worker 心跳";}
        else if(queue.delayedCount()>0 && queue.readyCount()==0) {
            state="WAITING_TASK";reason="任务尚未到期，数据库预计 "+queue.nextDueSeconds()+" 秒后可领取";
        } else if(queue.leasedCount()>0 && queue.readyCount()==0) {
            state="WAITING_LEASE";reason="任务租约尚未释放，等待执行完成或租约到期";
        } else {
            state="IDLE";reason=queue.readyCount()>0?"已有可执行任务，等待下次轮询":
                queue.failedCount()>0?"暂无自动任务；失败任务等待管理员重试":"暂无可执行任务";
        }
        var database=new CommunityWorkerStatus.Database(databaseAvailable,string(worker,"databaseNow"),
            string(worker,"sessionTimeZone"),string(worker,"systemTimeZone"));
        Long phaseTimeout=current.modelInFlight()?modelTimeoutMs:null;
        Instant phaseDeadline=current.modelInFlight()?current.modelDeadlineAt():null;
        return new CommunityWorkerStatus(instanceId,now,startedAt,current.heartbeatAt(),heartbeatAge,schedulerAlive,
            pollIntervalMs,initialDelayMs,current.pollStartedAt(),current.pollFinishedAt(),current.successfulPollAt(),
            current.busy(),state,current.phase(),current.phaseStartedAt(),age(current.phaseStartedAt(),now),
            current.progressAt(),age(current.progressAt(),now),progressWarningMs,
            modelTimeoutMs,phaseTimeout,phaseDeadline,
            current.previousPhase(),current.previousPhaseStartedAt(),current.previousPhaseFinishedAt(),current.taskId(),current.botId(),current.postId(),
            current.eventId(),current.currentTaskStartedAt(),taskStatus,current.model(),leaseOwned,leaseState,leaseRemaining,reason,current.error(),
            current.errorAt(),current.failures(),current.lastTaskId(),current.lastTaskStatus(),current.lastTaskStartedAt(),
            current.lastTaskFinishedAt(),queue,database);
    }
    private static long age(Instant from,Instant now) {return Math.max(0,Duration.between(from,now).toMillis());}
    private static long number(Map<String,Object> row,String key) {Long value=nullableNumber(row,key);return value==null?0:value;}
    private static Long nullableNumber(Map<String,Object> row,String key) {Object value=row==null?null:row.get(key);return value instanceof Number n?n.longValue():null;}
    private static String string(Map<String,Object> row,String key) {Object value=row==null?null:row.get(key);return value==null?null:value.toString();}
}
