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
    public RedisScript<Long> enterQueueScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/enter_queue.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("admitOneWaitingUserScript")
    public RedisScript<String> admitOneWaitingUserScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/admit_one_waiting_user.lua"));
        script.setResultType(String.class);
        return script;
    }

    @Bean("admitNWaitingUsersScript")
    public RedisScript<List> admitNWaitingUsersScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/admit_n_waiting_users.lua"));
        script.setResultType(List.class);
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
}
