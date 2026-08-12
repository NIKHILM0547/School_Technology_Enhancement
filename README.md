# EduAdmin — School Management System (Spring Boot + Thymeleaf)

A single Spring Boot application: server-rendered UI (Thymeleaf), session-based
auth via Spring Security, and JPA/Hibernate for persistence. No separate
frontend — everything runs from one `mvn spring-boot:run`.

## Stack

- **Java 17 + Spring Boot 3.3**
- **Spring MVC** — controllers return Thymeleaf views
- **Thymeleaf** (+ `thymeleaf-extras-springsecurity6`) — server-rendered HTML,
  with `sec:authorize` to show/hide UI by role
- **Spring Security** — session-based form login, role-based URL rules,
  BCrypt password hashing
- **Spring Data JPA + Hibernate** — persistence
- **H2** (file-based) for local dev — zero config; **MySQL** profile
  included for production

## Project structure

```
src/main/java/com/eduadmin/school/
├── SchoolApplication.java
├── config/
│   ├── SecurityConfig.java     # form login, role-based access rules
│   └── DataSeeder.java         # creates demo admin + sample data on startup
├── security/
│   └── AppUserDetailsService.java  # loads User entity for Spring Security
├── model/
│   ├── User.java, Role.java
│   ├── Student.java
│   ├── Attendance.java
│   └── Fee.java                # includes recomputeStatus() for paid/partial/overdue
├── repository/
│   ├── UserRepository.java
│   ├── StudentRepository.java
│   ├── AttendanceRepository.java
│   └── FeeRepository.java
└── controller/
    ├── LoginController.java
    ├── DashboardController.java
    ├── StudentController.java
    ├── AttendanceController.java
    └── FeeController.java

src/main/resources/
├── application.properties          # default: H2 file DB
├── application-mysql.properties    # production profile
├── templates/
│   ├── login.html
│   ├── dashboard.html
│   ├── students.html
│   ├── attendance.html
│   ├── fees.html
│   └── fragments/layout.html   # shared sidebar + head, included via th:replace
└── static/css/styles.css
```

## Local setup

Requires **Java 17+** and **Maven** (or use the included `mvnw` wrapper if you
add one via `mvn -N io.takari:maven:wrapper`).

```bash
mvn spring-boot:run
```

The app starts on **http://localhost:5000**. On first run, `DataSeeder` creates:

- **Email:** admin@school.test
- **Password:** admin123

Log in at http://localhost:5000/login.

The H2 database file is created automatically at `./data/school.mv.db` — no
setup needed. You can browse it directly at http://localhost:5000/h2-console
(JDBC URL: `jdbc:h2:file:./data/school`, user `sa`, blank password).

## Roles & permissions

Users have one of: `admin`, `teacher`, `parent`, `accountant`. Enforced two ways:

1. **URL-level**, in `SecurityConfig.java` — e.g. only `admin`/`accountant`
   can reach `/fees/**` write actions.
2. **View-level**, in templates via `sec:authorize="hasRole('admin')"` — e.g.
   the "Remove student" button only renders for admins.

There's no self-service registration page by design — create additional users
by inserting rows via the H2 console, or add a `/register` admin-only screen
as a next step.

## Switching to MySQL for production

```bash
# 1. Make sure MySQL is running locally (or point at a remote instance)
#    e.g. via Docker: docker run -d -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8

# 2. Set credentials as env vars (defaults to root/root if omitted)
export DB_USERNAME=root
export DB_PASSWORD=root

# 3. Run with the mysql profile active
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

The database `schooldb` is created automatically on first connection
(`createDatabaseIfNotExist=true` in the JDBC URL), and
`spring.jpa.hibernate.ddl-auto=update` auto-creates tables on first run.
For real production use, replace this with **Flyway** migrations so schema
changes are versioned and reviewable — recommended once more than one
developer is touching the database.

## Extending it

Same pattern for every new module (exams, transport, admissions):

1. Add a JPA entity in `model/`
2. Add a `JpaRepository` in `repository/`
3. Add an `@Controller` in `controller/` returning a Thymeleaf view
4. Add the `.html` template in `templates/`, and a link in `fragments/layout.html`

## Notes

- This is a starter, not a hardened production deployment: add proper
  validation messages, pagination for large student lists, CSRF-safe file
  uploads if needed, and audit logging before going live with real student data.
- Parent/SMS notifications aren't wired up yet — a natural next module would
  be a `NotificationService` called from `AttendanceController` and
  `FeeController` when a student is marked absent or a fee becomes overdue.
