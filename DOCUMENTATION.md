# EduAdmin - School Management System

A single Spring Boot application for school administration: server-rendered UI
(Thymeleaf), session-based authentication (Spring Security), and JPA/Hibernate
persistence. There is no separate frontend - everything runs from one
`mvn spring-boot:run`.

## Table of contents

1. [Tech stack](#tech-stack)
2. [Features](#features)
3. [Project structure](#project-structure)
4. [Local setup](#local-setup)
5. [Demo accounts](#demo-accounts)
6. [Database configuration](#database-configuration)
7. [Data model](#data-model)
8. [Roles & permissions](#roles--permissions)
9. [Module walkthrough](#module-walkthrough)
10. [HTTP endpoints](#http-endpoints)
11. [How to extend](#how-to-extend)
12. [Known limitations](#known-limitations)

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3 |
| Web layer | Spring MVC (controllers return Thymeleaf views) |
| Templating | Thymeleaf + `thymeleaf-extras-springsecurity6` (for `sec:authorize`) |
| Security | Spring Security - session form login, role-based URL rules, BCrypt |
| Persistence | Spring Data JPA + Hibernate (`ddl-auto=update`) |
| Database | MySQL (default) or H2 (dev profile) |
| Build | Maven (`pom.xml`) |

---

## Features

- **Dashboard** - today's attendance summary (present/absent/late/excused),
  attendance rate, staff present count, list of absent people today, fee
  collections (total collected, outstanding, overdue count, pending list).
- **Attendance** - student **and** staff attendance:
  - Mark daily student attendance per class (`/attendance/rollcall`).
  - Mark staff (teacher) attendance (`/attendance/rollcall-staff`).
  - View/filter/search the day's combined register; edit individual marks
    inline with optional remarks.
  - Apply for leave (students & teachers) with admin approval/rejection.
- **Fees** - create fee records (term, amount due, amount paid, due date),
  record partial payments, auto status (`unpaid` / `partial` / `paid` /
  `overdue`), filter by student / class / name.
- **Fee structure** - admin defines a per-class fee template (term, amount due,
  due date) and applies it to create/update fee records for all students in a
  class; the structure also shows on each student's fees page.
- **Users** - admin manages teacher & student accounts: create (with admission
  number), edit profile, delete. Creating a student user auto-creates the
  linked `Student` record; admission numbers are auto-generated (`S1004`, ...)
  if not provided.
- **Reviews & Discussion** - students post reviews / difficulties; everyone can
  read the board. Posts are attributed to the logged-in student via the
  `Student <-> User` link and show name, admission number and timestamp.

---

## Project structure

```
src/main/java/com/eduadmin/school/
|-- SchoolApplication.java              # entry point
|-- config/
|   |-- SecurityConfig.java             # form login, role-based URL rules, BCrypt
|   `-- DataSeeder.java                 # demo users + sample students/fees on startup
|-- security/
|   `-- AppUserDetailsService.java      # loads the User entity for Spring Security
|-- service/
|   |-- SmsService.java                 # interface (currently logs to console)
|   `-- SmsServiceImpl.java             # stub - replace with a real SMS gateway
|-- model/
|   |-- Role.java                       # enum: admin, teacher, student
|   |-- User.java                       # login account (has @Transient admissionNo)
|   |-- Student.java                    # admission record, links to a User
|   |-- Attendance.java                 # student attendance per day
|   |-- StaffAttendance.java            # staff attendance per day
|   |-- AttendanceStatus.java           # enum: present, absent, late, excused
|   |-- LeaveRequest.java               # student/teacher leave applications
|   |-- LeaveStatus.java                # enum: pending, approved, rejected
|   |-- Note.java                       # note metadata + target classes
|   |-- NoteFile.java                   # note bytes stored in DB (LONGBLOB)
|   |-- Fee.java                        # includes recomputeStatus()
|   |-- FeeStructure.java               # per-class fee template
|   `-- Review.java                     # student reviews / difficulties
|-- repository/                         # Spring Data repositories
|   |-- UserRepository.java
|   |-- StudentRepository.java
|   |-- AttendanceRepository.java
|   |-- StaffAttendanceRepository.java
|   |-- LeaveRequestRepository.java
|   |-- NoteRepository.java
|   |-- NoteFileRepository.java
|   |-- FeeRepository.java
|   |-- FeeStructureRepository.java
|   `-- ReviewRepository.java
`-- controller/
    |-- LoginController.java            # GET /login
    |-- StudentsRedirectController.java # GET /students -> redirect /users
    |-- DashboardController.java        # GET /
    |-- AttendanceController.java       # /attendance/**
    |-- LeaveController.java            # /attendance/leave/**
    |-- NoteController.java             # /notes/**
    |-- FeeController.java              # /fees/**
    |-- FeeStructureController.java     # /fees/structure/**
    |-- UserController.java             # /users/**
    `-- ReviewController.java           # /reviews/**

src/main/resources/
|-- application.properties              # MySQL default + H2 profile flag
|-- templates/
|   |-- login.html
|   |-- dashboard.html
|   |-- attendance.html                 # register view + filters + inline edit
|   |-- attendance-rollcall.html        # mark student attendance
|   |-- attendance-staff.html           # mark staff attendance
|   |-- leave.html                      # apply for leave + review requests
|   |-- fees.html
|   |-- fee-structure.html              # per-class fee templates + apply
|   |-- users.html                      # manage teacher/student accounts
|   |-- reviews.html                    # discussion board
|   `-- fragments/layout.html           # shared head + sidebar (th:replace)
`-- static/css/styles.css               # all styling (CSS variables + classes)
```

---

## Local setup

Requires **Java 17+** and **Maven**.

```bash
mvn spring-boot:run
```

The app starts on **http://localhost:5000**. Log in at
http://localhost:5000/login.

On first start, `DataSeeder` creates demo users and, if the students table is
empty, three sample students plus a fee record each (see below).

---

## Demo accounts

| Role | Email | Password | Notes |
|---|---|---|---|
| Admin | `admin@school.test` | `admin123` | Full access: everything |
| Teacher | `teacher@school.test` | `teacher123` | Attendance + dashboard |
| Student | `student@school.test` | `student123` | Linked to student S1001 (Aarav Sharma); can post reviews |

`DataSeeder` also links the demo student account to student **S1001** so that
account can post reviews out of the box.

---

## Database configuration

### Default: MySQL

`application.properties` points at a local MySQL by default:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/schooldb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:root}
```

- The database `schooldb` is created automatically on first connection
  (`createDatabaseIfNotExist=true`).
- Credentials default to `root`/`root`; override with env vars
  `DB_USERNAME` and `DB_PASSWORD`.
- `spring.jpa.hibernate.ddl-auto=update` auto-creates/updates tables on
  startup.

### H2 (dev only)

H2 is on the classpath and the H2 console is supported, but it is **disabled by
default** and no H2 profile file ships yet. To use it for local development,
add an `application-h2.properties` with:

```properties
spring.datasource.url=jdbc:h2:file:./data/school
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true
```

then run with `mvn spring-boot:run -Dspring-boot.run.profiles=h2`.

---

## Data model

Tables (auto-created by Hibernate):

| Table | Purpose | Key relationships |
|---|---|---|
| `users` | Login accounts (admin/teacher/student) | `Student.user_id` references this |
| `students` | Admission records | `@OneToOne` to `users`; owns `attendance` and `fees` collections |
| `attendance` | Per-student-per-day attendance | `ManyToOne` -> `students` |
| `staff_attendance` | Per-teacher-per-day attendance | `ManyToOne` -> `users` |
| `leave_requests` | Student/teacher leave applications | `ManyToOne` -> `users` (applicant + reviewer), `ManyToOne` -> `students` |
| `note_files` | Raw bytes of each note (`LONGBLOB`) | `@MapsId` `@OneToOne` -> `notes` (PK = `note_id`) |
| `fee_structures` | Per-class fee templates (class + term) | unique (`class_name`, `term`) |
| `fees` | Fee records per student | `ManyToOne` -> `students` |
| `reviews` | Student reviews/difficulties | `ManyToOne` -> `students` |

Important details:

- **Enums are stored as strings** (`@Enumerated(EnumType.STRING)`): `Role`
  (admin/teacher/student), `AttendanceStatus`
  (present/absent/late/excused), `LeaveStatus`
  (pending/approved/rejected), `Fee.Status`
  (unpaid/partial/paid/overdue).
- **`User.admissionNo` is `@Transient`** - the admission number lives on the
  linked `Student` row and is copied onto the `User` DTO in
  `UserController.list()` for display.
- **Fee status is derived**, not stored as truth: `Fee.recomputeStatus()`
  sets `paid` when `amountPaid >= amountDue`, `partial` when partially paid,
  `overdue` when nothing paid and past `dueDate`, else `unpaid`.

---

## Roles & permissions

Roles are `admin`, `teacher`, `student`. Enforced two ways:

1. **URL-level** in `SecurityConfig.java`:

   - `permitAll`: `/login`, `/css/**`, `/js/**`, `/images/**`, `/h2-console/**`
   - `hasRole("admin")`: `/fees/**`, `/users/**`, `/attendance/leave/{id}/review`
   - `hasAnyRole("admin","teacher")`: `/attendance/**` (minus leave apply/review)
   - `hasAnyRole("admin","teacher","student")`: `/attendance/leave/apply`
   - `authenticated` (everything else): `/reviews`, `/`

2. **View-level** in templates via `sec:authorize="hasRole('admin')"` - e.g.
   only admins see the "Users" and "Fees" sidebar links; the review **post
   form** only renders for students (`th:if="${isStudent}"`).

Server-side the review POST handler also re-checks the role and the student
link, so a user cannot post unless the DB has a `Student` row linked to their
account.

---

## Module walkthrough

### Dashboard (`/`)

Shown to every role. Reads today's data:

- Student attendance counts (present/absent/late/excused) and attendance rate
  (present / marked that day).
- Staff present count.
- Absent list for today (students + staff).
- Fee figures: total collected, total outstanding, overdue count, top 5
  pending fees.

### Attendance (`/attendance`, admin & teacher)

- **Register view** (`GET /attendance`): one combined list of all students
  plus staff (teachers) for the selected date. Filters: date, status, class,
  name. Shows the actual saved status (defaults to "present" when no record
  exists) and lets you edit status + remarks inline via `POST /attendance/{id}/edit`.
- **Mark student attendance** (`GET /attendance/rollcall`): pick a date, set a
  status for each student, save via `POST /attendance/save`. Existing marks
  are pre-filled; unsaved students are skipped by the save loop.
- **Mark staff attendance** (`GET /attendance/rollcall-staff`): same flow for
  teachers via `POST /attendance/save-staff`.
- Records are upserted: one row per student/date and staff/date.

### Leave requests (`/attendance/leave`, all roles)

- Students and teachers apply for leave via a form (from date, to date,
  reason); students are matched to their linked `Student` record so the class
  shows in the admin list.
- Admin sees all requests (with status filter) and can approve or reject each
  one, optionally leaving a review note (`POST /attendance/leave/{id}/review`).
- Students/teachers only see their own requests. Pending requests cannot be
  re-reviewed once decided; the reviewer name + comment are shown after review.

### Fees (`/fees`, admin only)

- List with filters: student dropdown, class, name search (custom JPQL in
  `FeeRepository.search`).
- Create: student, term, amount due, optional amount paid, optional due date.
  Requires `amountDue > 0`.
- Pay: `POST /fees/{id}/pay` with an amount adds to `amountPaid`, then
  `recomputeStatus()`.

### Fee structure (`/fees/structure`, admin only)

- Admin defines fee templates per class: class + term + amount due + due date
  (`fee_structures` table, unique on class + term). Add / inline-edit / delete.
- "Apply structure" upserts a fee record for every student in the class, matched
  by term: existing records keep their `amountPaid` and refresh amount/due date,
  new ones are created as `unpaid`. Deleting a structure row does not remove
  already-applied fee records.
- Students see their class's fee structure as a reference on their fees page.

### Users (`/users`, admin only)

- Create teacher or student accounts. For students, `admissionNo` and class
  are captured; a linked `Student` row is auto-created. Admission numbers are
  auto-generated as `S<n+1>` when blank.
- Edit name/email/password (optional)/subject/classes/mobile/class-teacher-of.
- Delete a user account.
- Validation: unique email, valid email format, mobile matching
  `^[6-9]\d{9}$`, duplicate admission number check.
- On create, an SMS message is "sent" via `SmsService` (currently logs to the
  console).

### Reviews & Discussion (`/reviews`)

- `GET /reviews`: newest-first list of all posts. If the logged-in user is a
  student with a linked `Student` record, a post form is shown and the form
  header displays the student's name + admission number.
- `POST /reviews/new`: only allowed for a student whose account is linked to a
  `Student`. Title and content are required (trimmed, blanks rejected).

### Notes & Material (`/notes`)

- Teachers and admins upload study material (video, image, or PDF) and pick
  which classes it should be visible to (e.g. `6-A`, `7-B`).
- Students only see notes shared with their own class (matched on
  `Student.getClassDisplay()`, e.g. `6-A`); the note list and file download are
  restricted accordingly.
- Files are stored **in the database**, not on disk: the `note_files` table
  holds the raw bytes as a `LONGBLOB`, keyed 1:1 by `note_id`
  (`@MapsId`/`@OneToOne`, lazily loaded so the notes list doesn't pull blobs).
  The `notes` table stores the metadata and `note_target_classes` stores the
  selected classes.
- Only the uploader or an admin can delete a note; deleting a note removes its
  blob row too.
- `spring.servlet.multipart.max-file-size` (100 MB) controls the upload limit.

---

## HTTP endpoints

| Method | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/login` | public | Login page |
| POST | `/login` | public | Spring Security form login |
| POST | `/logout` | authenticated | Log out |
| GET | `/` | authenticated | Dashboard |
| GET | `/students` | authenticated | Redirects to `/users` |
| GET | `/attendance` | admin, teacher | Register view with filters |
| GET | `/attendance/rollcall` | admin, teacher | Mark student attendance |
| POST | `/attendance/save` | admin, teacher | Save student attendance |
| GET | `/attendance/rollcall-staff` | admin, teacher | Mark staff attendance |
| POST | `/attendance/save-staff` | admin, teacher | Save staff attendance |
| POST | `/attendance/{id}/edit` | admin, teacher | Edit status/remarks |
| GET | `/attendance/leave` | admin, teacher, student | Leave requests (own or all) |
| POST | `/attendance/leave/apply` | admin, teacher, student | Submit a leave request |
| POST | `/attendance/leave/{id}/review` | admin | Approve/reject a request |
| GET | `/fees` | admin | Fee list with filters |
| POST | `/fees/new` | admin | Create a fee record |
| POST | `/fees/{id}/pay` | admin | Record a payment |
| GET | `/fees/structure` | admin | Fee structure per class |
| POST | `/fees/structure/new` | admin | Add a structure row |
| POST | `/fees/structure/{id}/edit` | admin | Update amount/due date |
| POST | `/fees/structure/{id}/delete` | admin | Delete a structure row |
| POST | `/fees/structure/apply` | admin | Apply structure to class students |
| GET | `/users` | admin | User list with filters |
| POST | `/users/new` | admin | Create teacher/student |
| POST | `/users/{id}/edit` | admin | Update a user |
| POST | `/users/{id}/delete` | admin | Delete a user |
| GET | `/reviews` | authenticated | Discussion board |
| POST | `/reviews/new` | student | Create a review post |
| GET | `/notes` | authenticated | Notes list (students see only their class) |
| POST | `/notes/upload` | teacher, admin | Upload a note for selected classes |
| GET | `/notes/{id}/download` | authenticated | Stream the file (class-checked) |
| POST | `/notes/{id}/delete` | uploader, admin | Delete a note |

---

## How to extend

Same pattern for every new module (exams, transport, admissions):

1. Add a JPA entity in `model/`.
2. Add a `JpaRepository` in `repository/` (add derived queries or a `@Query`
   for custom filtering, as in `FeeRepository.search`).
3. Add an `@Controller` in `controller/` returning a Thymeleaf view.
4. Add the `.html` template in `templates/` and a sidebar link in
   `fragments/layout.html` (wrap with `sec:authorize` for role-gated links).
5. Add any URL rule to `SecurityConfig.java`.
6. Compile and verify: `mvn compile` (or restart the running app - devtools
   hot-reloads templates and recompiles changed classes).

---

## Known limitations

- **No migrations yet** - `ddl-auto=update` is convenient for development but
  not versioned. Swap to Flyway/Liquibase before multiple developers (or a
  real deployment) touch the schema.
- **SMS is a stub** - `SmsServiceImpl` only prints to the console. Wire a real
  provider (Twilio, MSG91, etc.) behind the `SmsService` interface.
- **No pagination** - attendance/users lists load everything. Add Spring Data
  `Pageable` for large datasets.
- **Student self-registration / parent accounts** are not implemented - users
  are created by the admin on the Users tab only.
- **Permissions are coarse** - e.g. any admin or teacher can edit any
  attendance row; there is no per-class scoping for teachers yet.
- **Review posts cannot be edited or deleted** once created.
- **H2 profile is not pre-configured** - see
  [Database configuration](#database-configuration) to enable it.

