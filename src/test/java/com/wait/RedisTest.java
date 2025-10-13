package com.wait;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RedisTest {

    @Resource
    private RTForString RTForString;

    private static final Logger log = LoggerFactory.getLogger(RTForString.class);

    @Test
    void testRTForStringAllMethod() throws InterruptedException {
        log.info("name = {}", RTForString.get("name"));
        RTForString.set("name", "yanhengzhi");
        log.info("after set, name = {}", RTForString.get("name"));

        Map<String, String> data = new HashMap<>();
        data.put("sex", "male");
        data.put("age", "21");
        RTForString.mset(data);

        List<String> allKeys = Arrays.asList("name", "age", "sex", "testNone");
        List<String> mgetRes = RTForString.mget(allKeys);
        log.info("keys = {}, res = {}", allKeys, mgetRes);

        Long age = RTForString.incr("age");
        log.info("after incr, age = {}, redis value = {}", age, RTForString.get("age"));

        age = RTForString.incrBy("age", 2L);
        log.info("after incrBy 2, age = {}, redis value = {}", age, RTForString.get("age"));

        Boolean ageSetFlag = RTForString.setNx("age", "18", Duration.ofSeconds(3));
        log.info("setNx age flag: {}", ageSetFlag);

        Boolean setFlag = RTForString.setNx("height", "187", Duration.ofSeconds(3));
        log.info("setNx height flag: {}, value = {}", setFlag, RTForString.get("height"));

        TimeUnit.SECONDS.sleep(3);
        log.info("after 3s, height = {}", RTForString.get("height"));

        RTForString.setEx("weight", "80", Duration.ofSeconds(3));
        log.info("setEx weight value = {}", RTForString.get("weight"));

        TimeUnit.SECONDS.sleep(3);
        log.info("after 3s, weight = {}", RTForString.get("weight"));
    }
}
