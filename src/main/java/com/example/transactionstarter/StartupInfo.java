package com.example.transactionstarter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Prints the URLs once the server is up, so you don't have to remember the port.
 */
@Component
class StartupInfo {

    private static final Logger log = LoggerFactory.getLogger(StartupInfo.class);

    private final Environment environment;

    StartupInfo(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    void logUrls() {
        String port = environment.getProperty("server.port", "8080");
        log.info("""

                ----------------------------------------------------------
                  Counter is running. Open one of these in a browser:
                    Console      http://localhost:{}/
                    Sample API   http://localhost:{}/api/sample
                    H2 console   http://localhost:{}/h2-console
                ----------------------------------------------------------""",
                port, port, port);
    }
}
