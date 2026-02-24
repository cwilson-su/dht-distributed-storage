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
    TEMP_PUML="pure_structure.puml"
    
    echo "Generating Clean PDF for package: $package_name..."
    
    python3 - <<EOF > "$TEMP_PUML"
import os, re

print("@startuml")
print("skinparam classAttributeIconSize 0")
print("set namespaceSeparator none")

# Regex to find class/interface/enum headers
head_pat = re.compile(r'(class|interface|enum)\s+(\w+)')

for filename in sorted(os.listdir('$java_dir')):
    if filename.endswith('.java') and filename != 'module-info.java':
        with open(os.path.join('$java_dir', filename), 'r') as f:
            content = f.read()
            
            header = head_pat.search(content)
            if header:
                kind, name = header.groups()
                print(f"{kind} {name} {{")
                
                # Split content and look for member declarations
                lines = content.split('\n')
                for line in lines:
                    line = line.strip()
                    
                    # VALID MEMBER RULE:
                    # 1. Ends in ; (field) OR ends in { (method start)
                    # 2. Does NOT contain logic symbols: =, +, ", break, if, while, switch, log
                    # 3. Must have a type and a name
                    if (line.endswith(';') or line.endswith('{')) and '(' in line:
                        # This is likely a method signature
                        if not any(x in line for x in ['=', '"', '+', 'break', 'if', 'while', 'log', 'return']):
                            clean = line.replace('{', '').replace('public ', '').replace('private ', '').strip()
                            if clean and clean != name and not clean.startswith('class'):
                                print(f"  {clean}")
                    elif line.endswith(';') and not '(' in line:
                        # This is likely a field declaration
                        if not any(x in line for x in ['=', '"', '+', 'break', 'new']):
                            clean = line.rstrip(';').replace('public ', '').replace('private ', '').strip()
                            if clean:
                                print(f"  {clean}")
                print("}")
print("@enduml")
EOF

    # Generate SVG
    java -jar "$PLANTUML_JAR" -tsvg -o "$PWD/$target_dir" "$TEMP_PUML"
    
    # Convert to PDF
    if [ -f "$target_dir/pure_structure.svg" ]; then
        rsvg-convert -f pdf -o "$target_dir/${package_name}.pdf" "$target_dir/pure_structure.svg"
        rm "$target_dir/pure_structure.svg"
    fi

    rm "$TEMP_PUML"
done

echo "Process complete. Check ./$ROOT_DIAGRAMS_DIR for clean PDFs."
