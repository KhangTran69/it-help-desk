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
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    private String technicianUsername;
    private String technicianFullName;

    @BeforeEach
    void createAccountsAndBrowser() {
        String suffix = Long.toString(System.nanoTime());
        employeeUsername = "browser-employee-" + suffix;
        technicianUsername = "browser-tech-" + suffix;
        technicianFullName = "Browser Technician " + suffix;
        users.save(new User(employeeUsername, encoder.encode("employee-pass-123"), "Browser Employee", Role.EMPLOYEE));
        users.save(new User(technicianUsername, encoder.encode("technician-pass-123"), technicianFullName, Role.TECHNICIAN));
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1440,1000");
        browser = new ChromeDriver(options);
    }

    @AfterEach
    void closeBrowser() {
        if (browser != null) browser.quit();
    }

    @Test
    void loginKeyboardOrderAndPrimaryPagesFitTargetViewports() {
        browser.get(baseUrl() + "/login");
        for (int width : new int[]{320, 560, 760, 850, 1280}) {
            assertNoPageOverflow(width, "login");
        }

        browser.findElement(By.tagName("body")).sendKeys(org.openqa.selenium.Keys.TAB);
        assertEquals("username", browser.switchTo().activeElement().getAttribute("name"), "First Tab should focus username");
        assertVisibleFocus();
        browser.findElement(By.tagName("body")).sendKeys(org.openqa.selenium.Keys.TAB);
        assertEquals("password", browser.switchTo().activeElement().getAttribute("name"), "Second Tab should focus password");
        assertVisibleFocus();
        browser.findElement(By.tagName("body")).sendKeys(org.openqa.selenium.Keys.TAB);
        assertEquals("Sign in", browser.switchTo().activeElement().getText(), "Third Tab should focus Sign in");
        assertVisibleFocus();

        login(employeeUsername, "employee-pass-123");
        browser.get(baseUrl() + "/my-tickets");
        for (int width : new int[]{320, 560, 760, 850, 1280}) {
            assertNoPageOverflow(width, "employee tickets");
        }
        browser.get(baseUrl() + "/tickets/new");
        for (int width : new int[]{320, 560, 760, 850, 1280}) {
            assertNoPageOverflow(width, "ticket form");
        }

        browser.findElement(By.cssSelector("form.signout button")).click();
        login(technicianUsername, "technician-pass-123");
        browser.get(baseUrl() + "/tickets");
        assertEquals("/tickets", java.net.URI.create(browser.getCurrentUrl()).getPath(),
                pageDiagnostic("Expected technician ticket queue"));
        assertTrue(browser.findElement(By.tagName("body")).getText().contains(technicianFullName),
                pageDiagnostic("Ticket queue did not render for this test's technician account"));
        System.out.printf("Selenium technician page: account=%s; url=%s; title=%s; heading=%s%n",
                technicianUsername, browser.getCurrentUrl(), browser.getTitle(),
                browser.findElement(By.cssSelector("main h1")).getText());
        for (int width : new int[]{320, 560, 760, 850, 1280}) {
            assertNoPageOverflow(width, "technician ticket table");
            Object tableOverflow = ((JavascriptExecutor) browser).executeScript(
                    "const table=document.querySelector('.table-wrap'); return table && getComputedStyle(table).overflowX");
            assertNotNull(tableOverflow, pageDiagnostic("Expected .table-wrap on technician ticket queue"));
            assertEquals("auto", tableOverflow,
                    "Technician table wrapper should permit local horizontal scrolling at " + width + "px");
            if (width <= 850) {
                assertTrue((Boolean) ((JavascriptExecutor) browser).executeScript(
                        "return document.querySelector('.table-wrap').scrollWidth > document.querySelector('.table-wrap').clientWidth"),
                        "Technician table should scroll inside its wrapper at constrained width " + width + "px");
            }
        }
    }

    @Test
    void employeeSubmitsAndTracksTicketThenTechnicianAssignsAndResolvesIt() {
        login(employeeUsername, "employee-pass-123");
        assertTrue(browser.getCurrentUrl().endsWith("/tickets/new"));
        tabTo(By.name("title"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys("Browser workflow request");
        tabTo(By.name("description"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys("Need a display adapter for a meeting room.");
        tabTo(By.name("category"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.HOME, "HARDWARE");
        tabTo(By.name("priority"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.HOME, "HIGH");
        tabTo(By.cssSelector("form[action='/tickets'] button[type='submit']"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ENTER);
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Ticket confirmation did not appear at " + browser.getCurrentUrl() + "\n" + browser.getPageSource())
                .until(driver -> driver.getPageSource().contains("submitted successfully"));
        assertTrue(tickets.findAll().stream().anyMatch(t -> t.getTitle().equals("Browser workflow request")
                        && t.getReporter().getUsername().equals(employeeUsername)),
                "Keyboard-submitted employee ticket should be persisted for the signed-in employee");
        browser.findElement(By.linkText("My tickets")).click();
        browser.findElement(By.linkText("View progress")).click();
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Employee ticket progress did not load at " + browser.getCurrentUrl())
                .until(driver -> driver.getPageSource().contains("Browser workflow request"));
        for (int width : new int[]{320, 560, 760, 850, 1280}) {
            assertNoPageOverflow(width, "employee ticket progress");
        }
        browser.get(baseUrl() + "/notifications");
        for (int width : new int[]{320, 560, 760, 850, 1280}) {
            assertNoPageOverflow(width, "employee notifications");
        }
        tabTo(By.cssSelector("form[action='/notifications/read-all'] button[type='submit']"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ENTER);
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Keyboard action did not mark notifications read at " + browser.getCurrentUrl())
                .until(driver -> driver.getPageSource().contains("All notifications marked as read."));
        browser.get(baseUrl() + "/my-tickets");
        browser.findElement(By.linkText("View progress")).click();
        tabTo(By.id("comment-content"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys("The room is available after lunch.");
        tabTo(By.cssSelector("form.comment-form button[type='submit']"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ENTER);
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Employee comment was not displayed at " + browser.getCurrentUrl())
                .until(driver -> driver.getPageSource().contains("The room is available after lunch."));
        browser.navigate().refresh();
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Employee comment did not persist after reload at " + browser.getCurrentUrl())
                .until(driver -> driver.getPageSource().contains("The room is available after lunch."));
        captureScreenshot("employee-ticket-progress.png");
        Long ticketId = tickets.findAll().stream()
                .filter(t -> t.getTitle().equals("Browser workflow request"))
                .filter(t -> t.getReporter().getUsername().equals(employeeUsername))
                .findFirst().orElseThrow().getId();

        browser.quit();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage", "--window-size=1440,1000");
        browser = new ChromeDriver(options);
        login(technicianUsername, "technician-pass-123");
        browser.get(baseUrl() + "/tickets/" + ticketId);
        browser.get(baseUrl() + "/notifications");
        for (int width : new int[]{320, 560, 760, 850, 1280}) {
            assertNoPageOverflow(width, "technician notifications");
        }
        browser.get(baseUrl() + "/tickets/" + ticketId);
        assertTrue(browser.getPageSource().contains("Browser workflow request"));
        browser.findElement(By.cssSelector("form.comment-form textarea[name='content']"))
                .sendKeys("Internal note: confirm the room equipment inventory.");
        browser.findElement(By.cssSelector("form.comment-form input[name='internalNote']")).click();
        browser.findElement(By.cssSelector("form.comment-form button[type='submit']")).click();
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Technician internal note was not displayed at " + browser.getCurrentUrl())
                .until(driver -> driver.getPageSource().contains("Internal note: confirm the room equipment inventory."));
        captureScreenshot("technician-ticket-detail.png");
        String technicianId = users.findByUsername(technicianUsername).orElseThrow().getId().toString();
        var assignmentSelect = new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Assignment form was not rendered at " + browser.getCurrentUrl() + "\n" + browser.getPageSource())
                .until(driver -> driver.findElements(By.cssSelector("form[action$='/assign'] select[name='technicianId']"))
                        .stream().findFirst().orElse(null));
        tabTo(By.cssSelector("form[action$='/assign'] select[name='technicianId']"));
        assertVisibleFocus();
        selectFocusedOptionByKeyboard(assignmentSelect, technicianId);
        tabTo(By.cssSelector("form[action$='/assign'] button[type='submit']"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ENTER);
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Keyboard assignment was not saved at " + browser.getCurrentUrl())
                .until(driver -> driver.getPageSource().contains(technicianFullName));
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Ticket status controls did not become ready at " + browser.getCurrentUrl())
                .until(ExpectedConditions.elementToBeClickable(By.id("status")));
        tabTo(By.id("status"));
        assertVisibleFocus();
        selectFocusedOptionByKeyboard(browser.findElement(By.id("status")), "RESOLVED");
        tabTo(By.id("notes"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys("Provided and tested the required adapter.");
        tabTo(By.cssSelector("form[action$='/status'] button[type='submit']"));
        assertVisibleFocus();
        browser.switchTo().activeElement().sendKeys(org.openqa.selenium.Keys.ENTER);
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Resolved status was not displayed at " + browser.getCurrentUrl())
                .until(driver -> {
                    String page = driver.getPageSource();
                    return page.contains("Ticket status updated.") && page.contains("RESOLVED");
                });

        browser.findElement(By.cssSelector("form.signout button")).click();
        login(employeeUsername, "employee-pass-123");
        browser.get(baseUrl() + "/my-tickets/" + ticketId);
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> "Employee could not reopen ticket progress at " + browser.getCurrentUrl())
                .until(driver -> driver.getPageSource().contains("The room is available after lunch."));
        assertTrue(browser.getPageSource().contains("RESOLVED"));
        assertTrue(!browser.getPageSource().contains("Internal note: confirm the room equipment inventory."),
                "Technician-only internal note must not be visible to the employee");
    }

    private void login(String username, String password) {
        browser.get(baseUrl() + "/login");
        browser.findElement(By.name("username")).sendKeys(username);
        browser.findElement(By.name("password")).sendKeys(password);
        browser.findElement(By.cssSelector("button[type='submit'], form button:not([type])")).click();
        new WebDriverWait(browser, Duration.ofSeconds(10))
                .withMessage(() -> pageDiagnostic("Login did not leave the login page for account " + username))
                .until(driver -> !java.net.URI.create(driver.getCurrentUrl()).getPath().equals("/login"));
        assertTrue(browser.findElement(By.tagName("body")).getText().contains(
                        users.findByUsername(username).orElseThrow().getFullName()),
                pageDiagnostic("Logged-in page did not identify account " + username));
    }

    private String pageDiagnostic(String reason) {
        String title = browser == null ? "<no browser>" : browser.getTitle();
        String url = browser == null ? "<no browser>" : browser.getCurrentUrl();
        String body = browser == null ? "<no browser>" : browser.findElements(By.tagName("body")).stream()
                .findFirst().map(element -> element.getText()).orElse("<no body>");
        return reason + "; URL=" + url + "; title=" + title + "; page=" + body;
    }

    private String baseUrl() { return "http://localhost:" + port; }

    private void assertNoPageOverflow(int width, String page) {
        browser.manage().window().setSize(new Dimension(width, 1000));
        boolean noOverflow = (Boolean) ((JavascriptExecutor) browser).executeScript(
                "return document.documentElement.scrollWidth <= document.documentElement.clientWidth");
        assertTrue(noOverflow, page + " has page-level horizontal overflow at " + width + "px: "
                + ((JavascriptExecutor) browser).executeScript(
                "return 'client=' + document.documentElement.clientWidth + ', scroll=' + document.documentElement.scrollWidth"));
    }

    private void assertVisibleFocus() {
        boolean visible = (Boolean) ((JavascriptExecutor) browser).executeScript(
                "const s=getComputedStyle(document.activeElement); return s.outlineStyle !== 'none' && parseFloat(s.outlineWidth) >= 2");
        assertTrue(visible, "Keyboard-focused login control should have a visible outline");
    }

    private void tabTo(By target) {
        for (int attempt = 0; attempt < 30; attempt++) {
            if (browser.switchTo().activeElement().equals(browser.findElement(target))) return;
            new Actions(browser).sendKeys(org.openqa.selenium.Keys.TAB).perform();
        }
        org.openqa.selenium.WebElement active = browser.switchTo().activeElement();
        org.openqa.selenium.WebElement expected = browser.findElement(target);
        String activeDescription = "tag=" + active.getTagName()
                + ", id=" + active.getAttribute("id")
                + ", name=" + active.getAttribute("name")
                + ", text=" + active.getText();
        assertEquals(expected, active, "Keyboard did not reach locator " + target
                + "; active element: " + activeDescription
                + "; URL=" + browser.getCurrentUrl());
    }

    private void selectFocusedOptionByKeyboard(org.openqa.selenium.WebElement select, String targetValue) {
        String optionText = (String) ((JavascriptExecutor) browser).executeScript(
                "const option=Array.from(arguments[0].options).find(item => item.value === arguments[1]); return option && option.text",
                select, targetValue);
        assertTrue(optionText != null, "Select should contain keyboard target value " + targetValue);
        browser.switchTo().activeElement().sendKeys(optionText);
        assertEquals(targetValue, browser.switchTo().activeElement().getDomProperty("value"),
                "Keyboard selection should choose " + targetValue);
    }

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
