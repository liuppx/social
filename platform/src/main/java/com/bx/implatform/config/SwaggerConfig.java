package com.bx.implatform.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi userApi() {
        String[] paths = {"/**"};
        String[] packagedToMatch = {"com.bx"};
        return GroupedOpenApi.builder().group("Yeying-Social-Platform")
            .pathsToMatch(paths)
            .packagesToScan(packagedToMatch).build();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        Contact contact = new Contact();
        contact.setName("Blue");
        return new OpenAPI().info(new Info()
            .title("Yeying Social接口文档")
            .description("Yeying Social业务平台服务")
            .contact(contact)
            .version("3.0")
            .termsOfService("https://social.yeying.pub")
            .license(new License().name("MIT")
                .url("https://social.yeying.pub")));
    }

}
