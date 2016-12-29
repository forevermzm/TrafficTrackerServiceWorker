package main;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Preconditions.checkArgument;

@ComponentScan(basePackages = {"hello", "aws", "service", "dao", "google"})
public class Application {
    private static final Logger LOG = LogManager.getFormatterLogger();

    public static void main(String[] args) {
        checkArgument(Strings.isNotBlank(System.getProperty("googleApiKey")),
                "Google API key is not provided.");

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
                Application.class);

        final int numOfWorkers = 5;
        final int schedulerThreadCount = 1;
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(schedulerThreadCount);
        TrafficStatusSystem trafficStatusSystem = new TrafficStatusSystem(numOfWorkers, scheduler);

        trafficStatusSystem.start();

        ctx.close();
    }

}