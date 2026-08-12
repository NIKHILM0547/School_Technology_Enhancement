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

### Fees (7 new records, all four statuses represented)
| Term | Status | Student |
|---|---|---|
| Term 2 2026 | unpaid (pending) | Aarav, Diya, Kabir |
| Term 1 2025 | overdue | Diya, Kabir |
| Term 2 2025 | partial | Aarav, Kabir |

### Payments (5 new)
- Payment history for the partial Term 2 2025 fees and the existing paid fee.
