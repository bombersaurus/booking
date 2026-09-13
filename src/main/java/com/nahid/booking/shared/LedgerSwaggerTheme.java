package com.nahid.booking.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class LedgerSwaggerTheme extends SwaggerIndexPageTransformer {
    public LedgerSwaggerTheme(SwaggerUiConfigProperties config, SwaggerUiOAuthProperties oauth,
                              SwaggerWelcomeCommon welcome, ObjectMapperProvider mapper) {
        super(config, oauth, welcome, mapper);
    }

    @Override
    public Resource transform(HttpServletRequest request, Resource resource,
                              ResourceTransformerChain chain) throws IOException {
        // Preserve springdoc's generated configuration and bundled Swagger behaviour.
        Resource transformed = super.transform(request, resource, chain);
        if (!"index.html".equals(resource.getFilename())) {
            return transformed;
        }
        String page;
        try (var input = transformed.getInputStream()) {
            page = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String base = request.getContextPath();
        page = page.replace("<title>Swagger UI</title>", "<title>Ledger | Booking workspace</title>")
                .replace("</head>", """
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <link rel="stylesheet" href="%s/docs/ledger.css">
                        </head>
                        """.formatted(base))
                .replace("<body>", """
                        <body>
                        <header class="ledger-nav">
                          <a class="ledger-brand" href="#operations" aria-label="Ledger booking operations">
                            <span class="ledger-symbol" aria-hidden="true"><i></i><i></i><i></i></span>
                            Ledger<span class="ledger-product">Booking API</span>
                          </a>
                          <span class="ledger-version">Version 3</span>
                        </header>
                        <div class="ledger-intro">
                          <span class="ledger-eyebrow">YOUR API PLAYGROUND</span>
                          <p>Good classes.<br><span>Simple bookings.</span></p>
                          <div class="ledger-caption">A little space to explore what you can build.</div>
                        </div>
                        """)
                .replace("</body>", "<script src=\"" + base + "/docs/ledger.js\"></script></body>");
        return new TransformedResource(transformed, page.getBytes(StandardCharsets.UTF_8));
    }
}

