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

# Find only directories that actually contain .java files
find src -name "*.java" -not -path '*/.*' | xargs -n1 dirname | sort -u | while read -r java_dir; do
    
    target_dir="$ROOT_DIAGRAMS_DIR/$java_dir"
    mkdir -p "$target_dir"
    
    package_name=$(basename "$java_dir")
    TEMP_PUML="generic_relational.puml"
    
    echo "Generating Clean Relational UML for package: $package_name..."
    
    python3 - <<EOF > "$TEMP_PUML"
import os, re

def get_visibility(line):
    if 'public' in line: return '+'
    if 'private' in line: return '-'
    if 'protected' in line: return '#'
    return '~' 

def clean_sig(line):
    line = re.sub(r'\b(public|private|protected|static|final|volatile)\b', '', line)
    return line.split('=')[0].split('{')[0].strip().rstrip(';')

print("@startuml")
print("skinparam classAttributeIconSize 0")
print("set namespaceSeparator none")

classes = []
relationships = set()

# Pass 1: Catalog types (File check added)
for filename in sorted(os.listdir('$java_dir')):
    filepath = os.path.join('$java_dir', filename)
    if os.path.isfile(filepath) and filename.endswith('.java'):
        with open(filepath, 'r') as f:
            content = f.read()
            match = re.search(r'\b(class|interface|enum)\s+(\w+)', content)
            if match:
                classes.append(match.group(2))

# Pass 2: Extract and Build (File check added)
for filename in sorted(os.listdir('$java_dir')):
    filepath = os.path.join('$java_dir', filename)
    if not os.path.isfile(filepath) or not filename.endswith('.java'):
        continue
        
    with open(filepath, 'r') as f:
        lines = f.readlines()
        current_main = ""
        brace_level = 0
        
        for line in lines:
            raw = line.strip()
            if not raw or raw.startswith(('//', '/*', '*', 'import', 'package', '@')):
                continue
            
            if brace_level == 0:
                match = re.search(r'\b(class|interface|enum)\s+(\w+)(?:\s+(?:extends|implements)\s+([\w\s,]+))?', raw)
                if match:
                    kind, name, parents = match.groups()
                    current_main = name
                    print(f"{kind} {name} {{")
                    if parents:
                        for p in re.split(r'[,\s]+', parents.strip()):
                            if p in classes:
                                arrow = "<|--" if "extends" in raw else "..|>"
                                relationships.add(f"{p} {arrow} {name}")
            
            old_level = brace_level
            brace_level += raw.count('{')
            brace_level -= raw.count('}')
            
            if current_main and old_level == 1:
                for other in classes:
                    if other != current_main and re.search(r'\b' + other + r'\b', raw):
                        arrow = "-->" if ';' in raw else "..>"
                        relationships.add(f"{current_main} {arrow} {other}")

                if 'enum' in raw and '{' in raw:
                    e_name = re.search(r'enum\s+(\w+)', raw).group(1)
                    relationships.add(f"{current_main} +-- {e_name}")
                    print(f"  {get_visibility(raw)} enum {e_name}")
                elif ';' in raw or '(' in raw:
                    if not any(k in raw for k in ['if ', 'for ', 'while ', 'return ']):
                        print(f"  {get_visibility(raw)} {clean_sig(raw)}")
                elif re.match(r'^[A-Z_]+[,;]?$', raw):
                    print(f"  + {raw.rstrip(',').rstrip(';')}")

            if current_main and brace_level == 0 and old_level > 0:
                print("}")
                current_main = ""

# External Enum Definition Handler
for filename in sorted(os.listdir('$java_dir')):
    filepath = os.path.join('$java_dir', filename)
    if os.path.isfile(filepath) and filename.endswith('.java'):
        with open(filepath, 'r') as f:
            content = f.read()
            enums = re.findall(r'enum\s+(\w+)\s*\{([^}]+)\}', content)
            for e_name, e_content in enums:
                print(f"enum {e_name} {{")
                for const in re.findall(r'\b([A-Z_]+)\b', e_content):
                    print(f"  + {const}")
                print("}")

for rel in sorted(relationships):
    print(rel)

print("@enduml")
EOF

    java -jar "$PLANTUML_JAR" -tsvg -o "$PWD/$target_dir" "$TEMP_PUML"
    
    if [ -f "$target_dir/generic_relational.svg" ]; then
        rsvg-convert -f pdf -o "$target_dir/${package_name}.pdf" "$target_dir/generic_relational.svg"
        rm "$target_dir/generic_relational.svg"
    fi
    rm "$TEMP_PUML"
done

echo "Process complete. Relational PDFs generated in ./$ROOT_DIAGRAMS_DIR"
