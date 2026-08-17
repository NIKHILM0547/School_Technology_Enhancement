package com.eduadmin.school.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Dedicated XML-mode Thymeleaf engine for the report-card PDF template.
 * Flying Saucer (XHTML -&gt; PDF) requires well-formed XML, so we render this
 * template in XML mode rather than the lenient HTML mode used for web pages.
 */
@Configuration
public class ReportCardTemplateConfig {

    @Bean
    public SpringTemplateEngine reportCardTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setEnableSpringELCompiler(false);
        return engine;
    }
}