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

    @Bean("passwordResetConsumeScript")
    public RedisScript<List> passwordResetConsumeScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/password_reset_consume.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean("acquireSlotScript")
    public RedisScript<Long> acquireSlotScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/acquire_slot.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
