package com.example.helpdesk.e2e;

import com.example.helpdesk.model.*;
import com.example.helpdesk.repo.TicketRepository;
import com.example.helpdesk.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "browser.tests", matches = "true")
class TicketBrowserFlowTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired TicketRepository tickets;
    @Autowired PasswordEncoder encoder;
    private WebDriver browser;
    private String employeeUsername;

    @BeforeEach
    void createAccountsAndBrowser() {
        String suffix = Long.toString(System.nanoTime());
        employeeUsername = "browser-employee-" + suffix;
        users.save(new User(employeeUsername, encoder.encode("employee-pass-123"), "Browser Employee", Role.EMPLOYEE));
        users.save(new User("browser-tech-" + suffix, encoder.encode("technician-pass-123"), "Browser Technician", Role.TECHNICIAN));
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1440,1000");
        browser = new ChromeDriver(options);
    }

    @AfterEach
    void closeBrowser() {
        if (browser != null) browser.quit();
    }

    @Test
    void employeeSubmitsAndTracksTicketThenTechnicianAssignsAndResolvesIt() {
        login(employeeUsername, "employee-pass-123");
        assertTrue(browser.getCurrentUrl().endsWith("/tickets/new"));
        browser.findElement(By.name("title")).sendKeys("Browser workflow request");
        browser.findElement(By.name("description")).sendKeys("Need a display adapter for a meeting room.");
        new Select(browser.findElement(By.name("category"))).selectByValue("HARDWARE");
        new Select(browser.findElement(By.name("priority"))).selectByValue("HIGH");
        browser.findElement(By.cssSelector("form[action='/tickets'] button[type='submit']")).click();
        assertTrue(browser.getPageSource().contains("submitted successfully"), () -> browser.getCurrentUrl() + "\n" + browser.getPageSource());
        browser.findElement(By.linkText("My tickets")).click();
        browser.findElement(By.linkText("View progress")).click();
        assertTrue(browser.getPageSource().contains("Browser workflow request"));
        captureScreenshot("employee-ticket-progress.png");
        Long ticketId = tickets.findAll().stream().filter(t -> t.getTitle().equals("Browser workflow request"))
                .findFirst().orElseThrow().getId();

        String technicianUsername = users.findAll().stream().filter(u -> u.getRole() == Role.TECHNICIAN && u.getFullName().equals("Browser Technician"))
                .findFirst().orElseThrow().getUsername();
        browser.quit();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1440,1000");
        browser = new ChromeDriver(options);
        login(technicianUsername, "technician-pass-123");
        browser.get(baseUrl() + "/tickets/" + ticketId);
        assertTrue(browser.getPageSource().contains("Browser workflow request"));
        captureScreenshot("technician-ticket-detail.png");
        new Select(browser.findElement(By.name("technicianId"))).selectByValue(users.findByUsername(technicianUsername).orElseThrow().getId().toString());
        browser.findElement(By.cssSelector("form[action$='/assign'] button[type='submit']")).click();
        new WebDriverWait(browser, Duration.ofSeconds(10)).until(driver -> driver.findElement(By.id("status")).isDisplayed());
        new Select(browser.findElement(By.id("status"))).selectByValue("RESOLVED");
        browser.findElement(By.id("notes")).sendKeys("Provided and tested the required adapter.");
        browser.findElement(By.cssSelector("form[action$='/status'] button[type='submit']")).click();
        assertTrue(browser.getPageSource().contains("RESOLVED"));
    }

    private void login(String username, String password) {
        browser.get(baseUrl() + "/login");
        browser.findElement(By.name("username")).sendKeys(username);
        browser.findElement(By.name("password")).sendKeys(password);
        browser.findElement(By.cssSelector("button[type='submit'], form button:not([type])")).click();
    }

    private String baseUrl() { return "http://localhost:" + port; }

    private void captureScreenshot(String fileName) {
        String screenshotDirectory = System.getProperty("screenshots.dir");
        if (screenshotDirectory == null || screenshotDirectory.isBlank()) return;
        try {
            Path directory = Path.of(screenshotDirectory);
            Files.createDirectories(directory);
            Path screenshot = ((org.openqa.selenium.TakesScreenshot) browser)
                    .getScreenshotAs(org.openqa.selenium.OutputType.FILE).toPath();
            Files.copy(screenshot, directory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not save application screenshot", exception);
        }
    }
}
