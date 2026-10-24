package com.distrischool.gestao_escolar.common;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confere que o springdoc serve a documentação e os recursos estáticos do Swagger UI sob o
 * prefixo {@code /escolar}, usado pelo Ingress para separar os serviços.
 */
@WebMvcTest(controllers = OpenApiConfig.class)
@Import(OpenApiConfig.class)
@ImportAutoConfiguration({
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class,
        SwaggerConfig.class,
        SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class
})
class SwaggerPrefixTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsFicaSobOPrefixo() throws Exception {
        mockMvc.perform(get("/escolar/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("DistriSchool - gestao-escolar-service"));
    }

    @Test
    void swaggerUiRedirecionaParaOIndexSobOPrefixo() throws Exception {
        mockMvc.perform(get("/escolar/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/escolar/swagger-ui/index.html"));
    }

    @Test
    void recursosEstaticosDoSwaggerUiSaoServidosSobOPrefixo() throws Exception {
        mockMvc.perform(get("/escolar/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/escolar/swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/escolar/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/escolar/v3/api-docs/swagger-config")));
    }

    @Test
    void swaggerConfigApontaParaAApiDocsDoPrefixo() throws Exception {
        mockMvc.perform(get("/escolar/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/escolar/v3/api-docs"))
                .andExpect(jsonPath("$.configUrl").value("/escolar/v3/api-docs/swagger-config"));
    }
}
