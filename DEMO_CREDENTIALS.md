# Demo Login Credentials

All demo accounts log in at **http://localhost:5000/login**.

| Role | Email | Password | Notes |
|---|---|---|---|
| Admin | `admin@school.test` | `admin123` | Full access |
| Teacher | `teacher@school.test` | `teacher123` | Rajesh Kumar - class teacher 6-A |
| Teacher | `meera@school.test` | `teacher123` | Meera Iyer - class teacher 7-B |
| Student | `student@school.test` | `student123` | Aarav Sharma (S1001, 6-A) |
| Student | `diya@school.test` | `student123` | Diya Verma (S1002, 6-A) |
| Student | `kabir@school.test` | `student123` | Kabir Singh (S1003, 7-B) |

Passwords:
- Admin password: `admin123`
- Teacher password: `teacher123`
- Student password: `student123`

---

## Demo data inserted (added to the existing rows)

### Login accounts (new)
- `diya@school.test` / `student123` - linked to student **Diya Verma (S1002, 6-A)**
- `kabir@school.test` / `student123` - linked to student **Kabir Singh (S1003, 7-B)**
- `meera@school.test` / `teacher123` - new teacher Meera Iyer, class teacher of **7-B**

### Reviews (5 new)
- Diya: Algebra practice, Hindi comprehension
- Kabir: Science project, History timeline activity
- Aarav: Decimals chapter

### Student attendance (21 records, 2026-08-03 .. 2026-08-11)
- Mix of `present`, `late`, `absent`, `excused` for Aarav, Diya and Kabir.

### Staff attendance (14 records)
- Rajesh Kumar and Meera Iyer marked present each school day, plus two
  non-present records (excused + late) for Rajesh.

### Payments (8 new)
- Payment history that matches the fees below (fully paid, partial, overdue
  statuses all derive from today's date vs each due date).

### Fees (rebuilt, aligned to the class fee structure)
All student fee records now match the class fee structure (`Fee Structure` tab)
amounts and due dates. Statuses are correct relative to today's date:

| Student | Class | Term 1 2025 | Term 2 2025 | Term 1 2026 | Term 2 2026 | Term 3 2026 |
|---|---|---|---|---|---|---|
| Aarav | 6-A | paid | paid | **partial** (₹4,000/₹9,500) | unpaid | unpaid |
| Diya  | 6-A | overdue | overdue | unpaid | unpaid | unpaid |
| Kabir | 7-B | paid | partial (₹3,000/₹10,000) | unpaid | unpaid | unpaid |
| Tara  | 7-B | — | — | unpaid | unpaid | unpaid |

- Structure amounts: 6-A = ₹9,500 / ₹9,500 / ₹9,000 · 7-B = ₹10,000 each, due
  Aug 15 / Dec 1 / Mar 15.

### Fee structure for all classes (108 rows)
- Every class (`1-A` .. `12-C`) has 3 terms (**Term 1 / 2 / 3 2026**) with amounts
  that scale by grade (₹18,000/yr for Class 1 up to ₹55,000/yr for Class 12).
- Due dates: Term 1 = 2026-08-15, Term 2 = 2026-12-01, Term 3 = 2027-03-15.
- View/edit in **Fees → Fee Structure**; use "Apply structure" to push it to
  students' fee records.
