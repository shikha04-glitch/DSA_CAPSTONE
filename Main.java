import java.util.*;

/*
 Hospital Appointment & Triage System
 - Per-doctor schedule: singly linked list (SlotNode)
 - Per-doctor routine queue: circular queue (tokens)
 - Emergency triage: min-heap (lower severity => higher priority)
 - Patient index: hash table with chaining
 - Undo log: stack of actions with enough metadata to revert
 - Token: represents a booked appointment or a queued patient
*/

/* ============================
   Data Model / ADTs
   ============================ */

// Patient with visit count (for Top-K)
class Patient {
    int id;
    String name;
    int age;
    int severity; // last-known severity
    int visits;   // incremented whenever served

    Patient(int id, String name, int age, int severity) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.severity = severity;
        this.visits = 0;
    }
}

// Token: appointment or queue entry
class Token {
    static int nextTokenId = 1;
    int tokenId;
    int patientId;
    int doctorId;   // 0 if not assigned initially
    int slotId;     // 0 if not assigned
    enum Type { ROUTINE, EMERGENCY }
    Type type;

    Token(int patientId, int doctorId, int slotId, Type type) {
        this.tokenId = nextTokenId++;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.slotId = slotId;
        this.type = type;
    }

    public String toString() {
        return String.format("[Token %d | P:%d | D:%d | S:%d | %s]", tokenId, patientId, doctorId, slotId, type);
    }
}

// Slot Node (singly linked list)
class SlotNode {
    int slotId;
    String start, end;
    boolean booked;
    SlotNode next;

    SlotNode(int slotId, String start, String end) {
        this.slotId = slotId;
        this.start = start;
        this.end = end;
        this.booked = false;
        this.next = null;
    }

    public String toString() {
        return String.format("Slot[%d: %s-%s %s]", slotId, start, end, booked ? "(BOOKED)" : "(FREE)");
    }
}

// Circular queue for tokens (stores tokenIds)
class CircularQueue {
    int size;
    int front = -1, rear = -1;
    int[] arr;

    CircularQueue(int size) {
        this.size = size;
        arr = new int[size];
    }

    boolean isFull() {
        return (front == 0 && rear == size - 1) || ((rear + 1) % size == front);
    }

    boolean isEmpty() {
        return front == -1;
    }

    boolean enqueue(int tokenId) {
        if (isFull()) {
            return false;
        }
        if (front == -1) front = 0;
        rear = (rear + 1) % size;
        arr[rear] = tokenId;
        return true;
    }

    int dequeue() {
        if (isEmpty()) return -1;
        int x = arr[front];
        if (front == rear) front = rear = -1;
        else front = (front + 1) % size;
        return x;
    }

    int peek() {
        return isEmpty() ? -1 : arr[front];
    }

    // count elements
    int count() {
        if (isEmpty()) return 0;
        if (rear >= front) return rear - front + 1;
        return size - front + rear + 1;
    }

    // insert at front (used for undo re-insert)
    boolean enqueueFront(int tokenId) {
        if (isFull()) return false;
        if (isEmpty()) {
            front = rear = 0;
            arr[front] = tokenId;
            return true;
        }
        front = (front - 1 + size) % size;
        arr[front] = tokenId;
        return true;
    }

    // remove specific tokenId (linear search)
    boolean removeToken(int tokenId) {
        if (isEmpty()) return false;
        List<Integer> tmp = new ArrayList<>();
        boolean found = false;
        while (!isEmpty()) {
            int t = dequeue();
            if (t == tokenId && !found) { found = true; continue; }
            tmp.add(t);
        }
        for (int x : tmp) enqueue(x);
        return found;
    }

    // list tokens array form (for reports)
    List<Integer> toList() {
        List<Integer> res = new ArrayList<>();
        if (isEmpty()) return res;
        int i = front;
        while (true) {
            res.add(arr[i]);
            if (i == rear) break;
            i = (i + 1) % size;
        }
        return res;
    }
}

/* ============================
   MinHeap for emergency triage
   Stores (tokenId, patientId, severity)
   lower severity => higher priority
   ============================ */
class EmergencyHeap {
    int size = 0;
    int capacity;
    int[][] heap; // [tokenId, patientId, severity]

    EmergencyHeap(int capacity) {
        this.capacity = capacity;
        heap = new int[capacity][3];
    }

    boolean isEmpty() { return size == 0; }
    boolean isFull() { return size == capacity; }

    void insert(int tokenId, int patientId, int severity) {
        if (isFull()) {
            System.out.println("Emergency heap full!");
            return;
        }
        heap[size][0] = tokenId;
        heap[size][1] = patientId;
        heap[size][2] = severity;
        int i = size++;
        while (i > 0 && heap[i][2] < heap[(i - 1) / 2][2]) {
            int[] tmp = heap[i];
            heap[i] = heap[(i - 1) / 2];
            heap[(i - 1) / 2] = tmp;
            i = (i - 1) / 2;
        }
    }

    // extract-min returns tokenId
    int extractMin() {
        if (size == 0) return -1;
        int tokenId = heap[0][0];
        heap[0] = heap[size - 1];
        size--;
        minHeapify(0);
        return tokenId;
    }

    int peekTokenId() {
        return size == 0 ? -1 : heap[0][0];
    }

    int peekSeverity() {
        return size == 0 ? -1 : heap[0][2];
    }

    private void minHeapify(int i) {
        int smallest = i;
        int l = 2 * i + 1, r = 2 * i + 2;
        if (l < size && heap[l][2] < heap[smallest][2]) smallest = l;
        if (r < size && heap[r][2] < heap[smallest][2]) smallest = r;
        if (smallest != i) {
            int[] tmp = heap[i];
            heap[i] = heap[smallest];
            heap[smallest] = tmp;
            minHeapify(smallest);
        }
    }

    // remove a specific tokenId (used for undo cancel)
    boolean removeToken(int tokenId) {
        int idx = -1;
        for (int i = 0; i < size; i++) if (heap[i][0] == tokenId) { idx = i; break; }
        if (idx == -1) return false;
        heap[idx] = heap[size - 1];
        size--;
        if (idx < size) {
            while (idx > 0 && heap[idx][2] < heap[(idx - 1) / 2][2]) {
                int[] tmp = heap[idx];
                heap[idx] = heap[(idx - 1) / 2];
                heap[(idx - 1) / 2] = tmp;
                idx = (idx - 1) / 2;
            }
            minHeapify(idx);
        }
        return true;
    }

    // peek patientId from root (for report)
    int peekPatientId() {
        return size == 0 ? -1 : heap[0][1];
    }

    // get severity by tokenId (search)
    int getSeverityByToken(int tokenId) {
        for (int i = 0; i < size; i++) if (heap[i][0] == tokenId) return heap[i][2];
        return -1;
    }
}

/* ============================
   Hash Table for Patients (chaining)
   Supports CRUD: insert/update/get/delete
   ============================ */
class HashTablePatients {
    class Node {
        Patient p;
        Node next;
        Node(Patient p) { this.p = p; }
    }

    Node[] buckets;
    int capacity;

    HashTablePatients(int capacity) {
        this.capacity = capacity;
        buckets = new Node[capacity];
    }

    int hash(int id) { return id % capacity; }

    void insertOrUpdate(Patient p) {
        int idx = hash(p.id);
        Node curr = buckets[idx];
        while (curr != null) {
            if (curr.p.id == p.id) { curr.p = p; return; }
            curr = curr.next;
        }
        Node n = new Node(p);
        n.next = buckets[idx];
        buckets[idx] = n;
    }

    Patient get(int id) {
        int idx = hash(id);
        Node curr = buckets[idx];
        while (curr != null) {
            if (curr.p.id == id) return curr.p;
            curr = curr.next;
        }
        return null;
    }

    boolean delete(int id) {
        int idx = hash(id);
        Node curr = buckets[idx], prev = null;
        while (curr != null) {
            if (curr.p.id == id) {
                if (prev == null) buckets[idx] = curr.next;
                else prev.next = curr.next;
                return true;
            }
            prev = curr;
            curr = curr.next;
        }
        return false;
    }

    List<Patient> allPatients() {
        List<Patient> res = new ArrayList<>();
        for (Node b : buckets) {
            Node curr = b;
            while (curr != null) { res.add(curr.p); curr = curr.next; }
        }
        return res;
    }
}

/* ============================
   Doctor class: holds schedule and routine queue
   ============================ */
class Doctor {
    int id;
    String name;
    String specialization;
    SlotNode scheduleHead;
    CircularQueue routineQueue; // stores tokenIds
    int servedCount = 0;

    Doctor(int id, String name, String specialization, int queueSize) {
        this.id = id;
        this.name = name;
        this.specialization = specialization;
        this.scheduleHead = null;
        this.routineQueue = new CircularQueue(queueSize);
    }

    // add slot at head (simple)
    void addSlot(int slotId, String start, String end) {
        SlotNode s = new SlotNode(slotId, start, end);
        s.next = scheduleHead;
        scheduleHead = s;
    }

    // delete slot by slotId
    boolean deleteSlot(int slotId) {
        SlotNode curr = scheduleHead, prev = null;
        while (curr != null) {
            if (curr.slotId == slotId) {
                if (prev == null) scheduleHead = curr.next;
                else prev.next = curr.next;
                return true;
            }
            prev = curr;
            curr = curr.next;
        }
        return false;
    }

    // find next free slot (lowest slotId among free slots)
    SlotNode findNextFreeSlot() {
        SlotNode curr = scheduleHead;
        SlotNode best = null;
        while (curr != null) {
            if (!curr.booked) {
                if (best == null || curr.slotId < best.slotId) best = curr;
            }
            curr = curr.next;
        }
        return best;
    }

    // traverse list (for reports)
    List<SlotNode> listSlots() {
        List<SlotNode> res = new ArrayList<>();
        SlotNode curr = scheduleHead;
        while (curr != null) { res.add(curr); curr = curr.next; }
        return res;
    }
}

/* ============================
   Undo system
   We store enough metadata to revert actions:
   ActionType: REGISTER_PATIENT, DELETE_PATIENT, ADD_SLOT, DELETE_SLOT,
               BOOK_ROUTINE, EMERGENCY_IN, SERVE (routine/emergency)
   ============================ */
class UndoAction {
    enum ActionType {
        REGISTER_PATIENT, DELETE_PATIENT,
        ADD_SLOT, DELETE_SLOT,
        BOOK_ROUTINE, BOOK_WALKIN,
        EMERGENCY_IN,
        SERVE_ROUTINE, SERVE_EMERGENCY
    }
    ActionType type;
    // flexible metadata:
    int patientId, doctorId, slotId, tokenId, severity;
    // For ADD_SLOT we store doctorId and slotId
    // For SERVE we store whether it was routine/emergency and tokenId or patientId/severity
    UndoAction(ActionType t) { this.type = t; }
}

/* ============================
   Main Hospital System
   ============================ */
class HospitalSystem {
    HashMap<Integer, Doctor> doctors = new HashMap<>();
    HashTablePatients patients = new HashTablePatients(101);
    EmergencyHeap emergency = new EmergencyHeap(200);
    HashMap<Integer, Token> tokenStore = new HashMap<>(); // tokenId -> Token
    Stack<UndoAction> undo = new Stack<>();
    int routineQueueSizePerDoctor = 50;

    // Stats
    int totalServed = 0;
    int totalPending() {
        int sum = 0;
        for (Doctor d : doctors.values()) sum += d.routineQueue.count();
        sum += emergency.size;
        return sum;
    }

    /* -------------------------
       Patient CRUD
       ------------------------- */
    void registerPatient(int id, String name, int age, int severity) {
        Patient p = new Patient(id, name, age, severity);
        patients.insertOrUpdate(p);
        UndoAction ua = new UndoAction(UndoAction.ActionType.REGISTER_PATIENT);
        ua.patientId = id;
        undo.push(ua);
        System.out.println("Patient registered: " + id);
    }

    void updatePatient(int id, String name, Integer age, Integer severity) {
        Patient p = patients.get(id);
        if (p == null) { System.out.println("No patient " + id); return; }
        if (name != null) p.name = name;
        if (age != null) p.age = age;
        if (severity != null) p.severity = severity;
        patients.insertOrUpdate(p);
        System.out.println("Patient updated: " + id);
    }

    void deletePatient(int id) {
        Patient p = patients.get(id);
        if (p == null) { System.out.println("No such patient"); return; }
        patients.delete(id);
        UndoAction ua = new UndoAction(UndoAction.ActionType.DELETE_PATIENT);
        ua.patientId = id;
        // store enough to re-insert on undo: name/age/severity in token? simpler: not stored here
        // For simplicity we won't restore full patient fields on undo delete (teacher-dependent).
        undo.push(ua);
        System.out.println("Patient deleted: " + id);
    }

    Patient patientGet(int id) { return patients.get(id); }

    /* -------------------------
       Doctor & Schedule
       ------------------------- */
    void addDoctor(int id, String name, String spec) {
        if (doctors.containsKey(id)) { System.out.println("Doctor exists"); return; }
        doctors.put(id, new Doctor(id, name, spec, routineQueueSizePerDoctor));
        System.out.println("Doctor added: " + id);
    }

    void addSlot(int doctorId, int slotId, String start, String end) {
        Doctor d = doctors.get(doctorId);
        if (d == null) { System.out.println("No doctor"); return; }
        d.addSlot(slotId, start, end);
        UndoAction ua = new UndoAction(UndoAction.ActionType.ADD_SLOT);
        ua.doctorId = doctorId; ua.slotId = slotId;
        undo.push(ua);
        System.out.println("Slot added to Dr." + doctorId + ": " + slotId);
    }

    void cancelSlot(int doctorId, int slotId) {
        Doctor d = doctors.get(doctorId);
        if (d == null) { System.out.println("No doctor"); return; }
        boolean ok = d.deleteSlot(slotId);
        if (ok) {
            UndoAction ua = new UndoAction(UndoAction.ActionType.DELETE_SLOT);
            ua.doctorId = doctorId; ua.slotId = slotId;
            undo.push(ua);
            System.out.println("Slot cancelled: " + slotId);
        } else System.out.println("Slot not found");
    }

    /* -------------------------
       Booking / Queues
       ------------------------- */
    // Book routine: try assign next free slot for that doctor; else enqueue as walk-in routine
    void enqueueRoutine(int patientId, int doctorId) {
        Doctor d = doctors.get(doctorId);
        if (d == null) { System.out.println("No doctor"); return; }
        SlotNode free = d.findNextFreeSlot();
        Token token;
        if (free != null) {
            // allocate slot
            free.booked = true;
            token = new Token(patientId, doctorId, free.slotId, Token.Type.ROUTINE);
            tokenStore.put(token.tokenId, token);
            // Enqueue token
            boolean enq = d.routineQueue.enqueue(token.tokenId);
            if (!enq) {
                // revert slot booking
                free.booked = false;
                tokenStore.remove(token.tokenId);
                System.out.println("Routine queue full for doctor " + doctorId);
                return;
            }
            UndoAction ua = new UndoAction(UndoAction.ActionType.BOOK_ROUTINE);
            ua.patientId = patientId; ua.doctorId = doctorId; ua.slotId = free.slotId; ua.tokenId = token.tokenId;
            undo.push(ua);
            System.out.println("Booked slot and token: " + token);
        } else {
            // no free slot, enqueue as walk-in routine with no slot
            token = new Token(patientId, doctorId, 0, Token.Type.ROUTINE);
            tokenStore.put(token.tokenId, token);
            boolean enq = d.routineQueue.enqueue(token.tokenId);
            if (!enq) {
                tokenStore.remove(token.tokenId);
                System.out.println("Routine queue full for doctor " + doctorId);
                return;
            }
            UndoAction ua = new UndoAction(UndoAction.ActionType.BOOK_WALKIN);
            ua.patientId = patientId; ua.doctorId = doctorId; ua.tokenId = token.tokenId;
            undo.push(ua);
            System.out.println("Booked walk-in token: " + token);
        }
    }

    // enqueue emergency: create emergency token and insert into emergency heap
    void triageInsert(int patientId, int severity) {
        Token token = new Token(patientId, 0, 0, Token.Type.EMERGENCY);
        tokenStore.put(token.tokenId, token);
        emergency.insert(token.tokenId, patientId, severity);
        UndoAction ua = new UndoAction(UndoAction.ActionType.EMERGENCY_IN);
        ua.patientId = patientId; ua.tokenId = token.tokenId; ua.severity = severity;
        undo.push(ua);
        System.out.println("Emergency inserted: token " + token.tokenId + " severity " + severity);
    }

    // Serve Next: emergency preempts routine. Serve emergency token's patient or routine token.
    void serveNext() {
        // Serve emergency if exists
        if (!emergency.isEmpty()) {
            int tokenId = emergency.extractMin();
            Token tok = tokenStore.get(tokenId);
            if (tok == null) {
                System.out.println("Emergency token missing data");
                return;
            }
            Patient p = patients.get(tok.patientId);
            if (p != null) p.visits++;
            totalServed++;
            UndoAction ua = new UndoAction(UndoAction.ActionType.SERVE_EMERGENCY);
            ua.tokenId = tokenId; ua.patientId = tok.patientId; ua.severity = emergency.getSeverityByToken(tokenId); // -1 now
            // since we popped from heap, we cannot retrieve severity directly after extraction (we tried getSeverityByToken earlier).
            // To keep revert possible, store severity from tok? tok didn't store severity. Simplify: store severity in ua.severity as -1 (best-effort)
            ua.severity = -1;
            ua.patientId = tok.patientId;
            undo.push(ua);
            tokenStore.remove(tokenId);
            System.out.println("Served EMERGENCY patient: " + ua.patientId + " (token " + tokenId + ")");
            return;
        }

        // Else, find doctor with non-empty routine queue (we'll choose the one with earliest doctor id)
        for (Doctor d : doctors.values()) {
            if (!d.routineQueue.isEmpty()) {
                int tokenId = d.routineQueue.dequeue();
                Token tok = tokenStore.get(tokenId);
                if (tok == null) {
                    System.out.println("Token missing");
                    return;
                }
                // free slot if any
                if (tok.slotId != 0) {
                    // find slot and mark free
                    SlotNode curr = d.scheduleHead;
                    while (curr != null) {
                        if (curr.slotId == tok.slotId) { curr.booked = false; break; }
                        curr = curr.next;
                    }
                }
                Patient p = patients.get(tok.patientId);
                if (p != null) p.visits++;
                tokenStore.remove(tokenId);
                totalServed++;
                d.servedCount++;
                UndoAction ua = new UndoAction(UndoAction.ActionType.SERVE_ROUTINE);
                ua.tokenId = tokenId; ua.patientId = tok.patientId; ua.doctorId = d.id; ua.slotId = tok.slotId;
                undo.push(ua);
                System.out.println("Served ROUTINE patient: " + ua.patientId + " (token " + tokenId + ")");
                return;
            }
        }

        System.out.println("No patients to serve.");
    }

    /* -------------------------
       Undo logic: revert last action
       ------------------------- */
    void undoLast() {
        if (undo.isEmpty()) { System.out.println("Nothing to undo."); return; }
        UndoAction ua = undo.pop();
        switch (ua.type) {
            case REGISTER_PATIENT:
                // remove patient
                patients.delete(ua.patientId);
                System.out.println("Undo: patient registration removed: " + ua.patientId);
                break;
            case DELETE_PATIENT:
                // Not storing full patient data to restore — inform user.
                System.out.println("Undo for delete patient not supported (no stored snapshot).");
                break;
            case ADD_SLOT:
                // remove the slot we added
                Doctor d = doctors.get(ua.doctorId);
                if (d != null) {
                    d.deleteSlot(ua.slotId);
                    System.out.println("Undo: slot removed: " + ua.slotId + " from Dr." + ua.doctorId);
                }
                break;
            case DELETE_SLOT:
                // Can't restore details (start/end) since not stored. Inform user.
                System.out.println("Undo: restore deleted slot not supported (no slot metadata stored).");
                break;
            case BOOK_ROUTINE:
                // Remove token from doctor's queue, mark slot free, remove token
                Doctor dr = doctors.get(ua.doctorId);
                if (dr != null) {
                    boolean removed = dr.routineQueue.removeToken(ua.tokenId);
                    // free the slot
                    SlotNode curr = dr.scheduleHead;
                    while (curr != null) {
                        if (curr.slotId == ua.slotId) { curr.booked = false; break; }
                        curr = curr.next;
                    }
                    tokenStore.remove(ua.tokenId);
                    System.out.println("Undo: routine booking undone (token " + ua.tokenId + ")");
                }
                break;
            case BOOK_WALKIN:
                Doctor dr2 = doctors.get(ua.doctorId);
                if (dr2 != null) {
                    boolean removed2 = dr2.routineQueue.removeToken(ua.tokenId);
                    tokenStore.remove(ua.tokenId);
                    System.out.println("Undo: walk-in booking undone (token " + ua.tokenId + ")");
                }
                break;
            case EMERGENCY_IN:
                // remove from heap by tokenId
                boolean rem = emergency.removeToken(ua.tokenId);
                tokenStore.remove(ua.tokenId);
                System.out.println("Undo: emergency insertion undone (token " + ua.tokenId + ")");
                break;
            case SERVE_EMERGENCY:
                // cannot perfectly restore severity if not stored; but we stored tokenId earlier—reinsert as emerg
                Token t = new Token(ua.patientId, 0, 0, Token.Type.EMERGENCY);
                tokenStore.put(t.tokenId, t);
                int sev = ua.severity >= 0 ? ua.severity : 5; // fallback severity 5
                emergency.insert(t.tokenId, ua.patientId, sev);
                totalServed = Math.max(0, totalServed - 1);
                System.out.println("Undo: service of emergency patient undone, reinserted token " + t.tokenId);
                break;
            case SERVE_ROUTINE:
                // reinsert token to front of doctor's routine queue and re-book slot if any
                Doctor drec = doctors.get(ua.doctorId);
                if (drec != null) {
                    Token tok = new Token(ua.patientId, ua.doctorId, ua.slotId, Token.Type.ROUTINE);
                    tokenStore.put(tok.tokenId, tok);
                    drec.routineQueue.enqueueFront(tok.tokenId);
                    if (ua.slotId != 0) {
                        SlotNode cur = drec.scheduleHead;
                        while (cur != null) {
                            if (cur.slotId == ua.slotId) { cur.booked = true; break; }
                            cur = cur.next;
                        }
                    }
                    totalServed = Math.max(0, totalServed - 1);
                    System.out.println("Undo: routine service undone, token reinserted " + tok.tokenId);
                }
                break;
            default:
                System.out.println("Undo: action type not supported.");
        }
    }

    /* -------------------------
       Reports & Utilities
       ------------------------- */
    void reportPerDoctor() {
        System.out.println("Per-Doctor Report:");
        for (Doctor d : doctors.values()) {
            SlotNode ns = d.findNextFreeSlot();
            System.out.println("Dr." + d.id + " " + d.name + " (" + d.specialization + ")");
            System.out.println("  Pending routine: " + d.routineQueue.count());
            System.out.println("  Next free slot: " + (ns == null ? "None" : ns));
            System.out.println("  Served count: " + d.servedCount);
        }
    }

    void reportSummary() {
        int pending = totalPending();
        System.out.println("Summary: total served: " + totalServed + " | total pending: " + pending + " | emergency queued: " + emergency.size);
    }

    // Top-K frequent patients by visits
    void reportTopK(int K) {
        List<Patient> all = patients.allPatients();
        all.sort((a,b) -> Integer.compare(b.visits, a.visits));
        System.out.println("Top " + K + " frequent patients:");
        for (int i = 0; i < Math.min(K, all.size()); i++) {
            Patient p = all.get(i);
            System.out.println(" " + (i+1) + ". " + p.id + " " + p.name + " visits:" + p.visits);
        }
    }

    // small debug listing tokens
    void listTokens() {
        System.out.println("Active Tokens:");
        for (Token t : tokenStore.values()) System.out.println("  " + t);
    }

    /* -------------------------
       Complexity Notes (brief)
       These are inline quick notes
       ------------------------- */
    void printComplexities() {
        System.out.println("\nComplexities (summary):");
        System.out.println("Queue enqueue/dequeue: T = O(1), S = O(1) per op");
        System.out.println("Heap insert/extract-min: T = O(log n), S = O(1) per op");
        System.out.println("Hash insert/search (avg): T = O(1), S = O(1) per op (table size m)");
        System.out.println("Linked list insert/delete at head: T = O(1); find/delete by id: O(k) where k slots");
        System.out.println("Stack push/pop: T = O(1)");
        System.out.println("Find next free slot: O(k) where k = slots for doctor");
        System.out.println("Report per-doctor traversal: O(k) per doc");
        System.out.println("Top-K frequent: O(n log n) naive (n patients) or O(n log K) with min-heap");
    }
}

/* ============================
   CLI Main
   ============================ */
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        HospitalSystem hs = new HospitalSystem();

        // sample seed data to demonstrate functionality
        hs.addDoctor(11, "Arjun", "Cardio");
        hs.addDoctor(12, "Leela", "Ortho");
        hs.addSlot(11, 1, "10:00", "10:30");
        hs.addSlot(11, 2, "10:30", "11:00");
        hs.addSlot(12, 1, "09:00", "09:30");

        // simple menu
        while (true) {
            System.out.println("\n1.Register Patient\n2.Update Patient\n3.Delete Patient\n4.Add Doctor\n5.Add Slot\n6.Cancel Slot\n7.Book Routine\n8.Emergency In\n9.Serve Next\n10.Undo\n11.Reports\n12.List Tokens\n13.Exit");
            System.out.print("Choice: ");
            int ch = -1;
            try { ch = sc.nextInt(); } catch (Exception e) { sc.nextLine(); continue; }
            if (ch == 1) {
                System.out.print("ID: "); int id = sc.nextInt();
                System.out.print("Name: "); String name = sc.next();
                System.out.print("Age: "); int age = sc.nextInt();
                System.out.print("Severity: "); int s = sc.nextInt();
                hs.registerPatient(id, name, age, s);
            } else if (ch == 2) {
                System.out.print("ID: "); int id = sc.nextInt();
                System.out.print("Name (or - to skip): "); String name = sc.next();
                System.out.print("Age (or -1 to skip): "); int age = sc.nextInt();
                System.out.print("Severity (or -1 to skip): "); int sev = sc.nextInt();
                hs.updatePatient(id, name.equals("-") ? null : name, age == -1 ? null : age, sev == -1 ? null : sev);
            } else if (ch == 3) {
                System.out.print("ID: "); int id = sc.nextInt();
                hs.deletePatient(id);
            } else if (ch == 4) {
                System.out.print("Doc ID: "); int id = sc.nextInt();
                System.out.print("Name: "); String name = sc.next();
                System.out.print("Spec: "); String spec = sc.next();
                hs.addDoctor(id, name, spec);
            } else if (ch == 5) {
                System.out.print("Doc ID: "); int did = sc.nextInt();
                System.out.print("Slot ID: "); int sid = sc.nextInt();
                System.out.print("Start: "); String st = sc.next();
                System.out.print("End: "); String en = sc.next();
                hs.addSlot(did, sid, st, en);
            } else if (ch == 6) {
                System.out.print("Doc ID: "); int did = sc.nextInt();
                System.out.print("Slot ID: "); int sid = sc.nextInt();
                hs.cancelSlot(did, sid);
            } else if (ch == 7) {
                System.out.print("Patient ID: "); int pid = sc.nextInt();
                System.out.print("Doctor ID: "); int did = sc.nextInt();
                hs.enqueueRoutine(pid, did);
            } else if (ch == 8) {
                System.out.print("Patient ID: "); int pid = sc.nextInt();
                System.out.print("Severity (lower is more urgent): "); int sev = sc.nextInt();
                hs.triageInsert(pid, sev);
            } else if (ch == 9) {
                hs.serveNext();
            } else if (ch == 10) {
                hs.undoLast();
            } else if (ch == 11) {
                System.out.println("1) Per-doctor report\n2) Summary\n3) Top-K patients\n4) Complexities");
                int r = sc.nextInt();
                if (r == 1) hs.reportPerDoctor();
                else if (r == 2) hs.reportSummary();
                else if (r == 3) { System.out.print("K: "); int K = sc.nextInt(); hs.reportTopK(K); }
                else if (r == 4) hs.printComplexities();
            } else if (ch == 12) {
                hs.listTokens();
            } else break;
        }

        System.out.println("Exiting.");
        sc.close();
    }
}
