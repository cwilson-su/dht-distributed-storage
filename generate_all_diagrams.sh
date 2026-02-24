#!/bin/bash

# Configuration
PLANTUML_JAR="/usr/local/bin/plantuml/plantuml.jar"
ROOT_DIAGRAMS_DIR="diagrams"

if [ ! -f "$PLANTUML_JAR" ]; then
    echo "Error: plantuml.jar not found at $PLANTUML_JAR"
    exit 1
fi

echo "Cleaning old diagrams..."
rm -rf "$ROOT_DIAGRAMS_DIR"

echo "Scanning for Java packages..."

find src -name "*.java" -not -path '*/.*' | xargs -n1 dirname | sort -u | while read -r java_dir; do
    
    target_dir="$ROOT_DIAGRAMS_DIR/$java_dir"
    mkdir -p "$target_dir"
    
    package_name=$(basename "$java_dir")
    TEMP_PUML="final_structure.puml"
    
    echo "Processing package: $package_name..."
    
    # Use Python to accurately extract the class structure
    python3 - <<EOF > "$TEMP_PUML"
import os, re

print("@startuml")
print("skinparam classAttributeIconSize 0")
print("set namespaceSeparator none")

for filename in sorted(os.listdir('$java_dir')):
    if filename.endswith('.java') and filename != 'module-info.java':
        classname = filename[:-5]
        print(f"class {classname} {{")
        with open(os.path.join('$java_dir', filename), 'r') as f:
            for line in f:
                line = line.strip()
                # Capture fields (end in ;) and methods (contain '(' and '{')
                # but ignore control flow and logic
                if (';' in line or ('(' in line and '{' in line)) and not any(kw in line for kw in ['package', 'import', 'return', 'if', 'for', 'while', 'System.out', 'this.', 'super', 'new ']):
                    # Clean up the line for UML
                    clean = line.replace('public ', '').replace('private ', '').replace('protected ', '').replace('final ', '').replace('static ', '').split('{')[0].strip()
                    if clean and not clean.startswith('//'):
                        print(f"  {clean.rstrip(';')}")
        print("}")
print("@enduml")
EOF

    # Generate SVG natively
    java -jar "$PLANTUML_JAR" -tsvg -o "$PWD/$target_dir" "$TEMP_PUML"
    
    # Convert to PDF
    if [ -f "$target_dir/final_structure.svg" ]; then
        rsvg-convert -f pdf -o "$target_dir/${package_name}.pdf" "$target_dir/final_structure.svg"
        rm "$target_dir/final_structure.svg"
    fi

    rm "$TEMP_PUML"
done

echo "Success: Check the PDFs in ./$ROOT_DIAGRAMS_DIR"
