package com.danzzan.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisLuaScriptConfig {

    @Bean("claimV2Script")
    public RedisScript<List> claimV2Script() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/claim_v2.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean("claimRollbackScript")
    public RedisScript<Long> claimRollbackScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/claim_rollback.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("passwordResetConsumeScript")
    public RedisScript<List> passwordResetConsumeScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/password_reset_consume.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean("readyToActiveScript")
    public RedisScript<Long> readyToActiveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/ready_to_active.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("enterQueueScript")
    public RedisScript<List> enterQueueScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/enter_queue.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean("admitOneWaitingUserScript")
    public RedisScript<Long> admitOneWaitingUserScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/admit_one_waiting_user.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("expireReadyUsersScript")
    public RedisScript<List> expireReadyUsersScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/expire_ready_users.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean("expireActiveUsersScript")
    public RedisScript<List> expireActiveUsersScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/expire_active_users.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean("queueStatusSnapshotScript")
    public RedisScript<List> queueStatusSnapshotScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/queue_status_snapshot.lua"));
        script.setResultType(List.class);
        return script;
    }

}
