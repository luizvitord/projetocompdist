package com.distrischool.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "ADMINISTRATIVA_URL=http://administrativa.test",
        "ESCOLAR_URL=http://escolar.test"
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
