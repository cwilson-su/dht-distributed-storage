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
    TEMP_PUML="clean_structure.puml"
    
    echo "Generating PDF for package: $package_name..."
    
    # Use Python to precisely extract class headers and member signatures
    python3 - <<EOF > "$TEMP_PUML"
import os, re

print("@startuml")
print("skinparam classAttributeIconSize 0")
print("set namespaceSeparator none")

# Patterns to match class/interface and member signatures
class_pattern = re.compile(r'(class|interface|enum)\s+(\w+)')
member_pattern = re.compile(r'^\s*(public|private|protected|static|final|\w+)\s+[\w<>, ]+\s+\w+(\(.*\))?\s*[;{]')

for filename in sorted(os.listdir('$java_dir')):
    if filename.endswith('.java') and filename != 'module-info.java':
        with open(os.path.join('$java_dir', filename), 'r') as f:
            content = f.read()
            # Find the class name
            class_match = class_pattern.search(content)
            if class_match:
                kind, name = class_match.groups()
                print(f"{kind} {name} {{")
                
                # Extract members line by line
                lines = content.split('\n')
                for line in lines:
                    line = line.strip()
                    # Only keep lines that look like a field or method signature
                    if member_pattern.match(line):
                        # Filter out common logic keywords that look like signatures
                        if not any(k in line for k in ['return', 'if', 'for', 'while', 'switch', 'throw', 'package', 'import', 'break']):
                            # Clean the line: remove 'public/private' and trailing '{' or ';'
                            clean = line.replace('public ', '').replace('private ', '').replace('protected ', '').split('{')[0].strip()
                            if clean and clean != name and not clean.startswith('class'):
                                print(f"  {clean.rstrip(';')}")
                print("}")
print("@enduml")
EOF

    # Generate SVG (native support) to bypass missing Batik libraries in Fedora
    java -jar "$PLANTUML_JAR" -tsvg -o "$PWD/$target_dir" "$TEMP_PUML"
    
    # Convert SVG to PDF using Fedora's librsvg tool
    if [ -f "$target_dir/clean_structure.svg" ]; then
        rsvg-convert -f pdf -o "$target_dir/${package_name}.pdf" "$target_dir/clean_structure.svg"
        rm "$target_dir/clean_structure.svg"
    fi

    rm "$TEMP_PUML"
done

echo "Process complete. Check ./$ROOT_DIAGRAMS_DIR for clean, professional PDFs."
