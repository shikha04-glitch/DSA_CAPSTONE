# 🏥 Hospital Appointment & Triage System
### Data Structures Capstone Project • Console Application

This project simulates an Outpatient Department (OPD) appointment system using multiple data structures.  
It includes patient registration, doctor slot management, routine queueing, emergency triage, undo operations, and activity reporting.

---

## 🚀 Features

- **Patient Registration** using Hash Table  
- **Doctor Schedules** using Singly Linked Lists  
- **Routine Appointments** using Circular Queue  
- **Emergency Triage** using Min-Heap (Priority Queue)  
- **Undo Operations** using Stack  
- **Reports** for pending patients and next slots  

---

## 🧱 Data Structures Used

| Feature | Data Structure | Reason |
|--------|----------------|--------|
| Patient Records | Hash Table | O(1) average lookup |
| Doctor Slots | Singly Linked List | Dynamic schedule |
| Routine Queue | Circular Queue | O(1) enqueue/dequeue |
| Emergency Queue | Min Heap | Priority-based service |
| Undo System | Stack | Reversing last change |
| Logs | List/Stack | Audit trail |

---

## 📌 Menu Options

1.Register Patient
2.Update Patient
3.Delete Patient
4.Add Doctor
5.Add Slot
6.Cancel Slot
7.Book Routine
8.Emergency In
9.Serve Next
10.Undo
11.Reports
12.List Tokens
13.Exit


---

## 📘 Notes

- Severity: Lower score = more critical
- Undo works for: booking, serving, emergency additions
- Triage always overrides routine queue
- Each doctor maintains an independent schedule list

---


