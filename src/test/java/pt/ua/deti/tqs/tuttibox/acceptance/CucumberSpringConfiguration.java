package pt.ua.deti.tqs.tuttibox.acceptance;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pt.ua.deti.tqs.tuttibox.TuttiBoxApplication;

@CucumberContextConfiguration
@SpringBootTest(classes = TuttiBoxApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CucumberSpringConfiguration {
}
