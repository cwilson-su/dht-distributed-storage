# USER MANUAL

## 1. OVERVIEW AND DOCUMENTATION

This manual guides the user through the project directory structure and the location of key deliverables.

### 1.1. Project Report
The final technical report, which details the architectural design, implementation logic, and experimental results, is located in the `psar_report/` directory.

* **File Name:** `psar_report.pdf`
* **Description:** This is the compiled PDF document generated from the LaTeX source. It contains the complete analysis of the system, including figures and performance metrics.

---

## 2. PROJECT FILE STRUCTURE

The project is organised into distinct directories for source code, documentation, and resources. Below is a breakdown of the folder hierarchy:

### 2.1. Root Directory

* **`src/`**: Contains the Java source code for the DHT implementation. This includes all `.java` files for the Node threads, Message handling, and System orchestration.
* **`psar_report/`**: Contains all LaTeX source files and the final compiled report.
* **`psar_report/figures/`**: Stores the raw diagram files (`.drawio`) and the scripts used to generate the PDFs for the report.

### 2.2. Report Directory (`psar_report/`)

* **`psar_report.tex`**: The main LaTeX file that aggregates all sections.
* **`sections/`**: A subdirectory containing individual `.tex` files for each chapter (e.g., `introduction.tex`, `dht0.tex`, `chord.tex`, etc).
* **`psar.cls`**: The custom LaTeX class file defining the formatting rules.

For a normal user, only the file `psar_report.pdf` would typically be relevant.
---

## 3. EXECUTION INSTRUCTIONS

### Running the DHT System

To execute the distributed system simulation:

1. Navigate to the corresponding `src/` directory.
2. Compile the Java files (e.g., `javac *.java`).
3. Run the main system class (e.g., `java DHTSystem`).

### 3.2. Viewing the Report

Simply open the `psar_report/psar_report.pdf` file with any standard PDF viewer (Adobe Acrobat, Chrome, etc.).
