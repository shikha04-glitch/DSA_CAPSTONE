import java.util.*;

// ------------------- Patient ---------------------
class Patient {
    int id;
    String name;
    int age;
    int severity;

    Patient(int id, String name, int age, int severity) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.severity = severity;
    }
}

// ------------------- Doctor ----------------------
class Doctor {
    int id;
    String name;
    String specialization;
    SlotNode scheduleHead;

    Doctor(int id, String name, String specialization) {
        this.id = id;
        this.name = name;
        this.specialization = specialization;
        this.scheduleHead = null;
    }
}

// ------------------- Linked List for Schedule ----
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
}

// ------------------- Circular Queue --------------
class CircularQueue {
    int size;
    int front = -1, rear = -1;
    int[] arr;

    CircularQueue(int size) {
        this.size = size;
        arr = new int[size];
    }

    boolean isFull() {
        return (front == 0 && rear == size - 1) || (rear + 1 == front);
    }

    boolean isEmpty() {
        return front == -1;
    }

    void enqueue(int patientId) {
        if (isFull()) {
            System.out.println("Routine queue full.");
            return;
        }
        if (front == -1) front = 0;
        rear = (rear + 1) % size;
        arr[rear] = patientId;
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
}

// ------------------- Min Heap for Emergency ------
class MinHeap {
    int size = 0;
    int capacity;
    int[][] heap;

    MinHeap(int capacity) {
        this.capacity = capacity;
        heap = new int[capacity][2]; // [patientId, severity]
    }

    void insert(int patientId, int severity) {
        heap[size][0] = patientId;
        heap[size][1] = severity;
        int i = size;
        size++;

        while (i > 0 && heap[i][1] < heap[(i - 1) / 2][1]) {
            int[] temp = heap[i];
            heap[i] = heap[(i - 1) / 2];
            heap[(i - 1) / 2] = temp;
            i = (i - 1) / 2;
        }
    }

    int extractMin() {
        if (size == 0) return -1;

        int patientId = heap[0][0];
        heap[0] = heap[size - 1];
        size--;

        minHeapify(0);
        return patientId;
    }

    void minHeapify(int i) {
        int smallest = i;
        int left = 2 * i + 1, right = 2 * i + 2;

        if (left < size && heap[left][1] < heap[smallest][1]) smallest = left;
        if (right < size && heap[right][1] < heap[smallest][1]) smallest = right;

        if (smallest != i) {
            int[] temp = heap[i];
            heap[i] = heap[smallest];
            heap[smallest] = temp;
            minHeapify(smallest);
        }
    }
}

// ------------------- Hash Table (Chaining) -------
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

    int hash(int id) {
        return id % capacity;
    }

    void insert(Patient p) {
        int idx = hash(p.id);
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
}

// ------------------- Stack for Undo --------------
class UndoStack {
    Stack<String> st = new Stack<>();

    void push(String action) { st.push(action); }
    String pop() { return st.isEmpty() ? "" : st.pop(); }
}

// ------------------- Main System -----------------
class HospitalSystem {
    HashMap<Integer, Doctor> doctors = new HashMap<>();
    HashTablePatients patients = new HashTablePatients(50);
    CircularQueue routineQueue = new CircularQueue(20);
    MinHeap emergency = new MinHeap(20);
    UndoStack undo = new UndoStack();

    void registerPatient(int id, String name, int age, int severity) {
        patients.insert(new Patient(id, name, age, severity));
        undo.push("REG:" + id);
        System.out.println("Patient registered.");
    }

    void addDoctor(int id, String name, String spec) {
        doctors.put(id, new Doctor(id, name, spec));
        System.out.println("Doctor added.");
    }

    void addSlot(int doctorId, int slotId, String start, String end) {
        Doctor d = doctors.get(doctorId);
        SlotNode s = new SlotNode(slotId, start, end);
        s.next = d.scheduleHead;
        d.scheduleHead = s;
        undo.push("SLOTADD:" + doctorId + ":" + slotId);
    }

    void bookRoutine(int patientId) {
        routineQueue.enqueue(patientId);
        undo.push("BOOK:" + patientId);
        System.out.println("Booked in routine queue.");
    }

    void emergencyIn(int patientId, int severity) {
        emergency.insert(patientId, severity);
        undo.push("EMER:" + patientId);
        System.out.println("Emergency added.");
    }

    void serveNext() {
        int patient = -1;

        if (emergency.size > 0)
            patient = emergency.extractMin();
        else if (!routineQueue.isEmpty())
            patient = routineQueue.dequeue();

        if (patient == -1) {
            System.out.println("No patients to serve.");
            return;
        }

        undo.push("SERVE:" + patient);
        System.out.println("Serving patient: " + patient);
    }

    void undoLast() {
        String act = undo.pop();
        if (act.equals("")) {
            System.out.println("Nothing to undo.");
            return;
        }
        System.out.println("Undo action: " + act);
    }
}

// ------------------- MAIN MENU -------------------
public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        HospitalSystem hs = new HospitalSystem();

        while (true) {
            System.out.println("\n1.Register Patient\n2.Add Doctor\n3.Add Slot\n4.Book Routine\n5.Emergency In\n6.Serve Next\n7.Undo\n8.Exit");
            int ch = sc.nextInt();

            if (ch == 1) {
                System.out.print("ID: "); int id = sc.nextInt();
                System.out.print("Name: "); String name = sc.next();
                System.out.print("Age: "); int age = sc.nextInt();
                System.out.print("Severity: "); int s = sc.nextInt();
                hs.registerPatient(id, name, age, s);

            } else if (ch == 2) {
                System.out.print("Doc ID: "); int id = sc.nextInt();
                System.out.print("Name: "); String name = sc.next();
                System.out.print("Spec: "); String spec = sc.next();
                hs.addDoctor(id, name, spec);

            } else if (ch == 3) {
                System.out.print("Doc ID: "); int did = sc.nextInt();
                System.out.print("Slot ID: "); int sid = sc.nextInt();
                System.out.print("Start: "); String st = sc.next();
                System.out.print("End: "); String en = sc.next();
                hs.addSlot(did, sid, st, en);

            } else if (ch == 4) {
                System.out.print("Patient ID: "); int pid = sc.nextInt();
                hs.bookRoutine(pid);

            } else if (ch == 5) {
                System.out.print("Patient ID: "); int pid = sc.nextInt();
                System.out.print("Severity: "); int sev = sc.nextInt();
                hs.emergencyIn(pid, sev);

            } else if (ch == 6) {
                hs.serveNext();

            } else if (ch == 7) {
                hs.undoLast();

            } else break;
        }
    }
}
