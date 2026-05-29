package chat.liuxin.ai.infra.security;

class AiRateLimitBucket {
    private long windowStartMillis;
    private int count;
    private long lastSeenMillis;

    AiRateLimitBucket(long now) {
        this.windowStartMillis = now;
        this.lastSeenMillis = now;
    }

    synchronized boolean tryAcquire(long now, long windowMillis, int maxRequests) {
        lastSeenMillis = now;
        if (now - windowStartMillis >= windowMillis) {
            windowStartMillis = now;
            count = 0;
        }
        if (count >= maxRequests) {
            return false;
        }
        count++;
        return true;
    }

    synchronized boolean expired(long now, long ttlMillis) {
        return now - lastSeenMillis > ttlMillis;
    }
}
