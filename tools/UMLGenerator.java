package tools;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import java.nio.file.*;
import java.util.stream.*;

public class UMLGenerator {
    public static void main(String[] args) throws Exception {
        Path folder = Paths.get(args[0]);
        Files.walk(folder)
             .filter(p -> p.toString().endsWith(".java"))
             .forEach(p -> {
                 try {
                     CompilationUnit cu = StaticJavaParser.parse(p);
                     cu.getTypes().forEach(t -> {
                         // Only handle classes or interfaces
                         if (t instanceof ClassOrInterfaceDeclaration c) {
                             String type = c.isInterface() ? "interface" : "class";
                             System.out.println(type + " " + c.getName());

                             c.getExtendedTypes().forEach(e -> 
                                 System.out.println(c.getName() + " --|> " + e.getName())
                             );
                             c.getImplementedTypes().forEach(i -> 
                                 System.out.println(c.getName() + " ..|> " + i.getName())
                             );

                             c.getMembers().forEach(m -> {
                                 if (m instanceof FieldDeclaration fd) {
                                     fd.getVariables().forEach(v ->
                                         System.out.println(c.getName() + " : " + v.getName())
                                     );
                                 } else if (m instanceof MethodDeclaration md) {
                                     System.out.println(c.getName() + " : " + md.getName() + "()");
                                 }
                             });
                         }
                     });
                 } catch(Exception e) {
                     e.printStackTrace();
                 }
             });
    }
}
