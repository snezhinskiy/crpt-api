package org.sugrob.crptapiproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@SpringBootApplication
public class CrptApiProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrptApiProjectApplication.class, args);

        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);


        IntStream.range(0, 10).parallel().forEach(i -> {
            /**
             * Every request will return 401 error, because we have skipped authentication process in client
             */
            api.CreateDocument(
                /**
                 * Skip initialization, just sent empty object for test purposes
                 */
                new CrptApi.CrptDocument(),
                /**
                 * Real DTO must be here
                 */
                Object.class
            );
        });

    }

}
