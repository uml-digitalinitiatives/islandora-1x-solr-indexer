package ca.umanitoba.dam.islandora.fc3indexer;

import static ca.umanitoba.dam.islandora.fc3indexer.config.IndexerProps.DEFAULT_PROPERTY_FILE;

import java.io.File;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    /**
     * Main application.
     * @param args command line args.
     */
    public static void main(final String[] args) {
        final String prop = System.getProperty(DEFAULT_PROPERTY_FILE, null);
        if (prop == null) {
            System.out.println("You need to specify the location of the configuration file with -Dfc3indexer.config" +
                    ".file=");
            return;
        } else {
            final File propFile = new File(prop);
            if (!(propFile.exists() || propFile.canRead())) {
                System.out.println("Property file " + prop + " is not a readable file.");
                return;
            }
        }
        SpringApplication.run(Application.class, args);
    }
}
